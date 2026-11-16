"""Switch ordering and rollback without a running Docker daemon."""

import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import bluegreen


class BlueGreenTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        base = Path(self.directory.name)
        self.active = patch.object(bluegreen, "UPSTREAM", base / "active/upstream.conf")
        self.standby = patch.object(bluegreen, "STANDBY_UPSTREAM", base / "standby/upstream.conf")
        self.active.start()
        self.standby.start()
        self.addCleanup(self.active.stop)
        self.addCleanup(self.standby.stop)
        bluegreen.write_upstream("blue")

    def test_both_colors_have_shared_state_and_private_standby(self):
        source = (Path(__file__).parent / "compose.bluegreen.yaml").read_text()
        self.assertIn("127.0.0.1:8444:443", source)
        self.assertIn("api-blue:", source)
        self.assertIn("api-green:", source)
        self.assertEqual(source.count("DB_HOST: mysql"), 1)  # shared YAML anchor
        self.assertEqual(source.count("REDIS_HOST: redis"), 1)
        self.assertEqual(source.count("mysql_data:/var/lib/mysql"), 1)

    def test_switch_smokes_standby_before_public_and_reloads_both(self):
        events = []
        with patch.object(bluegreen, "compose", side_effect=lambda *a: events.append(a)), \
             patch.object(bluegreen, "smoke", side_effect=lambda *a, **k: events.append(k)):
            bluegreen.switch("green", "test.env", "smoke")
        self.assertEqual(bluegreen.active_color(), "green")
        self.assertEqual([event["standby"] for event in events if isinstance(event, dict)],
                         [True, False])
        self.assertIn(("test.env", "exec", "-T", "nginx", "nginx", "-s", "reload"), events)

    def test_public_smoke_failure_restores_previous_color(self):
        calls = []
        def fake_smoke(program, *, standby):
            calls.append(standby)
            if not standby:
                raise subprocess.CalledProcessError(1, program)
        with patch.object(bluegreen, "compose"), patch.object(bluegreen, "smoke", side_effect=fake_smoke):
            with self.assertRaises(subprocess.CalledProcessError):
                bluegreen.switch("green", "test.env", "smoke")
        self.assertEqual(calls, [True, False])
        self.assertEqual(bluegreen.active_color(), "blue")

    def test_invalid_active_file_blocks_switch(self):
        bluegreen.UPSTREAM.write_text("server untrusted;\n")
        with self.assertRaises(RuntimeError):
            bluegreen.active_color()


if __name__ == "__main__":
    unittest.main()
