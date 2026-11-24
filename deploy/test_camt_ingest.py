import json
import contextlib
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
from camt_ingest.render_document import DOCUMENT, STEP, render
from camt_ingest.render_stack import render_stack
from camt_ingest.run import IngestError, agent_from_env, evidence_from_mysql
from camt_ingest.send import ProbeError, dispatch, parse_output
from camt_ingest import run as ingest

INSTANCE = "i-0123456789abcdef0"
COMMAND = "01234567-89ab-cdef-0123-456789abcdef"
HASH = "a" * 64
ROW = ("0001178913-26-005000\t2026-11-04T07:03:59Z\t2026-11-24T01:00:00Z\t"
       "KEYWORD_READY\thttps://www.sec.gov/Archives/edgar/data/1109138/filing.htm\t"
       + "b" * 64 + "\t0\t" + "revenue evidence".encode().hex().upper() + "\n")
PAYLOAD = {"version": 1, "symbol": "CAMT", "new_versions": 1,
           "keyword_ready_versions": 1, **evidence_from_mysql(ROW)}


def invocation(**overrides):
    return json.dumps({"CommandId": COMMAND, "InstanceId": INSTANCE,
                       "DocumentName": DOCUMENT, "DocumentVersion": "1",
                       "Status": "Success", "ResponseCode": 0,
                       "StandardOutputContent": "CAMT_INGEST_V1=" + json.dumps(PAYLOAD) + "\n",
                       "StandardErrorContent": ""} | overrides)


class CamtIngestTest(unittest.TestCase):
    def test_fixed_run_collects_then_reads_only_public_camt_excerpt(self):
        with tempfile.TemporaryDirectory() as folder:
            home = Path(folder)
            root = home / "doChallenge"
            (root / ".git").mkdir(parents=True)
            (root / "deploy").mkdir()
            (root / "deploy/compose.yaml").write_text("services: {}\n")
            env_file = root / "deploy/.env"
            env_file.write_text("SEC_USER_AGENT=ShockGraph Operator operator@example.com\n")
            env_file.chmod(0o600)
            responses = ["SEC ingestion completed; new document versions: 1; keyword-ready versions: 1\n", ROW]
            with (patch.object(ingest, "HOME", home), patch.object(ingest, "ROOT", root),
                  patch.object(ingest, "ENV_FILE", env_file),
                  patch.object(ingest, "RAW", home / "shockgraph-sec-filings"),
                  patch.object(ingest, "LOCK", home / "camt.lock"),
                  patch.object(ingest, "COMPOSE", ["docker", "compose", "-f", str(root / "deploy/compose.yaml")]),
                  patch.object(ingest, "checked_process", side_effect=responses) as proc):
                out = io.StringIO()
                with contextlib.redirect_stdout(out):
                    self.assertEqual(ingest.run(), 0)
                data = parse_output(out.getvalue())
                self.assertEqual(data["excerpt"], "revenue evidence")
                args = proc.call_args_list[0].args[0]
                self.assertEqual(args[:2], ["docker", "compose"])
                self.assertIn("--app.briefing.ingestion.lookback-days=30", args)
                self.assertIn("--no-deps", args)
                self.assertNotIn("--app.briefing.operator.action=approve", args)
                self.assertEqual(proc.call_args_list[1].kwargs["input_text"], ingest.SQL)

    def test_env_requires_one_private_identifiable_agent_without_executing_it(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / ".env"
            path.write_text('SEC_USER_AGENT=ShockGraph Operator operator@example.com\n')
            path.chmod(0o600)
            self.assertEqual(agent_from_env(path), "ShockGraph Operator operator@example.com")
            path.chmod(0o644)
            with self.assertRaises(IngestError):
                agent_from_env(path)
            path.chmod(0o600)
            path.write_text('SEC_USER_AGENT=$(touch /tmp/not-executed)\n')
            with self.assertRaises(IngestError):
                agent_from_env(path)

    def test_only_one_bounded_ready_sec_chunk_is_exposed(self):
        result = evidence_from_mysql(ROW)
        self.assertEqual(result["excerpt"], "revenue evidence")
        for row in (ROW + ROW, ROW.replace("KEYWORD_READY", "PENDING"),
                    ROW.replace("https://www.sec.gov", "https://other.example"),
                    ROW.replace("revenue evidence".encode().hex().upper(), "GG")):
            with self.subTest(row=row[:35]), self.assertRaises(IngestError):
                evidence_from_mysql(row)

    def test_pinned_ssm_command_and_allowlisted_response(self):
        run = Mock(side_effect=[json.dumps({"Command": {"CommandId": COMMAND}}),
                                invocation(Status="InProgress"), invocation()])
        pause = Mock()
        self.assertEqual(dispatch(INSTANCE, HASH, run=run, now=lambda: 0, pause=pause), PAYLOAD)
        self.assertEqual(run.call_args_list[0].args, (
            "send-command", "--document-name", DOCUMENT, "--document-version", "1",
            "--document-hash-type", "Sha256", "--document-hash", HASH,
            "--instance-ids", INSTANCE, "--timeout-seconds", "60"))
        self.assertEqual(run.call_args_list[1].args, (
            "get-command-invocation", "--command-id", COMMAND,
            "--instance-id", INSTANCE, "--plugin-name", STEP))
        self.assertEqual(pause.call_count, 1)

    def test_reject_unexpected_output_and_never_print_it(self):
        with self.assertRaisesRegex(ProbeError, "suppressed") as error:
            parse_output("CAMT_INGEST_V1=" + json.dumps(PAYLOAD | {"excerpt": "private", "extra": "secret"}))
        self.assertNotIn("secret", str(error.exception))
        run = Mock()
        with self.assertRaises(ProbeError):
            dispatch("i-invalid; id", HASH, run=run)
        run.assert_not_called()
        run = Mock(side_effect=[json.dumps({"Command": {"CommandId": COMMAND}}),
                                invocation(StandardErrorContent="password=private")])
        with self.assertRaisesRegex(ProbeError, "suppressed") as error:
            dispatch(INSTANCE, HASH, run=run, now=lambda: 0)
        self.assertNotIn("private", str(error.exception))

    def test_document_is_fixed_and_role_cannot_send_arbitrary_commands(self):
        document = render()
        self.assertNotIn("parameters", document)
        script = "\n".join(document["mainSteps"][0]["inputs"]["runCommand"])
        self.assertIn("--app.briefing.ingestion.lookback-days=30", script)
        self.assertIn("d.symbol='CAMT'", script)
        self.assertNotIn("--app.briefing.operator.action=approve", script)
        subprocess.run(["python3", "-m", "py_compile", "deploy/camt_ingest/run.py"], check=True)
        stack = render_stack()
        role = stack["Resources"]["IngestRole"]["Properties"]
        trust = role["AssumeRolePolicyDocument"]["Statement"][0]["Condition"]["StringEquals"]
        self.assertTrue(trust["token.actions.githubusercontent.com:sub"].endswith(
            ":environment:ec2-camt-ingest"))
        permissions = role["Policies"][0]["PolicyDocument"]["Statement"]
        self.assertEqual({s["Action"] for s in permissions},
                         {"ssm:SendCommand", "ssm:GetCommandInvocation"})
        self.assertEqual(len(permissions[0]["Resource"]), 2)
        self.assertIn("document/${IngestDocument}", permissions[0]["Resource"][0]["Fn::Sub"])
        self.assertIn("instance/${InstanceId}", permissions[0]["Resource"][1]["Fn::Sub"])


if __name__ == "__main__":
    unittest.main()
