"""Fail-closed local Blue/Green switch for one EC2 Docker Compose host."""

from __future__ import annotations

import argparse
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = ROOT / "deploy/runtime/upstream.conf"
STANDBY_UPSTREAM = ROOT / "deploy/runtime-standby/upstream.conf"
COLORS = ("blue", "green")


def upstream(color: str) -> str:
    if color not in COLORS:
        raise ValueError("unknown color")
    return f"upstream active_api {{ server api-{color}:8080; }}\n"


def active_color() -> str:
    if not UPSTREAM.is_file():
        raise RuntimeError("active upstream missing; initialize an empty deployment first")
    contents = UPSTREAM.read_text()
    for color in COLORS:
        standby = "green" if color == "blue" else "blue"
        if contents == upstream(color) and STANDBY_UPSTREAM.read_text() == upstream(standby):
            return color
    raise RuntimeError("active upstream has unexpected content")


def write_upstream(color: str) -> None:
    for path, value in ((UPSTREAM, color),
                        (STANDBY_UPSTREAM, "green" if color == "blue" else "blue")):
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(mode="w", dir=path.parent, delete=False) as temp:
            temp.write(upstream(value))
            temp_path = Path(temp.name)
        os.chmod(temp_path, 0o600)
        temp_path.replace(path)


def compose(env_file: str, *args: str) -> None:
    overlay = os.getenv("SHOCKGRAPH_BLUEGREEN_OVERLAY")
    files = ["-f", "deploy/compose.bluegreen.yaml"]
    if overlay:
        files += ["-f", overlay]
    subprocess.run(["docker", "compose", "--env-file", env_file,
                    *files, *args],
                   cwd=ROOT, check=True, stdout=subprocess.DEVNULL)


def smoke(program: str, *, standby: bool, target: str) -> None:
    env = dict(os.environ)
    env["SHOCKGRAPH_SMOKE_BASE"] = "https://127.0.0.1:8444" if standby else "https://localhost"
    env["SHOCKGRAPH_SMOKE_SKIP_RECREATE"] = "1"
    env["SHOCKGRAPH_SMOKE_WATCHLIST"] = "1" if target == "green" else "0"
    env["SHOCKGRAPH_SMOKE_STATE_FILE"] = "deploy/.smoke-state.json"
    subprocess.run([program], cwd=ROOT, check=True, stdout=subprocess.DEVNULL, env=env)


def switch(target: str, env_file: str, smoke_program: str) -> None:
    previous = active_color()
    if target not in COLORS or target == previous:
        raise ValueError("target must be the standby color")
    # Both colors share the same database and Redis. A passing health response
    # alone is insufficient: caller-supplied smoke must test API and chat.
    compose(env_file, "exec", "-T", f"api-{target}", "curl", "--fail", "--silent",
            "http://127.0.0.1:8080/actuator/health")
    compose(env_file, "exec", "-T", "nginx", "nginx", "-t")
    compose(env_file, "exec", "-T", "nginx-standby", "nginx", "-t")
    smoke(smoke_program, standby=True, target=target)
    write_upstream(target)
    try:
        compose(env_file, "exec", "-T", "nginx", "nginx", "-t")
        compose(env_file, "exec", "-T", "nginx-standby", "nginx", "-t")
        compose(env_file, "exec", "-T", "nginx", "nginx", "-s", "reload")
        compose(env_file, "exec", "-T", "nginx-standby", "nginx", "-s", "reload")
        smoke(smoke_program, standby=False, target=target)
    except (OSError, subprocess.CalledProcessError):
        write_upstream(previous)
        compose(env_file, "exec", "-T", "nginx", "nginx", "-t")
        compose(env_file, "exec", "-T", "nginx-standby", "nginx", "-t")
        compose(env_file, "exec", "-T", "nginx", "nginx", "-s", "reload")
        compose(env_file, "exec", "-T", "nginx-standby", "nginx", "-s", "reload")
        raise


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("target", choices=(*COLORS, "init-blue"))
    parser.add_argument("--env-file", default="deploy/.env")
    parser.add_argument("--smoke-program",
                        help="executable that checks HTTPS API and authenticated chat")
    args = parser.parse_args()
    if args.target == "init-blue":
        if UPSTREAM.exists() or STANDBY_UPSTREAM.exists():
            parser.error("active upstream already exists")
        write_upstream("blue")
        print("Blue/Green active upstream initialized to blue")
        return
    if not Path(args.env_file).is_file():
        parser.error("environment file missing")
    if not args.smoke_program or not Path(args.smoke_program).is_file():
        parser.error("smoke program missing")
    switch(args.target, args.env_file, args.smoke_program)
    print(f"Blue/Green traffic switched to {args.target}; smoke passed")


if __name__ == "__main__":
    main()
