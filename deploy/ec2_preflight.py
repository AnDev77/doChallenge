"""Read-only EC2 inventory. Never authorizes deployment or reads secret values."""

from __future__ import annotations

import argparse
import json
import stat
import subprocess
import urllib.request
from pathlib import Path


def command_ok(args: list[str]) -> bool:
    """Discard both streams: Docker errors may include environment information."""
    try:
        return subprocess.run(
            args, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL, timeout=15, check=False,
        ).returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def role_attached() -> str:
    """IMDSv2 only; read role listing, never the credentials beneath role-name."""
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        token_request = urllib.request.Request(
            "http://169.254.169.254/latest/api/token", method="PUT",
            headers={"X-aws-ec2-metadata-token-ttl-seconds": "60"},
        )
        with opener.open(token_request, timeout=2) as response:
            token = response.read(4096).decode("ascii")
        role_request = urllib.request.Request(
            "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
            headers={"X-aws-ec2-metadata-token": token},
        )
        with opener.open(role_request, timeout=2) as response:
            attached = bool(response.read(4096).strip())
        return "attached_permissions_unverified" if attached else "not_detected"
    except (OSError, ValueError, UnicodeError):
        return "unverified"


def env_file_status(path: Path) -> str:
    # Do not parse, source, interpolate, or print any .env contents.
    try:
        info = path.lstat()
    except FileNotFoundError:
        return "missing"
    except OSError:
        return "unverified"
    if not stat.S_ISREG(info.st_mode):
        return "not_regular_file"
    if info.st_mode & 0o077:
        return "permissions_too_open"
    if info.st_size == 0:
        return "empty"
    return "restricted_file_present_values_unverified"


def inspect(root: Path) -> dict[str, object]:
    return {
        "mode": "read_only_inventory",
        "docker_daemon": command_ok(["docker", "info"]),
        "docker_compose": command_ok(["docker", "compose", "version"]),
        "iam_role": role_attached(),
        "ssm_agent_active": any(command_ok(["systemctl", "is-active", "--quiet", unit])
                                for unit in ("amazon-ssm-agent",
                                             "snap.amazon-ssm-agent.amazon-ssm-agent")),
        "server_env": env_file_status(root / "deploy/.env"),
        "bluegreen_compose_present": (root / "deploy/compose.bluegreen.yaml").is_file(),
        "flyway_v1_present": (root / "src/main/resources/db/migration/"
                              "V1__initial_schema.sql").is_file(),
        "deployment_status": "NOT_VERIFIED",
        "still_required": ["approved_main_and_ci", "api_domain_and_valid_tls",
                           "smtp_delivery_and_signup", "existing_database_and_backup",
                           "two_browser_chat", "switch_and_rollback"],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    print(json.dumps(inspect(args.root), indent=2))
    # Zero means inventory completed, never permission to deploy.


if __name__ == "__main__":
    main()
