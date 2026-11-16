"""Render a fixed SSM document with the candidate Compose file embedded."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
STAGES = {"validate", "lock", "registry", "settings", "start", "smoke"}
DIGEST_PATTERN = r"^sha256:[0-9a-f]{64}$"
DOCUMENT = "ShockGraph-StartCandidateV1"
STEP = "startCandidate"


def render():
    compose = (ROOT / "compose.yaml").read_text().rstrip()
    checks = (ROOT / "check.sh").read_text()
    run = (ROOT / "run.sh").read_text().replace("__COMPOSE_YAML__", compose)
    script = "bash <<'SHOCKGRAPH_RUN'\nset -Eeuo pipefail\nexec 3>&1 >/dev/null 2>&1\n"
    script += checks + "\n" + run + "SHOCKGRAPH_RUN\n"
    return {
        "schemaVersion": "2.2",
        "description": "Start and verify only the isolated ShockGraph Java candidate; no traffic switch.",
        "parameters": {
            "ImageDigest": {
                "type": "String",
                "interpolationType": "ENV_VAR",
                "allowedPattern": DIGEST_PATTERN,
            },
        },
        "mainSteps": [{
            "action": "aws:runShellScript",
            "name": STEP,
            "inputs": {"timeoutSeconds": "600", "runCommand": script.splitlines()},
        }],
    }


if __name__ == "__main__":
    print(json.dumps(render(), indent=2))
