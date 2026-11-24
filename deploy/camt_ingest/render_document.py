"""Embed the reviewed, fixed runner in an SSM document without shell parameters."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DOCUMENT = "ShockGraph-IngestCamtV1"
STEP = "ingestCamt"


def render():
    script = (ROOT / "run.py").read_text(encoding="utf-8")
    command = "python3 - <<'SHOCKGRAPH_CAMT_INGEST'\n" + script + "\nSHOCKGRAPH_CAMT_INGEST\n"
    return {"schemaVersion": "2.2",
            "description": "Ingest only CAMT SEC filings and return one bounded public excerpt",
            "mainSteps": [{"action": "aws:runShellScript", "name": STEP,
                           "inputs": {"timeoutSeconds": "540", "runCommand": command.splitlines()}}]}


if __name__ == "__main__":
    print(json.dumps(render(), indent=2))
