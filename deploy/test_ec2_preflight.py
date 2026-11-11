"""Synthetic inventory tests; do not contact AWS, Docker or production SMTP."""

import contextlib
import io
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from ec2_preflight import command_ok, env_file_status, inspect, role_attached


class Ec2PreflightTest(unittest.TestCase):
    def test_file_state_without_reading_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "env"
            self.assertEqual(env_file_status(path), "missing")
            path.write_text("SYNTHETIC_SECRET_SENTINEL")
            path.chmod(0o644)
            self.assertEqual(env_file_status(path), "permissions_too_open")
            path.chmod(0o600)
            with patch.object(Path, "read_text", side_effect=AssertionError("must not read")):
                self.assertEqual(env_file_status(path),
                                 "restricted_file_present_values_unverified")
            path.write_text("")
            self.assertEqual(env_file_status(path), "empty")
            link = Path(directory) / "link"
            link.symlink_to(path)
            self.assertEqual(env_file_status(link), "not_regular_file")

    def test_failed_command_has_no_error_output(self):
        for error in (OSError("SYNTHETIC_SECRET"),
                      subprocess.TimeoutExpired("SYNTHETIC_SECRET", 15)):
            with patch("ec2_preflight.subprocess.run", side_effect=error):
                output = io.StringIO()
                with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
                    self.assertFalse(command_ok(["docker", "info"]))
                self.assertEqual(output.getvalue(), "")

    def test_command_does_not_use_shell_or_emit_streams(self):
        with patch("ec2_preflight.subprocess.run") as run:
            run.return_value.returncode = 0
            self.assertTrue(command_ok(["docker", "info"]))
            self.assertEqual(run.call_args.kwargs["stdout"], subprocess.DEVNULL)
            self.assertEqual(run.call_args.kwargs["stderr"], subprocess.DEVNULL)
            self.assertFalse(run.call_args.kwargs.get("shell", False))

    def test_imds_only_reads_role_listing(self):
        opener = MagicMock()
        responses = []
        for content in (b"synthetic-token", b"synthetic-role"):
            response = MagicMock()
            response.__enter__.return_value.read.return_value = content
            responses.append(response)
        opener.open.side_effect = responses
        with patch("ec2_preflight.urllib.request.build_opener", return_value=opener):
            self.assertEqual(role_attached(), "attached_permissions_unverified")
        calls = opener.open.call_args_list
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[0].args[0].get_method(), "PUT")
        self.assertEqual(calls[1].args[0].full_url,
                         "http://169.254.169.254/latest/meta-data/iam/security-credentials/")

    def test_metadata_unavailable_is_not_reported_as_no_role(self):
        with patch("ec2_preflight.urllib.request.build_opener") as build:
            build.return_value.open.side_effect = OSError("SYNTHETIC_SECRET")
            self.assertEqual(role_attached(), "unverified")

    def test_positive_inventory_never_authorizes_deployment(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "deploy").mkdir()
            (root / "deploy/.env").write_text("SYNTHETIC_SECRET_SENTINEL")
            (root / "deploy/.env").chmod(0o600)
            with patch("ec2_preflight.command_ok", return_value=True), \
                    patch("ec2_preflight.role_attached", return_value="attached_permissions_unverified"):
                report = inspect(root)
            self.assertEqual(report["deployment_status"], "NOT_VERIFIED")
            self.assertIn("smtp_delivery_and_signup", report["still_required"])
            self.assertNotIn("SYNTHETIC_SECRET", json.dumps(report))


if __name__ == "__main__":
    unittest.main()
