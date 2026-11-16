import json
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

import ssm_probe as subject


INSTANCE = "i-0123456789abcdef0"
COMMAND = "01234567-89ab-cdef-0123-456789abcdef"
DIGEST = "a" * 64
OUTPUT = "probe_version=1\ndocker_daemon=ok\ndocker_compose=ok\ncheckout=present\ndeploy_env=missing\n"
DOCUMENT_PATH = Path(__file__).parent / "ssm" / "read-only-preflight.json"


class ProbeTest(unittest.TestCase):
    def runner(self, **overrides):
        payload = {
            "CommandId": COMMAND,
            "InstanceId": INSTANCE,
            "DocumentName": subject.DOCUMENT,
            "DocumentVersion": subject.VERSION,
            "Status": "Success",
            "ResponseCode": 0,
            "StandardOutputContent": OUTPUT,
            "StandardErrorContent": "",
        } | overrides
        return Mock(side_effect=[json.dumps({"Command": {"CommandId": COMMAND}}), "", json.dumps(payload)])

    def test_sends_only_the_pinned_document_to_one_node(self):
        runner = self.runner()
        result = subject.probe(INSTANCE, DIGEST, runner)
        self.assertEqual(result, subject.parse_flags(OUTPUT))
        send, wait, get = runner.call_args_list
        self.assertEqual(send.args, (
            "send-command", "--document-name", "ShockGraph-ReadOnlyPreflight", "--document-version", "1",
            "--document-hash-type", "Sha256", "--document-hash", DIGEST,
            "--instance-ids", INSTANCE, "--timeout-seconds", "45",
        ))
        selector = ("--command-id", COMMAND, "--instance-id", INSTANCE, "--plugin-name", "readOnlyPreflight")
        self.assertEqual(wait.args, ("wait", "command-executed", *selector))
        self.assertEqual(get.args, ("get-command-invocation", *selector))

    def test_invalid_target_or_hash_stops_before_aws(self):
        runner = self.runner()
        for instance, digest in (("", DIGEST), (INSTANCE, ""), ("i-0123456789abcdef00", DIGEST)):
            with self.subTest(instance=instance, digest=digest), self.assertRaises(subject.ProbeError):
                subject.probe(instance, digest, runner)
        runner.assert_not_called()

    def test_unrelated_failed_or_noisy_response_is_rejected(self):
        for overrides in (
            {"InstanceId": "i-12345678"}, {"CommandId": "different"},
            {"DocumentVersion": "2"}, {"Status": "Failed"}, {"ResponseCode": 1},
            {"StandardErrorContent": "sensitive-example"},
        ):
            with self.subTest(overrides=overrides), self.assertRaises(subject.ProbeError) as caught:
                subject.probe(INSTANCE, DIGEST, self.runner(**overrides))
            self.assertNotIn("sensitive-example", str(caught.exception))

    def test_unexpected_missing_and_duplicate_flags_do_not_escape(self):
        for raw in (
            OUTPUT + "token=sensitive-example", OUTPUT.replace("missing", "sensitive-example"),
            OUTPUT + "checkout=present", OUTPUT.split("\n", 1)[1], None,
        ):
            with self.subTest(raw=raw), self.assertRaises(subject.ProbeError) as caught:
                subject.parse_flags(raw)
            self.assertNotIn("sensitive-example", str(caught.exception))

    def test_aws_cli_errors_are_suppressed_and_wait_failure_stops(self):
        with patch.object(subject.subprocess, "run", return_value=subprocess.CompletedProcess(
            [], 1, "sensitive-example", "sensitive-example",
        )), self.assertRaises(subject.ProbeError) as caught:
            subject.aws_ssm("send-command")
        self.assertNotIn("sensitive-example", str(caught.exception))
        runner = Mock(side_effect=[
            json.dumps({"Command": {"CommandId": COMMAND}}), subject.ProbeError("wait failed"),
        ])
        with self.assertRaises(subject.ProbeError):
            subject.probe(INSTANCE, DIGEST, runner)
        self.assertEqual(runner.call_count, 2)

    def test_fixed_shell_probe_returns_presence_without_reading_env(self):
        document = json.loads(DOCUMENT_PATH.read_text())
        self.assertFalse(document.get("parameters"))
        self.assertEqual(len(document["mainSteps"]), 1)
        step = document["mainSteps"][0]
        self.assertEqual((step["action"], step["name"]), ("aws:runShellScript", subject.STEP))
        with TemporaryDirectory() as folder:
            root = Path(folder)
            docker = root / "docker"
            docker.write_text("#!/bin/sh\ncase \"$1\" in info) exit 0 ;; *) exit 1 ;; esac\n")
            docker.chmod(0o700)
            lines = [
                line for line in step["inputs"]["runCommand"] if not line.startswith(("export PATH=", "deploy_root="))
            ]
            script = 'deploy_root="$PROBE_TEST_ROOT"\n' + "\n".join(lines)
            for configured in (False, True):
                if configured:
                    (root / ".git").mkdir()
                    (root / "deploy").mkdir()
                    (root / "deploy" / ".env").write_text("SMTP_PASSWORD=sensitive-example\n")
                result = subprocess.run(
                    ["sh", "-c", script], check=True, text=True, capture_output=True,
                    env={**os.environ, "PATH": f"{folder}:/usr/bin:/bin", "PROBE_TEST_ROOT": folder},
                )
                self.assertEqual(result.stderr, "")
                self.assertEqual(subject.parse_flags(result.stdout), {
                    "probe_version": "1", "docker_daemon": "ok", "docker_compose": "unavailable",
                    "checkout": "present" if configured else "missing",
                    "deploy_env": "present" if configured else "missing",
                })


if __name__ == "__main__":
    unittest.main()
