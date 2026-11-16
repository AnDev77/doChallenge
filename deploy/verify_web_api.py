"""Check the public Nginx and Java web contracts without using member data."""

import argparse
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

MAX_RESPONSE = 256 * 1024
ROOM_PATH = "/api/v1/finance/rooms"


class CheckFailed(Exception):
    """An operator message safe to print without server response bodies."""


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def https_origin(value):
    try:
        parsed = urlsplit(value)
        _ = parsed.port
    except ValueError:
        raise CheckFailed("Invalid URL.") from None
    if (parsed.scheme != "https" or not parsed.hostname
            or parsed.username is not None or parsed.password is not None
            or parsed.path or parsed.query or parsed.fragment
            or value != f"{parsed.scheme}://{parsed.netloc}"
            or any(char.isspace() for char in value)):
        raise CheckFailed("Enter an origin without a path, query or credentials.")
    return value


def fetch(opener, request):
    try:
        with opener.open(request, timeout=8) as response:
            return response.status, response.headers, response.read(MAX_RESPONSE + 1)
    except HTTPError as error:
        try:
            return error.code, error.headers, b""
        finally:
            error.close()
    except (URLError, OSError, ValueError):
        raise CheckFailed("Connection or HTTPS certificate validation failed.") from None


def verify(api, web, opener=None, report=print):
    api, web = https_origin(api), https_origin(web)
    opener = opener if opener is not None else build_opener(NoRedirect())

    def check(name, path, *, method="GET", headers=None, expected=(200,)):
        try:
            status, response_headers, body = fetch(opener, Request(
                api + path, method=method, headers=headers or {},
            ))
        except CheckFailed as error:
            raise CheckFailed(f"{name}: {error}") from None
        if status not in expected:
            raise CheckFailed(f"{name}: HTTP {status}.")
        if len(body) > MAX_RESPONSE:
            raise CheckFailed(f"{name}: Response size exceeded the check limit.")
        return response_headers, body

    check("Nginx HTTPS", "/healthz")
    report("PASS Nginx HTTPS /healthz (proxy only)")

    for symbol in ("QQQ", "SPY"):
        name = f"{symbol} read"
        headers, body = check(name, f"{ROOM_PATH}/{symbol}/messages",
                              headers={"Origin": web, "Accept": "application/json"})
        if headers.get("Access-Control-Allow-Origin") != web:
            raise CheckFailed(f"{name}: Java did not allow the selected web origin.")
        if headers.get("Content-Type", "").split(";", 1)[0].strip().lower() != "application/json":
            raise CheckFailed(f"{name}: Expected JSON from Java.")
        try:
            result = json.loads(body)
        except (ValueError, UnicodeError):
            raise CheckFailed(f"{name}: Invalid JSON response.") from None
        rows = result.get("data") if isinstance(result, dict) else None
        if (not isinstance(result, dict) or result.get("success") is not True
                or not isinstance(rows, list) or len(rows) > 30
                or any(not isinstance(row, dict) or row.get("symbol") != symbol for row in rows)):
            raise CheckFailed(f"{name}: Unexpected chat response contract.")
        report(f"PASS Java {symbol} JSON read and browser origin")

    for name, path, requested in (
        ("signup", "/api/v1/auth/signup", {"content-type"}),
        ("chat write", f"{ROOM_PATH}/QQQ/messages", {"content-type", "authorization"}),
    ):
        label = f"{name} browser preflight"
        headers, _ = check(label, path, method="OPTIONS", expected=(200, 204), headers={
            "Origin": web,
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": ", ".join(sorted(requested)),
        })
        methods = {part.strip().upper() for part in headers.get("Access-Control-Allow-Methods", "").split(",")}
        allowed = {part.strip().lower() for part in headers.get("Access-Control-Allow-Headers", "").split(",")}
        if headers.get("Access-Control-Allow-Origin") != web or "POST" not in methods or not requested <= allowed:
            raise CheckFailed(f"{label}: The browser origin, method or headers were not allowed.")
        report(f"PASS {label} (no POST sent)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", required=True, help="Public Java HTTPS origin")
    parser.add_argument("--web", default="https://shockgraph-web.vercel.app", help="Vercel HTTPS origin")
    args = parser.parse_args()
    try:
        verify(args.api, args.web)
    except CheckFailed as error:
        print(f"FAIL {error}", file=sys.stderr)
        return 1
    print("Public HTTPS, Java reads and browser preflights passed. SMTP and signed-in writes remain unverified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
