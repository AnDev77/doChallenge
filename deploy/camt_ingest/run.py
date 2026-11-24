"""Fixed EC2 SSM command: ingest CAMT, then report one public SEC evidence excerpt."""

import fcntl
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
from urllib.parse import urlsplit

HOME = Path("/home/ubuntu")
ROOT = HOME / "doChallenge"
ENV_FILE = ROOT / "deploy/.env"
RAW = HOME / "shockgraph-sec-filings"
LOCK = Path("/var/lock/shockgraph-camt-ingest.lock")
COMPOSE = ["docker", "compose", "-p", "deploy", "--env-file", str(ENV_FILE),
           "-f", str(ROOT / "deploy/compose.yaml")]
INGEST_LINE = re.compile(r"SEC ingestion completed; new document versions: (\d+); keyword-ready versions: (\d+)")
ACCESSION = re.compile(r"\d{10}-\d{2}-\d{6}")

SQL = """
SELECT d.accession,
       DATE_FORMAT(d.accepted_at, '%Y-%m-%dT%H:%i:%sZ'),
       DATE_FORMAT(d.fetched_at, '%Y-%m-%dT%H:%i:%sZ'),
       d.index_status, d.source_url, d.raw_sha256, c.ordinal_no,
       HEX(LEFT(c.section_text, 1000))
FROM rag_document d JOIN rag_chunk c ON c.document_id=d.id
WHERE d.symbol='CAMT' AND d.index_status='KEYWORD_READY'
  AND d.accepted_at>=UTC_TIMESTAMP()-INTERVAL 30 DAY
ORDER BY d.accepted_at DESC,
         (LOWER(c.section_text) LIKE '%revenue%') DESC, c.ordinal_no
LIMIT 1;
"""


class IngestError(Exception):
    pass


def agent_from_env(path):
    metadata = path.lstat()
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_mode & 0o077:
        raise IngestError("config")
    lines = path.read_text(encoding="utf-8").splitlines()
    values = [line.partition("=")[2].strip() for line in lines
              if line.partition("=")[0].strip() == "SEC_USER_AGENT" and "=" in line]
    if len(values) != 1:
        raise IngestError("config")
    value = values[0]
    if len(value) >= 2 and value[0] in "\"'" and value[-1] == value[0]:
        value = value[1:-1]
    if not (5 <= len(value) <= 160 and "@" in value and
            all(32 <= ord(char) <= 126 for char in value)):
        raise IngestError("config")
    return value


def evidence_from_mysql(output):
    lines = output.splitlines()
    if len(lines) != 1:
        raise IngestError("evidence")
    fields = lines[0].split("\t")
    if len(fields) != 8:
        raise IngestError("evidence")
    accession, accepted, fetched, status, url, digest, ordinal, encoded = fields
    target = urlsplit(url)
    if (not ACCESSION.fullmatch(accession) or status != "KEYWORD_READY"
            or target.scheme != "https" or target.hostname != "www.sec.gov"
            or target.username or target.password or target.fragment or target.query
            or not target.path.startswith("/Archives/edgar/data/1109138/")
            or not re.fullmatch(r"[0-9a-f]{64}", digest)
            or not ordinal.isdecimal() or not re.fullmatch(r"[0-9A-F]{2,8000}", encoded)):
        raise IngestError("evidence")
    try:
        excerpt = bytes.fromhex(encoded).decode("utf-8")
    except (ValueError, UnicodeError):
        raise IngestError("evidence") from None
    if not excerpt or len(excerpt) > 1000:
        raise IngestError("evidence")
    if not all(re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", date)
               for date in (accepted, fetched)):
        raise IngestError("evidence")
    return {"accession": accession, "accepted_at": accepted, "fetched_at": fetched,
            "index_status": status, "source_url": url, "raw_sha256": digest,
            "chunk_index": int(ordinal), "excerpt": excerpt}


def checked_process(args, *, input_text=None, timeout=300):
    try:
        result = subprocess.run(args, cwd=ROOT, input=input_text, text=True,
                                capture_output=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise IngestError("execution") from None
    if result.returncode or len(result.stdout) > 20000:
        raise IngestError("execution")
    return result.stdout


def run():
    os.environ["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    stage = "config"
    try:
        if not (ROOT / ".git").exists() or not (ROOT / "deploy/compose.yaml").is_file():
            raise IngestError(stage)
        agent = agent_from_env(ENV_FILE)
        owner = HOME.stat()
        if RAW.is_symlink():
            raise IngestError(stage)
        RAW.mkdir(mode=0o700, exist_ok=True)
        os.chown(RAW, owner.st_uid, owner.st_gid)
        RAW.chmod(0o700)
        if LOCK.is_symlink():
            raise IngestError(stage)
        with LOCK.open("a+") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            stage = "ingest"
            output = checked_process(COMPOSE + ["run", "--rm", "--no-deps",
                    "--user", f"{owner.st_uid}:{owner.st_gid}",
                    "--volume", f"{RAW}:/app/data/sec-filings",
                    "-e", f"SEC_USER_AGENT={agent}", "api",
                    "--spring.main.web-application-type=none",
                    "--app.briefing.ingestion.run-once=true",
                    "--app.briefing.ingestion.lookback-days=30"], timeout=480)
            match = INGEST_LINE.search(output)
            if not match:
                raise IngestError(stage)
            stage = "evidence"
            rows = checked_process(COMPOSE + ["exec", "-T", "mysql", "sh", "-c",
                    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" '
                    '--batch --skip-column-names'], input_text=SQL, timeout=25)
            evidence = evidence_from_mysql(rows)
            result = {"version": 1, "symbol": "CAMT", "new_versions": int(match.group(1)),
                      "keyword_ready_versions": int(match.group(2)), **evidence}
            print("CAMT_INGEST_V1=" + json.dumps(result, ensure_ascii=True, separators=(",", ":")))
            return 0
    except (IngestError, OSError, UnicodeError, ValueError, BlockingIOError):
        print(f"CAMT_INGEST_FAILED:{stage}")
        return 1


if __name__ == "__main__":
    sys.exit(run())
