"""Synthetic repository fixtures for the fail-closed rollout gate."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from bluegreen_readiness import blockers


class BlueGreenReadinessTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        prod = self.root / "src/main/resources/application-prod.yml"
        prod.parent.mkdir(parents=True)
        prod.write_text("spring:\n  flyway:\n    enabled: true\n"
                        "    baseline-on-migrate: false\n  jpa:\n    hibernate:\n"
                        "      ddl-auto: validate\n")
        (self.root / "build.gradle").write_text(
            "implementation 'org.flywaydb:flyway-core'\n"
            "runtimeOnly 'org.flywaydb:flyway-mysql'\n"
        )
        migrations = self.root / "src/main/resources/db/migration"
        migrations.mkdir(parents=True)
        tables = ("member", "meetup", "meetup_member", "chat_room",
                  "chat_room_read_state", "chat_message", "finance_chat_message")
        (migrations / "V1__initial_schema.sql").write_text("".join(
            f"CREATE TABLE {table} (id BIGINT);\n" for table in tables
        ))

    def test_ready_sources_have_no_blockers(self) -> None:
        self.assertEqual(blockers(self.root), [])

    def test_hibernate_update_blocks_rollout_even_with_migration(self) -> None:
        prod = self.root / "src/main/resources/application-prod.yml"
        prod.write_text(prod.read_text().replace("validate", "update"))
        self.assertTrue(any("schema mode" in item for item in blockers(self.root)))

    def test_missing_migration_blocks_rollout(self) -> None:
        (self.root / "src/main/resources/db/migration/V1__initial_schema.sql").unlink()
        self.assertTrue(any("migration file" in item for item in blockers(self.root)))

    def test_commented_dependency_does_not_pass(self) -> None:
        build = self.root / "build.gradle"
        build.write_text("// implementation 'org.flywaydb:flyway-core'\n"
                         "runtimeOnly 'org.flywaydb:flyway-mysql'\n")
        self.assertTrue(any("migration dependency" in item for item in blockers(self.root)))

    def test_automatic_baseline_blocks_rollout(self) -> None:
        prod = self.root / "src/main/resources/application-prod.yml"
        prod.write_text(prod.read_text().replace("baseline-on-migrate: false",
                                                 "baseline-on-migrate: true"))
        self.assertTrue(any("automatic baseline" in item for item in blockers(self.root)))

    def test_incomplete_or_destructive_schema_blocks_rollout(self) -> None:
        migration = self.root / "src/main/resources/db/migration/V1__initial_schema.sql"
        migration.write_text("CREATE TABLE member (id BIGINT);\nDROP TABLE chat_room;\n")
        reasons = blockers(self.root)
        self.assertTrue(any("missing application tables" in item for item in reasons))
        self.assertTrue(any("destructive" in item for item in reasons))


if __name__ == "__main__":
    unittest.main()
