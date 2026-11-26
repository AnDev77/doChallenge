"""Publish a committed image once; apply its ECR digest to the existing single API."""
import argparse
import contextlib
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = r'[0-9]{12}\.dkr\.ecr\.(?P<region>[a-z]{2}-[a-z]+-[0-9])\.amazonaws\.com'
IMAGE = re.compile(REGISTRY + r'/[a-z0-9]+(?:[._/-][a-z0-9]+)*@sha256:[a-f0-9]{64}')
SHA = re.compile(r'[a-f0-9]{40}')


class ReleaseError(Exception):
    pass


def run(args, *, input=None, env=None, cwd=ROOT, timeout=900):
    try:
        p = subprocess.run(args, input=input, text=not isinstance(input, bytes),
                           capture_output=True, cwd=cwd, env=env, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ReleaseError('Command could not complete: ' + args[0]) from exc
    if p.returncode:
        # Compose output can contain interpolated secrets; do not echo stderr.
        raise ReleaseError('Command failed: ' + ' '.join(args[:3]))
    return p.stdout.decode() if isinstance(p.stdout, bytes) else p.stdout


def validate(data):
    if (not isinstance(data, dict) or data.get('version') != 1
            or not isinstance(data.get('image'), str) or not IMAGE.fullmatch(data['image'])
            or not isinstance(data.get('revision'), str) or not SHA.fullmatch(data['revision'])
            or data.get('platform') != 'linux/amd64'):
        raise ReleaseError('Expected an ECR digest, full Git SHA and linux/amd64 release.')
    return data


def read_release(stream):
    try:
        raw = stream.read(16385)
        if len(raw) > 16384:
            raise ValueError()
        return validate(json.loads(raw))
    except (ValueError, TypeError):
        raise ReleaseError('Invalid release manifest.') from None


@contextlib.contextmanager
def ecr_login(registry, region):
    with tempfile.TemporaryDirectory(prefix='shockgraph-docker-') as folder:
        env = dict(os.environ, DOCKER_CONFIG=folder, AWS_PAGER='')
        token = run(['aws', 'ecr', 'get-login-password', '--region', region], env=env)
        run(['docker', 'login', '--username', 'AWS', '--password-stdin', registry],
            input=token, env=env)
        yield env


def smoke(image):
    env = dict(os.environ, CANDIDATE_IMAGE=image, DB_PASSWORD=secrets.token_hex(32),
               MYSQL_ROOT_PASSWORD=secrets.token_hex(32), JWT_SECRET=secrets.token_hex(32))
    project = 'shockgraph-image-test-' + secrets.token_hex(6)
    compose = ['docker', 'compose', '-p', project, '-f', 'deploy/candidate/compose.yaml']
    try:
        run(compose + ['up', '-d', '--no-build', '--wait', '--wait-timeout', '240',
                       'mysql', 'redis', 'api'], env=env)
        data = json.loads(run(compose + ['exec', '-T', 'api', 'curl', '-fsS', '--max-time', '10',
                                        'http://127.0.0.1:8080/actuator/health'], env=env))
        if data.get('status') != 'UP':
            raise ReleaseError('Candidate health failed.')
        for symbol in ('QQQ', 'SPY'):
            data = json.loads(run(compose + ['exec', '-T', 'api', 'curl', '-fsS', '--max-time', '10',
                      'http://127.0.0.1:8080/api/v1/finance/rooms/' + symbol + '/messages'], env=env))
            if data.get('success') is not True or not isinstance(data.get('data'), list):
                raise ReleaseError('Candidate chat contract failed.')
    finally:
        # This uniquely named disposable local project never uses production volumes.
        run(compose + ['down', '--volumes', '--remove-orphans'], env=env)


def publish(args):
    if run(['git', 'status', '--porcelain']).strip():
        raise ReleaseError('Commit changes first; release checkout must be clean.')
    run(['git', 'fetch', 'origin', 'main'])
    sha = run(['git', 'rev-parse', 'HEAD']).strip()
    if sha != run(['git', 'rev-parse', 'FETCH_HEAD']).strip():
        raise ReleaseError('Publish only the current merged main commit.')
    repo = json.loads(run(['aws', 'ecr', 'describe-repositories', '--region', args.region,
                          '--repository-names', args.repository]))['repositories'][0]
    if repo['imageTagMutability'] != 'IMMUTABLE':
        raise ReleaseError('ECR repository must use IMMUTABLE tags without exclusions.')
    uri = repo['repositoryUri']
    if not re.fullmatch(REGISTRY + r'/[a-z0-9]+(?:[._/-][a-z0-9]+)*', uri):
        raise ReleaseError('Invalid ECR repository URI.')
    tag = 'git-' + sha + '-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + secrets.token_hex(3)
    image = uri + ':' + tag
    # Git archive excludes ignored local .env, keys and untracked files by construction.
    with tempfile.TemporaryFile() as archive:
        subprocess.run(['git', 'archive', '--format=tar', sha], cwd=ROOT, stdout=archive, check=True)
        archive.seek(0)
        run(['docker', 'buildx', 'build', '--platform', 'linux/amd64', '--load',
             '-f', 'deploy/Dockerfile', '--label', 'org.opencontainers.image.revision=' + sha,
             '--label', 'org.opencontainers.image.source=https://github.com/AnDev77/doChallenge',
             '-t', image, '-'], input=archive.read(), timeout=1800)
    smoke(image)
    registry = uri.split('/')[0]
    with ecr_login(registry, args.region) as env:
        run(['docker', 'push', image], env=env, timeout=1800)
    digest = run(['aws', 'ecr', 'describe-images', '--region', args.region,
                  '--repository-name', args.repository, '--image-ids', 'imageTag=' + tag,
                  '--query', 'imageDetails[0].imageDigest', '--output', 'text']).strip()
    data = validate({'version': 1, 'image': uri + '@' + digest, 'revision': sha,
                     'platform': 'linux/amd64', 'tag': tag})
    Path(args.output).write_text(json.dumps(data, indent=2) + '\n')
    print('Published and locally smoke-tested: ' + data['image'])
    print('Release manifest: ' + args.output)


def update_env(path, image):
    if path.is_symlink() or not path.is_file():
        raise ReleaseError('Expected regular deploy/.env file.')
    info = path.stat()
    lines = [line for line in path.read_text().splitlines()
             if line.partition('=')[0].strip() != 'API_IMAGE']
    with tempfile.NamedTemporaryFile(mode='w', dir=path.parent, delete=False) as tmp:
        tmp.write('\n'.join(lines) + '\nAPI_IMAGE=' + image + '\n')
        tmp.flush()
        os.fsync(tmp.fileno())
        os.chmod(tmp.name, 0o600)
        if hasattr(os, 'chown'):
            os.chown(tmp.name, info.st_uid, info.st_gid)
    os.replace(tmp.name, path)


def compose(image):
    env = dict(os.environ, API_IMAGE=image)
    cmd = ['docker', 'compose', '-p', 'deploy', '--env-file', str(ROOT / 'deploy/.env'),
           '-f', str(ROOT / 'deploy/compose.yaml')]
    return cmd, env


def activate(image):
    cmd, env = compose(image)
    run(cmd + ['up', '-d', '--no-build', '--pull', 'never', '--no-deps', '--wait',
               '--wait-timeout', '180', 'api'], env=env, timeout=240)
    run(cmd + ['exec', '-T', 'nginx', 'nginx', '-t'], env=env)
    # Static upstream DNS must resolve the recreated API container again.
    run(cmd + ['exec', '-T', 'nginx', 'nginx', '-s', 'reload'], env=env)


def apply(data):
    import fcntl  # EC2/Linux only; publish/deploy also run in WSL.
    data = validate(data)
    with (ROOT / 'deploy/.image-release.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        # Share the ingestion lock: don't replace the API/image during a collection job.
        with Path('/var/lock/shockgraph-camt-ingest.lock').open('a') as ingest_lock:
            fcntl.flock(ingest_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            apply_locked(data)


def apply_locked(data):
    image = data['image']
    config = ROOT / 'deploy/.env'
    if config.is_symlink() or not config.is_file():
        raise ReleaseError('Expected regular deploy/.env file.')
    cmd, env = compose(image)
    run(cmd + ['config', '--quiet'], env=env)
    run(cmd + ['exec', '-T', 'nginx', 'nginx', '-t'], env=env)
    ids = run(cmd + ['ps', '-q', 'api'], env=env).split()
    if len(ids) != 1:
        raise ReleaseError('Expected one running single-stack api. Initial provisioning is separate.')
    current = json.loads(run(['docker', 'inspect', ids[0]]))[0]
    if current['State'].get('Health', {}).get('Status') != 'healthy':
        raise ReleaseError('Current API must be healthy before replacing it.')
    old_id = current['Image']
    rollback = 'shockgraph-rollback:' + secrets.token_hex(12)
    run(['docker', 'tag', old_id, rollback])
    match = IMAGE.fullmatch(image)
    with ecr_login(image.split('/')[0], match.group('region')) as login_env:
        run(['docker', 'pull', image], env=login_env)
    metadata = json.loads(run(['docker', 'image', 'inspect', image]))[0]
    if (metadata['Config'].get('Labels', {}).get('org.opencontainers.image.revision') != data['revision']
            or metadata.get('Os') != 'linux' or metadata.get('Architecture') != 'amd64'):
        raise ReleaseError('Pulled image revision/platform does not match manifest.')
    history = ROOT / 'deploy/.image-release-history.jsonl'
    with history.open('a') as log:
        os.chmod(history, 0o600)
        log.write(json.dumps({'event': 'attempt', 'image': image, 'revision': data['revision'],
                             'previous_image_id': old_id, 'rollback_tag': rollback,
                             'at': datetime.now(timezone.utc).isoformat()}) + '\n')
    try:
        activate(image)
        actual = run(cmd + ['ps', '-q', 'api'], env=env).strip()
        if json.loads(run(['docker', 'inspect', actual]))[0]['Image'] != metadata['Id']:
            raise ReleaseError('Running image differs from pulled digest.')
        update_env(ROOT / 'deploy/.env', image)
    except Exception as failure:
        try:
            activate(rollback)
            update_env(ROOT / 'deploy/.env', rollback)
            with history.open('a') as log:
                log.write(json.dumps({'event': 'rolled_back', 'image': rollback, 'image_id': old_id,
                                     'at': datetime.now(timezone.utc).isoformat()}) + '\n')
        except Exception as recovery:
            raise ReleaseError('Release failed AND previous image recovery failed; inspect EC2.') from recovery
        raise ReleaseError('Release failed; previous container image restored. DB migrations are not reversed.') from failure
    with history.open('a') as log:
        log.write(json.dumps({'event': 'healthy', **data, 'at': datetime.now(timezone.utc).isoformat()}) + '\n')
    print('API healthy; Nginx reloaded; selected image persisted: ' + image)


def deploy(args):
    with open(args.release) as source:
        data = read_release(source)
    if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_.@-]*', args.host):
        raise ReleaseError('Use a host alias from ~/.ssh/config.')
    remote = 'sudo -n python3 /home/ubuntu/doChallenge/deploy/image_release.py apply'
    # SSH auth and host verification use the user's existing OpenSSH configuration.
    print(run(['ssh', '-o', 'BatchMode=yes', args.host, remote], input=json.dumps(data), timeout=1200))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    pub = commands.add_parser('publish')
    pub.add_argument('--region', default='us-east-2')
    pub.add_argument('--repository', default='shockgraph-java-candidate')
    pub.add_argument('--output', default='release.json')
    dep = commands.add_parser('deploy')
    dep.add_argument('--host', required=True)
    dep.add_argument('--release', default='release.json')
    commands.add_parser('apply')
    args = parser.parse_args()
    try:
        if args.command == 'publish':
            publish(args)
        elif args.command == 'deploy':
            deploy(args)
        else:
            apply(read_release(sys.stdin))
    except (ReleaseError, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print('Release did not complete: ' + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
