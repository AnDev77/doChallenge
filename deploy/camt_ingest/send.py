"""Dispatch a hash-pinned, fixed CAMT command and publish its bounded result."""

import json
import os
import re
import sys
import time
from pathlib import Path
from urllib.parse import urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ssm_probe import ProbeError, aws_ssm, decode_response

from camt_ingest.render_document import DOCUMENT, STEP

INSTANCE = re.compile(r"i-(?:[a-f0-9]{8}|[a-f0-9]{17})")
HASH = re.compile(r"[A-Fa-f0-9]{64}")
COMMAND = re.compile(r"[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")
TRANSIENT = {"Pending", "InProgress", "Delayed"}
FIELDS = {"version", "symbol", "new_versions", "keyword_ready_versions", "accession",
          "accepted_at", "fetched_at", "index_status", "source_url", "raw_sha256",
          "chunk_index", "excerpt"}


def parse_output(raw):
    if not isinstance(raw, str) or len(raw) > 8000 or not raw.startswith("CAMT_INGEST_V1="):
        raise ProbeError("Unexpected CAMT output; suppressed.")
    try:
        data = json.loads(raw[len("CAMT_INGEST_V1="):])
    except ValueError:
        raise ProbeError("Unexpected CAMT output; suppressed.") from None
    if not isinstance(data, dict) or data.keys() != FIELDS:
        raise ProbeError("Unexpected CAMT output; suppressed.")
    try:
        url = urlsplit(data["source_url"]) if isinstance(data["source_url"], str) else None
    except ValueError:
        raise ProbeError("Unexpected CAMT output; suppressed.") from None
    if (data["version"] != 1 or data["symbol"] != "CAMT"
            or data["index_status"] != "KEYWORD_READY"
            or not all(type(data[k]) is int and 0 <= data[k] <= 1000
                       for k in ("new_versions", "keyword_ready_versions", "chunk_index"))
            or not isinstance(data["accession"], str)
            or not re.fullmatch(r"\d{10}-\d{2}-\d{6}", data["accession"])
            or not all(isinstance(data[k], str) and re.fullmatch(
                r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", data[k])
                for k in ("accepted_at", "fetched_at"))
            or not url or url.scheme != "https" or url.hostname != "www.sec.gov"
            or url.username or url.password or url.fragment or url.query
            or not url.path.startswith("/Archives/edgar/data/1109138/")
            or not isinstance(data["raw_sha256"], str)
            or not re.fullmatch(r"[0-9a-f]{64}", data["raw_sha256"])
            or not isinstance(data["excerpt"], str) or not 1 <= len(data["excerpt"]) <= 1000):
        raise ProbeError("Unexpected CAMT output; suppressed.")
    return data


def dispatch(instance, doc_hash, run=aws_ssm, now=time.monotonic, pause=time.sleep):
    if not isinstance(instance, str) or not INSTANCE.fullmatch(instance):
        raise ProbeError("Configure EC2_INSTANCE_ID.")
    if not isinstance(doc_hash, str) or not HASH.fullmatch(doc_hash):
        raise ProbeError("Configure AWS_CAMT_DOCUMENT_SHA256.")
    sent = decode_response(run(
        "send-command", "--document-name", DOCUMENT, "--document-version", "1",
        "--document-hash-type", "Sha256", "--document-hash", doc_hash,
        "--instance-ids", instance, "--timeout-seconds", "60",
    )).get("Command")
    command_id = sent.get("CommandId") if isinstance(sent, dict) else None
    if not isinstance(command_id, str) or not COMMAND.fullmatch(command_id):
        raise ProbeError("Missing SSM command ID.")
    selector = ("--command-id", command_id, "--instance-id", instance, "--plugin-name", STEP)
    deadline = now() + 580
    while now() < deadline:
        try:
            result = decode_response(run("get-command-invocation", *selector))
        except ProbeError:
            # SSM can return InvocationDoesNotExist shortly after dispatch.
            pause(5)
            continue
        if any(result.get(k) != v for k, v in {
                "CommandId": command_id, "InstanceId": instance,
                "DocumentName": DOCUMENT, "DocumentVersion": "1"}.items()):
            raise ProbeError("Unexpected SSM invocation identity.")
        if result.get("Status") in TRANSIENT:
            pause(6)
            continue
        output = result.get("StandardOutputContent")
        if (result.get("Status") == "Success" and result.get("ResponseCode") == 0
                and not result.get("StandardErrorContent")):
            return parse_output(output)
        if isinstance(output, str) and re.fullmatch(r"CAMT_INGEST_FAILED:[a-z]+\n?", output):
            raise ProbeError("CAMT ingest failed at " + output.strip().split(":", 1)[1] + ".")
        raise ProbeError("CAMT ingest failed; server output suppressed.")
    raise ProbeError("CAMT ingest timed out; verify the SSM invocation before retrying.")


def main():
    try:
        data = dispatch(os.environ.get("EC2_INSTANCE_ID"), os.environ.get("AWS_CAMT_DOCUMENT_SHA256"))
    except ProbeError as error:
        print(str(error), file=sys.stderr)
        return 1
    print("CAMT SEC evidence collected and indexed:")
    print(json.dumps(data, ensure_ascii=True, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
