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
    if not re.search(r"(?m)^\s*enabled:\s*true\s*$", prod):
        problems.append("production Flyway must be enabled")
    if not re.search(r"(?m)^\s*baseline-on-migrate:\s*false\s*$", prod):
        problems.append("automatic baseline must be disabled for existing MySQL")
    core = re.search(r"(?m)^\s*(?:implementation|runtimeOnly)\s+['\"]org\.flywaydb:flyway-core", build)
    mysql = re.search(r"(?m)^\s*(?:implementation|runtimeOnly)\s+['\"]org\.flywaydb:flyway-mysql", build)
    if not core or not mysql:
        problems.append("versioned MySQL migration dependency is missing")
    migration = migration_dir / "V1__initial_schema.sql"
    if not migration.is_file() or not migration.read_text().strip():
        problems.append("versioned MySQL migration file is missing")
    else:
        sql = migration.read_text()
        required = {"member", "meetup", "meetup_member", "chat_room", "chat_room_read_state",
                    "chat_message", "finance_chat_message"}
        found_tables = set(re.findall(r"(?im)^\s*CREATE TABLE\s+([a-z_]+)\s*\(", sql))
        if not required.issubset(found_tables):
            problems.append("initial migration is missing application tables")
        if re.search(r"(?im)^\s*(?:DROP|TRUNCATE)\s+", sql):
            problems.append("initial migration contains destructive statements")
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
