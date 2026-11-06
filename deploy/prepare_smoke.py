"""Create disposable Compose secrets and localhost TLS fixtures without printing them."""

from __future__ import annotations

import secrets
import subprocess
from pathlib import Path


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    tls = root / "deploy/.smoke-tls"
    tls.mkdir(parents=True, exist_ok=True)
    cert = tls / "cert.pem"
    key = tls / "key.pem"
    subprocess.run(
        ["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
         "-keyout", str(key), "-out", str(cert), "-subj", "/CN=localhost",
         "-addext", "subjectAltName=DNS:localhost"],
        check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    key.chmod(0o600)
    values = {
        "DB_NAME": "doChallenge",
        "DB_USER": "shockgraph",
        "DB_PASSWORD": secrets.token_urlsafe(32),
        "MYSQL_ROOT_PASSWORD": secrets.token_urlsafe(32),
        "JWT_SECRET": secrets.token_urlsafe(48),
        "MAIL_HOST": "mailpit",
        "MAIL_PORT": "1025",
        "MAIL_USERNAME": "smoke",
        "MAIL_PASSWORD": "unused",
        "MAIL_FROM": "smoke@example.invalid",
        "WEB_ORIGIN": "https://shockgraph-web.vercel.app",
        "TLS_CERT_PATH": str(cert),
        "TLS_KEY_PATH": str(key),
    }
    env = root / "deploy/.smoke.env"
    env.write_text("".join(f"{name}={value}\n" for name, value in values.items()))
    env.chmod(0o600)


if __name__ == "__main__":
    main()
