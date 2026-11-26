import io
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
from unittest.mock import patch, Mock
from contextlib import contextmanager
import image_release as release

IMAGE = '123456789012.dkr.ecr.us-east-2.amazonaws.com/shockgraph-java-candidate@sha256:' + 'a' * 64
DATA = {'version': 1, 'image': IMAGE, 'revision': 'b' * 40, 'platform': 'linux/amd64'}


@contextmanager
def login(*args):
    yield {}


class ImageReleaseTest(TestCase):
    def test_mutable_tag_and_shell_input_are_rejected(self):
        for image in ('repo:latest', IMAGE.replace('@sha256:', ':sha-'), IMAGE + '; echo secret'):
            with self.assertRaises(release.ReleaseError):
                release.validate(DATA | {'image': image})
        with self.assertRaises(release.ReleaseError):
            release.validate(DATA | {'platform': 'linux/arm64'})
        self.assertEqual(release.read_release(io.StringIO(json.dumps(DATA))), DATA)

    def test_env_preserves_secret_values_and_sets_only_one_image(self):
        with TemporaryDirectory() as folder:
            p = Path(folder) / '.env'
            p.write_text('DB_PASSWORD=literal$secret\nAPI_IMAGE=old\n# comment\nAPI_IMAGE=other\n')
            release.update_env(p, IMAGE)
            self.assertIn('DB_PASSWORD=literal$secret\n', p.read_text())
            self.assertEqual(p.read_text().count('API_IMAGE='), 1)
            self.assertEqual(p.stat().st_mode & 0o777, 0o600)

    def test_activation_never_builds_or_restarts_database_and_reloads_dns(self):
        with patch.object(release, 'run') as run:
            release.activate(IMAGE)
        args = run.call_args_list[0].args[0]
        self.assertIn('--no-build', args)
        self.assertIn('--no-deps', args)
        self.assertIn('--wait', args)
        self.assertEqual(args[-1], 'api')
        self.assertEqual(run.call_args_list[-1].args[0][-3:], ['nginx', '-s', 'reload'])

    def apply_case(self, *, activate_error=None, wrong_revision=False):
        folder = TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        root = Path(folder.name)
        (root / 'deploy').mkdir()
        (root / 'deploy/.env').write_text('DB_PASSWORD=keep\nAPI_IMAGE=old\n')
        calls = []
        def run(args, **kwargs):
            calls.append(args)
            if args[-3:] == ['ps', '-q', 'api']:
                return 'container\n'
            if args[:2] == ['docker', 'inspect']:
                return json.dumps([{'Image': 'old-id' if len([x for x in calls if x[:2] == ['docker', 'inspect']]) == 1 else 'new-id',
                                    'State': {'Health': {'Status': 'healthy'}}}])
            if args[:3] == ['docker', 'image', 'inspect']:
                return json.dumps([{'Id': 'new-id', 'Config': {'Labels': {
                    'org.opencontainers.image.revision': 'c' * 40 if wrong_revision else DATA['revision']}},
                    'Os': 'linux', 'Architecture': 'amd64'}])
            return ''
        activator = Mock(side_effect=[release.ReleaseError('unhealthy'), None] if activate_error else None)
        with patch.object(release, 'ROOT', root), patch.object(release, 'run', side_effect=run), \
             patch.object(release, 'ecr_login', login), patch.object(release, 'activate', activator):
            if activate_error or wrong_revision:
                with self.assertRaises(release.ReleaseError):
                    release.apply_locked(DATA)
            else:
                release.apply_locked(DATA)
        return root, calls, activator

    def test_success_records_digest_and_exact_running_image(self):
        root, calls, activate = self.apply_case()
        self.assertIn('API_IMAGE=' + IMAGE, (root / 'deploy/.env').read_text())
        self.assertEqual(activate.call_args.args, (IMAGE,))
        records = [json.loads(s) for s in (root / 'deploy/.image-release-history.jsonl').read_text().splitlines()]
        self.assertEqual([x['event'] for x in records], ['attempt', 'healthy'])
        self.assertEqual(records[0]['previous_image_id'], 'old-id')

    def test_failed_health_restores_captured_previous_image(self):
        root, calls, activate = self.apply_case(activate_error=True)
        self.assertEqual(activate.call_count, 2)
        rollback = activate.call_args.args[0]
        self.assertTrue(rollback.startswith('shockgraph-rollback:'))
        self.assertIn(['docker', 'tag', 'old-id', rollback], calls)
        self.assertIn('API_IMAGE=' + rollback, (root / 'deploy/.env').read_text())
        self.assertNotIn('"event": "healthy"', (root / 'deploy/.image-release-history.jsonl').read_text())

    def test_wrong_revision_never_replaces_running_api(self):
        root, calls, activate = self.apply_case(wrong_revision=True)
        activate.assert_not_called()
        self.assertIn('API_IMAGE=old', (root / 'deploy/.env').read_text())

    def test_dirty_or_unmerged_source_cannot_publish(self):
        args = Mock(region='us-east-2', repository='shockgraph-java-candidate')
        for responses in ([' M file'], ['', '', 'a'*40, 'b'*40]):
            with patch.object(release, 'run', side_effect=responses) as run:
                with self.assertRaises(release.ReleaseError):
                    release.publish(args)
                self.assertFalse(any(c.args[0][0] == 'docker' for c in run.call_args_list))

    def test_deploy_rejects_ssh_option_injection(self):
        with TemporaryDirectory() as folder:
            path = Path(folder) / 'release.json'
            path.write_text(json.dumps(DATA))
            with patch.object(release, 'run') as run:
                with self.assertRaises(release.ReleaseError):
                    release.deploy(Mock(release=str(path), host='-oProxyCommand=evil'))
                run.assert_not_called()

    def test_mutable_ecr_repository_cannot_publish(self):
        args = Mock(region='us-east-2', repository='shockgraph-java-candidate')
        responses = ['', '', DATA['revision'], DATA['revision'],
                     json.dumps({'repositories': [{'imageTagMutability': 'MUTABLE'}]})]
        with patch.object(release, 'run', side_effect=responses) as run:
            with self.assertRaises(release.ReleaseError):
                release.publish(args)
            self.assertFalse(any(c.args[0][0] == 'docker' for c in run.call_args_list))

    def test_publish_pushes_the_smoke_tested_image_without_rebuild(self):
        with TemporaryDirectory() as folder:
            output = Path(folder) / 'release.json'
            args = Mock(region='us-east-2', repository='shockgraph-java-candidate', output=str(output))
            calls = []
            uri, digest = IMAGE.split('@')
            def run(command, **kwargs):
                calls.append(command)
                if command[:2] == ['git', 'rev-parse']:
                    return DATA['revision']
                if command[:3] == ['aws', 'ecr', 'describe-repositories']:
                    return json.dumps({'repositories': [{'imageTagMutability': 'IMMUTABLE', 'repositoryUri': uri}]})
                if command[:3] == ['aws', 'ecr', 'describe-images']:
                    return digest
                return ''
            def smoke(image):
                calls.append(['smoke', image])
            with patch.object(release, 'run', side_effect=run), patch.object(release, 'smoke', side_effect=smoke), \
                 patch.object(release, 'ecr_login', login), patch.object(release.subprocess, 'run') as archive:
                release.publish(args)
            archive.assert_called_once()
            self.assertEqual(archive.call_args.args[0], ['git', 'archive', '--format=tar', DATA['revision']])
            builds = [c for c in calls if c[:3] == ['docker', 'buildx', 'build']]
            self.assertEqual(len(builds), 1)
            built_image = builds[0][builds[0].index('-t') + 1]
            self.assertLess(calls.index(['smoke', built_image]), calls.index(['docker', 'push', built_image]))
            self.assertEqual(json.loads(output.read_text())['image'], IMAGE)
