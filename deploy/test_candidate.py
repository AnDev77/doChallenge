import json
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parent / "candidate"))

from render_document import DOCUMENT, STEP, render
from render_stack import render_stack
from send import ProbeError, start

INSTANCE = "i-0123456789abcdef0"
COMMAND = "01234567-89ab-cdef-0123-456789abcdef"
DIGEST = "sha256:" + "a" * 64
HASH = "b" * 64


def response(**overrides):
    result = {
        "InstanceId": INSTANCE, "CommandId": COMMAND, "DocumentName": DOCUMENT,
        "DocumentVersion": "1", "Status": "Success", "ResponseCode": 0,
        "StandardOutputContent": "CANDIDATE_VERIFIED_V1\n", "StandardErrorContent": "",
    }
    return json.dumps(result | overrides)


def run_with(*results):
    return Mock(side_effect=[json.dumps({"Command": {"CommandId": COMMAND}}), *results])


class CandidateTest(unittest.TestCase):
    def test_send_pinned_document_digest_and_wait_for_verified_result(self):
        run = run_with(ProbeError("not visible yet"), response(Status="InProgress"), response())
        pause = Mock()
        start(INSTANCE, DIGEST, HASH, run=run, now=lambda: 0, pause=pause)
        self.assertEqual(run.call_args_list[0].args, (
            "send-command", "--document-name", DOCUMENT, "--document-version", "1",
            "--document-hash-type", "Sha256", "--document-hash", HASH, "--instance-ids", INSTANCE,
            "--timeout-seconds", "60", "--parameters", f"ImageDigest={DIGEST}",
        ))
        for call in run.call_args_list[1:]:
            self.assertEqual(call.args, ("get-command-invocation", "--command-id", COMMAND,
                                          "--instance-id", INSTANCE, "--plugin-name", STEP))
        self.assertEqual(pause.call_count, 2)

    def test_reject_tag_shell_arguments_and_wrong_document_hash_before_aws(self):
        run = Mock()
        for instance, digest, doc_hash in (
            ("i-12345678; id", DIGEST, HASH), (INSTANCE, "latest", HASH),
            (INSTANCE, DIGEST + "; id", HASH), (INSTANCE, DIGEST, ""),
        ):
            with self.subTest(digest=digest), self.assertRaises(ProbeError):
                start(instance, digest, doc_hash, run=run)
        run.assert_not_called()

    def test_fails_closed_and_does_not_echo_server_output(self):
        for result, expected in (
            (response(InstanceId="i-12345678"), "identity"),
            (response(StandardOutputContent="CANDIDATE_VERIFIED_V1\nprivate"), "suppressed"),
            (response(StandardErrorContent="private"), "suppressed"),
            (response(Status="Failed", StandardOutputContent="CANDIDATE_FAILED:smoke\n"), "stage: smoke"),
            (response(Status="Success", ResponseCode=1), "suppressed"),
        ):
            with self.subTest(expected=expected), self.assertRaisesRegex(ProbeError, expected) as err:
                start(INSTANCE, DIGEST, HASH, run=run_with(result))
            self.assertNotIn("private", str(err.exception))

    def test_missing_or_invalid_agent_digest_stops_before_state_changes(self):
        doc = render()
        self.assertEqual(doc["parameters"]["ImageDigest"]["interpolationType"], "ENV_VAR")
        commands = doc["mainSteps"][0]["inputs"]["runCommand"]
        script = "\n".join(commands) + "\n"
        subprocess.run(["bash", "-n"], input=script, text=True, check=True)
        for digest in ("", "latest", DIGEST + "; id"):
            result = subprocess.run(["sh"], input=script, text=True,
                                    env={"PATH": "/usr/bin:/bin", "SSM_ImageDigest": digest},
                                    capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(result.stdout, "CANDIDATE_FAILED:validate\n")
            self.assertEqual(result.stderr, "")

    def test_iam_separates_publish_dispatch_and_instance_pull(self):
        resources = render_stack()["Resources"]
        self.assertEqual(resources["CandidateRepository"]["Properties"]["ImageTagMutability"], "IMMUTABLE")
        def statements(role):
            return resources[role]["Properties"]["Policies"][0]["PolicyDocument"]["Statement"]
        actions = {
            role: {a for s in statements(role) for a in (
                s["Action"] if isinstance(s["Action"], list) else [s["Action"]]
            )}
            for role in ("PublishRole", "RunRole")
        }
        self.assertIn("ecr:PutImage", actions["PublishRole"])
        self.assertNotIn("ssm:SendCommand", actions["PublishRole"])
        self.assertEqual(actions["RunRole"], {"ssm:SendCommand", "ssm:GetCommandInvocation"})
        target = statements("RunRole")[0]["Resource"]
        self.assertEqual(len(target), 2)
        self.assertIn("document/${CandidateDocument}", target[0]["Fn::Sub"])
        self.assertIn("instance/${InstanceId}", target[1]["Fn::Sub"])
        pull = resources["CandidatePullPolicy"]["Properties"]["PolicyDocument"]["Statement"]
        self.assertIn("ecr:GetDownloadUrlForLayer", pull[1]["Action"])
        self.assertNotIn("ecr:PutImage", pull[1]["Action"])


if __name__ == "__main__":
    unittest.main()
