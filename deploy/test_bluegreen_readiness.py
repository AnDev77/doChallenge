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
        prod.write_text("spring:\n  jpa:\n    hibernate:\n      ddl-auto: validate\n")
        (self.root / "build.gradle").write_text(
            "implementation 'org.flywaydb:flyway-core'\n"
            "runtimeOnly 'org.flywaydb:flyway-mysql'\n"
        )
        migrations = self.root / "src/main/resources/db/migration"
        migrations.mkdir(parents=True)
        (migrations / "V1__baseline.sql").write_text("CREATE TABLE example (id BIGINT);\n")

    def test_ready_sources_have_no_blockers(self) -> None:
        self.assertEqual(blockers(self.root), [])

    def test_hibernate_update_blocks_rollout_even_with_migration(self) -> None:
        prod = self.root / "src/main/resources/application-prod.yml"
        prod.write_text(prod.read_text().replace("validate", "update"))
        self.assertTrue(any("schema mode" in item for item in blockers(self.root)))

    def test_missing_migration_blocks_rollout(self) -> None:
        (self.root / "src/main/resources/db/migration/V1__baseline.sql").unlink()
        self.assertTrue(any("migration file" in item for item in blockers(self.root)))

    def test_commented_dependency_does_not_pass(self) -> None:
        build = self.root / "build.gradle"
        build.write_text("// implementation 'org.flywaydb:flyway-core'\n"
                         "runtimeOnly 'org.flywaydb:flyway-mysql'\n")
        self.assertTrue(any("migration dependency" in item for item in blockers(self.root)))


if __name__ == "__main__":
    unittest.main()
