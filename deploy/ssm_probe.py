"""Run the fixed EC2 SSM probe and publish only its allowlisted flags."""

import json
import os
import re
import subprocess
import sys

DOCUMENT = "ShockGraph-ReadOnlyPreflight"
VERSION = "1"
STEP = "readOnlyPreflight"
FLAGS = {
    "probe_version": {"1"},
    "docker_daemon": {"ok", "unavailable"},
    "docker_compose": {"ok", "unavailable"},
    "checkout": {"present", "missing"},
    "deploy_env": {"present", "missing"},
}


class ProbeError(Exception):
    """A safe, operator-facing failure."""


def aws_ssm(*args, timeout=25):
    try:
        result = subprocess.run(
            ["aws", "ssm", *args, "--no-cli-pager", "--output", "json",
             "--cli-connect-timeout", "5", "--cli-read-timeout", "10"],
            capture_output=True, text=True, check=False, timeout=timeout,
        )
    except (OSError, subprocess.TimeoutExpired, UnicodeError):
        raise ProbeError(f"Could not complete SSM {args[0]}.") from None
    if result.returncode:
        raise ProbeError(f"SSM {args[0]} failed; check IAM, node status and document version/hash.")
    return result.stdout


def decode_response(raw):
    try:
        value = json.loads(raw)
    except (TypeError, ValueError):
        raise ProbeError("Invalid SSM response.") from None
    if not isinstance(value, dict):
        raise ProbeError("Invalid SSM response.")
    return value


def parse_flags(raw):
    if not isinstance(raw, str) or len(raw) > 256:
        raise ProbeError("Unexpected server output; suppressed.")
    found = {}
    for line in raw.splitlines():
        key, sep, value = line.partition("=")
        if not sep or key not in FLAGS or key in found or value not in FLAGS[key]:
            raise ProbeError("Unexpected server output; suppressed.")
        found[key] = value
    if found.keys() != FLAGS.keys():
        raise ProbeError("Incomplete server response; suppressed.")
    return {key: found[key] for key in FLAGS}


def probe(instance, digest, run=aws_ssm):
    if not re.fullmatch(r"i-(?:[a-f0-9]{8}|[a-f0-9]{17})", instance):
        raise ProbeError("Configure EC2_INSTANCE_ID for the probe environment.")
    if not re.fullmatch(r"[A-Fa-f0-9]{64}", digest):
        raise ProbeError("Configure AWS_SSM_PROBE_DOCUMENT_SHA256 for the probe environment.")

    sent = decode_response(run(
        "send-command", "--document-name", DOCUMENT, "--document-version", VERSION,
        "--document-hash-type", "Sha256", "--document-hash", digest,
        "--instance-ids", instance, "--timeout-seconds", "45",
    )).get("Command")
    command_id = sent.get("CommandId") if isinstance(sent, dict) else None
    if not isinstance(command_id, str) or not re.fullmatch(
        r"[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}", command_id
    ):
        raise ProbeError("Missing or invalid SSM command ID.")

    selector = ("--command-id", command_id, "--instance-id", instance, "--plugin-name", STEP)
    run("wait", "command-executed", *selector, timeout=125)
    result = decode_response(run("get-command-invocation", *selector))
    expected = {
        "CommandId": command_id,
        "InstanceId": instance,
        "DocumentName": DOCUMENT,
        "DocumentVersion": VERSION,
        "Status": "Success",
        "ResponseCode": 0,
    }
    if any(result.get(key) != value for key, value in expected.items()):
        raise ProbeError("The expected SSM invocation did not succeed.")
    if result.get("StandardErrorContent"):
        raise ProbeError("Unexpected server error output; suppressed.")
    return parse_flags(result.get("StandardOutputContent"))


def main():
    try:
        report = probe(
            os.environ.get("EC2_INSTANCE_ID", ""),
            os.environ.get("AWS_SSM_PROBE_DOCUMENT_SHA256", ""),
        )
    except ProbeError as error:
        print(f"EC2 SSM probe not verified: {error}", file=sys.stderr)
        return 1
    print("EC2 SSM connection verified; deployment was not attempted.")
    for key, value in report.items():
        print(f"{key}={value}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
