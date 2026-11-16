"""Fail-closed schema readiness gate for a shared-MySQL Blue/Green rollout.

This checks repository evidence only. It does not claim that a live database has
been migrated or that two application versions are compatible with its schema.
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path


def blockers(root: Path) -> list[str]:
    prod = (root / "src/main/resources/application-prod.yml").read_text()
    build = (root / "build.gradle").read_text()
    migration_dir = root / "src/main/resources/db/migration"
    match = re.search(r"(?m)^\s*ddl-auto:\s*([\w-]+)\s*$", prod)
    found = match.group(1) if match else "missing"
    problems: list[str] = []
    if found != "validate":
        problems.append("production JPA schema mode must be validate; current=" + found)
    core = re.search(r"(?m)^\s*(?:implementation|runtimeOnly)\s+['\"]org\.flywaydb:flyway-core", build)
    mysql = re.search(r"(?m)^\s*(?:implementation|runtimeOnly)\s+['\"]org\.flywaydb:flyway-mysql", build)
    if not core or not mysql:
        problems.append("versioned MySQL migration dependency is missing")
    if not migration_dir.is_dir() or not any(
        path.read_text().strip() for path in migration_dir.glob("V*__*.sql")
    ):
        problems.append("versioned MySQL migration file is missing")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--enforce", action="store_true",
                        help="return failure if shared-DB rollout prerequisites are absent")
    args = parser.parse_args()
    problems = blockers(Path(__file__).resolve().parent.parent)
    if problems:
        print("BLUE_GREEN_HOLD: " + "; ".join(problems))
        return 1 if args.enforce else 0
    print("SCHEMA_SOURCE_READY: inspect real DB migration and dual-version compatibility")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
