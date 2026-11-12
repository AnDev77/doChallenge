"""Certbot deploy hook for the Blue/Green IP certificate on one EC2 host.

Run only after a successful renewal, registered for the specific IP certificate.
Never prints certificate contents or the resolved Compose environment.
"""

from __future__ import annotations

import ipaddress
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COMPOSE = ["docker", "compose", "--env-file", "deploy/.env",
           "-f", "deploy/compose.bluegreen.yaml"]


def run(*args: str, capture: bool = False) -> str:
    result = subprocess.run([*args], cwd=ROOT, check=True, stdin=subprocess.DEVNULL,
                            stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL, text=True, timeout=120)
    return result.stdout or ""


def reload_renewed_ip(ip: str, lineage: str | None, domains: str | None) -> None:
    address = ipaddress.ip_address(ip)
    if not address.is_global or address.version != 4:
        raise ValueError("expected a public IPv4 address")
    if domains is None or set(domains.split()) != {ip}:
        raise ValueError("Certbot renewed domains do not match the configured IP")
    if not lineage or Path(lineage).name != ip:
        raise ValueError("Certbot certificate lineage does not match the configured IP")
    cert = Path(lineage) / "fullchain.pem"
    key = Path(lineage) / "privkey.pem"
    if not cert.is_file() or not key.is_file() or not (ROOT / "deploy/.env").is_file():
        raise ValueError("certificate, key, or server-only Compose env is missing")
    run("openssl", "x509", "-in", str(cert), "-noout", "-checkip", ip)
    run(*COMPOSE, "config", "--quiet")
    running = set(run(*COMPOSE, "ps", "--services", "--status", "running",
                      capture=True).splitlines())
    if not {"nginx", "nginx-standby"}.issubset(running):
        raise RuntimeError("both Nginx proxies must already be running")

    # Recreate only proxy containers: file bind mounts may still point at old
    # certificate inodes after Certbot rotates its live/ symlinks.
    for service, port in (("nginx-standby", 8444), ("nginx", 443)):
        run(*COMPOSE, "up", "-d", "--no-deps", "--force-recreate", "--wait",
            "--wait-timeout", "60", service)
        run("curl", "--fail", "--silent", "--show-error", "--max-time", "15",
            "--resolve", f"{ip}:{port}:127.0.0.1", f"https://{ip}:{port}/healthz")


def main() -> int:
    global ROOT
    try:
        if len(sys.argv) != 3:
            raise ValueError("usage: ip_tls_reload.py REPO_ROOT PUBLIC_IP")
        ROOT = Path(sys.argv[1]).resolve(strict=True)
        reload_renewed_ip(sys.argv[2], os.getenv("RENEWED_LINEAGE"),
                          os.getenv("RENEWED_DOMAINS"))
    except (ValueError, RuntimeError, OSError, subprocess.CalledProcessError,
            subprocess.TimeoutExpired) as error:
        # Do not echo subprocess arguments or certificate paths on failure.
        print(f"IP_TLS_RELOAD_HOLD: {type(error).__name__}", file=sys.stderr)
        return 1
    print("IP_TLS_RELOAD_OK: both HTTPS proxies serve the renewed certificate")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
