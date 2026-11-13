"""Send only the fixed candidate SSM document and check its result."""

import argparse
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ssm_probe import ProbeError, aws_ssm, decode_response

from render_document import DIGEST_PATTERN, DOCUMENT, STAGES, STEP

SUCCESS = "CANDIDATE_VERIFIED_V1"
TRANSIENT = {"Pending", "InProgress", "Delayed", "Cancelling"}


def start(instance, digest, doc_hash, run=aws_ssm, now=time.monotonic, pause=time.sleep):
    if not re.fullmatch(r"i-(?:[a-f0-9]{8}|[a-f0-9]{17})", instance):
        raise ProbeError("Invalid EC2 instance ID.")
    if not re.fullmatch(DIGEST_PATTERN, digest):
        raise ProbeError("Invalid ECR image digest.")
    if not re.fullmatch(r"[a-fA-F0-9]{64}", doc_hash):
        raise ProbeError("Invalid SSM document SHA-256.")
    sent = decode_response(run(
        "send-command", "--document-name", DOCUMENT, "--document-version", "1",
        "--document-hash-type", "Sha256", "--document-hash", doc_hash,
        "--instance-ids", instance, "--timeout-seconds", "60",
        "--parameters", f"ImageDigest={digest}",
    )).get("Command")
    command_id = sent.get("CommandId") if isinstance(sent, dict) else None
    if not isinstance(command_id, str) or not re.fullmatch(
        r"[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}", command_id
    ):
        raise ProbeError("SSM did not return a valid command ID.")

    deadline = now() + 690
    while now() < deadline:
        try:
            raw = run("get-command-invocation", "--command-id", command_id,
                      "--instance-id", instance, "--plugin-name", STEP)
        except ProbeError:
            # SSM can return InvocationDoesNotExist immediately after dispatch.
            if now() >= deadline - 660:
                raise
            pause(5)
            continue
        result = decode_response(raw)
        expected = {
            "CommandId": command_id, "InstanceId": instance,
            "DocumentName": DOCUMENT, "DocumentVersion": "1",
        }
        if any(result.get(k) != v for k, v in expected.items()):
            raise ProbeError("Unexpected SSM invocation identity.")
        status = result.get("Status")
        if status in TRANSIENT:
            pause(8)
            continue
        output = result.get("StandardOutputContent")
        if (status == "Success" and result.get("ResponseCode") == 0
                and output in (SUCCESS, SUCCESS + "\n") and not result.get("StandardErrorContent")):
            return
        if isinstance(output, str) and output.strip().startswith("CANDIDATE_FAILED:"):
            stage = output.strip().removeprefix("CANDIDATE_FAILED:")
            if stage in STAGES:
                raise ProbeError(f"Candidate did not pass stage: {stage}.")
        raise ProbeError("SSM candidate command did not verify; server output suppressed.")
    raise ProbeError("Timed out waiting for the candidate; check SSM before rerunning.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instance", required=True)
    parser.add_argument("--digest", required=True)
    parser.add_argument("--document-hash", required=True)
    args = parser.parse_args()
    try:
        start(args.instance, args.digest, args.document_hash)
    except ProbeError as error:
        print(f"Candidate not verified: {error}", file=sys.stderr)
        return 1
    print("EC2 candidate verified: Java health, QQQ/SPY reads and anonymous write rejection.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
