"""Exercise the disposable Compose stack without printing mail codes or tokens."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import re
import socket
import ssl
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

BASE = "https://localhost"
MAILPIT = "http://127.0.0.1:8025"
ORIGIN = "https://shockgraph-web.vercel.app"
TLS = ssl._create_unverified_context()  # Disposable CI certificate only.


def request(path: str, *, method: str = "GET", payload: dict | None = None,
            token: str | None = None, opener=None) -> tuple[int, dict]:
    headers = {"Origin": ORIGIN}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    if token is not None:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    sender = opener.open if opener is not None else urllib.request.urlopen
    try:
        with sender(req, context=TLS, timeout=15) if opener is None else sender(req, timeout=15) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        # Never print a response body: it may contain account details.
        return error.code, {}


def require(status: int, expected: int, label: str) -> None:
    if status != expected:
        raise AssertionError(f"{label}: HTTP {status}, expected {expected}")


def verification_code() -> str:
    # This disposable mailbox receives exactly one synthetic message in the job.
    # Mailpit documents /view/latest.txt for UI integration tests.
    last_status = "unavailable"
    body_length = 0
    has_marker = False
    digit_count = 0
    for _ in range(30):
        try:
            with urllib.request.urlopen(MAILPIT + "/view/latest.txt", timeout=5) as response:
                body = response.read().decode()
            last_status = "ok"
            body_length = len(body)
            has_marker = "ShockGraph" in body
            digit_count = sum(char.isdigit() for char in body)
            match = re.search(r"(?<![0-9])[0-9]{6}(?![0-9])", body)
            if match:
                return match.group()
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            last_status = "not-found"
        time.sleep(1)
    raise AssertionError("synthetic mail code unavailable: "
                         f"mailpit={last_status}, body_length={body_length}, "
                         f"marker={has_marker}, digit_count={digit_count}")


class StompSocket:
    def __init__(self, token: str):
        self.socket = TLS.wrap_socket(socket.create_connection(("127.0.0.1", 443), 10),
                                      server_hostname="localhost")
        self.socket.settimeout(10)
        self.reader = self.socket.makefile("rb")
        key = base64.b64encode(os.urandom(16)).decode()
        upgrade = ("GET /ws HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\n"
                   f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n"
                   f"Sec-WebSocket-Version: 13\r\nOrigin: {ORIGIN}\r\n"
                   "Sec-WebSocket-Protocol: v12.stomp\r\n\r\n")
        self.socket.sendall(upgrade.encode())
        status = self.reader.readline()
        if b" 101 " not in status:
            raise AssertionError("WebSocket upgrade through Nginx failed")
        headers = {}
        while line := self.reader.readline().strip():
            name, value = line.decode().split(":", 1)
            headers[name.lower()] = value.strip()
        expected = base64.b64encode(hashlib.sha1(
            (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()
        ).digest()).decode()
        if headers.get("sec-websocket-accept") != expected:
            raise AssertionError("WebSocket accept key mismatch")
        self.send(f"CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer {token}"
                  "\nheart-beat:0,0\n\n\0")
        if not self.receive().startswith("CONNECTED\n"):
            raise AssertionError("authenticated STOMP CONNECT failed")

    def send(self, body: str) -> None:
        data = body.encode()
        mask = os.urandom(4)
        if len(data) < 126:
            header = bytes((0x81, 0x80 | len(data)))
        elif len(data) < 65536:
            header = bytes((0x81, 0xFE)) + len(data).to_bytes(2, "big")
        else:
            raise AssertionError("unexpectedly large STOMP frame")
        self.socket.sendall(header + mask + bytes(byte ^ mask[i % 4]
                                                  for i, byte in enumerate(data)))

    def receive(self) -> str:
        while True:
            header = self.reader.read(2)
            if len(header) != 2:
                raise AssertionError("WebSocket closed before STOMP response")
            opcode = header[0] & 0x0F
            length = header[1] & 0x7F
            if length == 126:
                length = int.from_bytes(self.reader.read(2), "big")
            elif length == 127:
                length = int.from_bytes(self.reader.read(8), "big")
            if header[1] & 0x80:
                mask = self.reader.read(4)
            else:
                mask = None
            body = self.reader.read(length)
            if mask is not None:
                body = bytes(byte ^ mask[i % 4] for i, byte in enumerate(body))
            if opcode == 8:
                raise AssertionError("WebSocket closed before message")
            if opcode == 1 and body.strip():
                return body.decode().rstrip("\0")

    def subscribe(self) -> None:
        self.send("SUBSCRIBE\nid:smoke\ndestination:/topic/finance/rooms/QQQ"
                  "\nack:auto\n\n\0")

    def close(self) -> None:
        self.reader.close()
        self.socket.close()


def migrated_schema_version() -> str:
    root = Path(__file__).resolve().parent.parent
    compose = ["docker", "compose", "--env-file", "deploy/.smoke.env",
               "-f", "deploy/compose.yaml", "-f", "deploy/compose.smoke.yaml"]
    query = ("MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -N -s -uroot \"$MYSQL_DATABASE\" "
             "-e 'SELECT version FROM flyway_schema_history "
             "WHERE success=1 ORDER BY installed_rank DESC LIMIT 1'")
    result = subprocess.run(compose + ["exec", "-T", "mysql", "sh", "-c", query],
                            cwd=root, check=True, capture_output=True, text=True)
    return result.stdout.strip()


def main() -> None:
    if migrated_schema_version() != "1":
        raise AssertionError("initial MySQL migration not recorded")
    email = f"smoke-{uuid.uuid4().hex}@example.invalid"
    password = uuid.uuid4().hex
    status, _ = request("/api/v1/auth/email-verifications/send", method="POST",
                        payload={"email": email})
    require(status, 200, "send verification")
    code = verification_code()
    status, confirmed = request("/api/v1/auth/email-verifications/confirm", method="POST",
                                payload={"email": email, "code": code})
    require(status, 200, "confirm verification")
    proof = confirmed["data"]["verificationToken"]
    signup = {"email": email, "password": password, "nickname": "smoke-user",
              "verificationToken": proof}
    status, _ = request("/api/v1/auth/signup", method="POST", payload=signup)
    require(status, 201, "signup")
    status, _ = request("/api/v1/auth/signup", method="POST", payload=signup)
    if status < 400:
        raise AssertionError("verification proof was reused")
    status, logged_in = request("/api/v1/auth/login", method="POST",
                                payload={"email": email, "password": password})
    require(status, 200, "login")
    token = logged_in["data"]["accessToken"]

    path = "/api/v1/finance/rooms/QQQ/messages"
    status, _ = request(path, method="POST", payload={"requestId": str(uuid.uuid4()),
                                                      "content": "unauthorized"})
    require(status, 401, "anonymous write rejection")
    live = StompSocket(token)
    try:
        live.subscribe()
        time.sleep(0.5)
        first_request = {"requestId": str(uuid.uuid4()), "content": "synthetic-first"}
        status, first = request(path, method="POST", payload=first_request, token=token)
        require(status, 200, "first write")
        first_id = first["data"]["id"]
        if "synthetic-first" not in live.receive():
            raise AssertionError("live QQQ subscription missed committed message")
        status, retry = request(path, method="POST", payload=first_request, token=token)
        require(status, 200, "idempotent retry")
        if retry["data"]["id"] != first_id:
            raise AssertionError("retry created a duplicate message")
    finally:
        live.close()

    status, second = request(path, method="POST", token=token,
                             payload={"requestId": str(uuid.uuid4()),
                                      "content": "synthetic-after-disconnect"})
    require(status, 200, "write while disconnected")
    second_id = second["data"]["id"]
    reconnected = StompSocket(token)
    try:
        reconnected.subscribe()
        # A fresh client recovers committed messages from MySQL by cursor.
        status, history = request(path + f"?afterId={first_id}")
        require(status, 200, "reconnect history")
        rows = history["data"]
        if [row["id"] for row in rows] != [second_id]:
            raise AssertionError("reconnect cursor did not recover exactly one message")
        status, spy = request("/api/v1/finance/rooms/SPY/messages")
        require(status, 200, "SPY room history")
        if any(row["id"] in (first_id, second_id) for row in spy["data"]):
            raise AssertionError("QQQ messages leaked into SPY")
    finally:
        reconnected.close()

    # Recreate every container while preserving Compose's named MySQL volume.
    # This exercises the same recovery path a single-host restart uses.
    root = Path(__file__).resolve().parent.parent
    compose = ["docker", "compose", "--env-file", "deploy/.smoke.env",
               "-f", "deploy/compose.yaml", "-f", "deploy/compose.smoke.yaml"]
    subprocess.run(compose + ["down", "--remove-orphans"], cwd=root, check=True)
    subprocess.run(compose + ["up", "-d", "--wait", "--wait-timeout", "360"],
                   cwd=root, check=True)
    if migrated_schema_version() != "1":
        raise AssertionError("MySQL migration history changed after recreation")
    status, _ = request("/api/v1/auth/login", method="POST",
                        payload={"email": email, "password": password})
    require(status, 200, "login after Compose recreation")
    status, recovered = request(path + f"?afterId={first_id}")
    require(status, 200, "chat history after Compose recreation")
    if [row["id"] for row in recovered["data"]] != [second_id]:
        raise AssertionError("MySQL chat history did not survive Compose recreation")
    print("PASS: TLS proxy, synthetic SMTP, signup, login, REST and STOMP chat, "
          "reconnect, MySQL volume persistence")


if __name__ == "__main__":
    main()
