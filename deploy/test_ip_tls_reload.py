"""Fail-closed renewal hook checks without touching an EC2 host."""

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import ip_tls_reload


class IpTlsReloadTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        cert = root / "18.191.135.212"
        cert.mkdir()
        (cert / "fullchain.pem").touch()
        (cert / "privkey.pem").touch()
        (root / "deploy").mkdir()
        (root / "deploy/.env").touch()
        self.lineage = str(cert)
        self.root = patch.object(ip_tls_reload, "ROOT", root)
        self.root.start()
        self.addCleanup(self.root.stop)

    def test_valid_renewal_recreates_standby_then_active_without_database(self):
        events = []

        def fake_run(*args, capture=False):
            events.append(args)
            if "ps" in args:
                return "nginx\nnginx-standby\n"
            return ""

        with patch.object(ip_tls_reload, "run", side_effect=fake_run):
            ip_tls_reload.reload_renewed_ip("18.191.135.212", self.lineage,
                                            "18.191.135.212")
        recreates = [args[-1] for args in events if "--force-recreate" in args]
        self.assertEqual(recreates, ["nginx-standby", "nginx"])
        self.assertFalse(any("mysql" in args or "redis" in args for args in events))
        self.assertEqual(sum("curl" == args[0] for args in events), 2)

    def test_wrong_or_private_ip_does_not_touch_docker(self):
        with patch.object(ip_tls_reload, "run") as run:
            for ip in ("172.31.13.100", "127.0.0.1", "not-an-ip"):
                with self.assertRaises(ValueError):
                    ip_tls_reload.reload_renewed_ip(ip, self.lineage, ip)
            with self.assertRaises(ValueError):
                ip_tls_reload.reload_renewed_ip("18.191.135.212", self.lineage,
                                                "18.191.135.213")
            run.assert_not_called()

    def test_missing_proxy_blocks_recreation(self):
        def fake_run(*args, capture=False):
            return "nginx\n" if "ps" in args else ""

        with patch.object(ip_tls_reload, "run", side_effect=fake_run) as run:
            with self.assertRaises(RuntimeError):
                ip_tls_reload.reload_renewed_ip("18.191.135.212", self.lineage,
                                                "18.191.135.212")
            self.assertFalse(any("--force-recreate" in call.args for call in run.call_args_list))

    def test_nginx_webroot_and_redirect_are_distinct(self):
        nginx = (Path(__file__).parent / "nginx.bluegreen.conf").read_text()
        compose = (Path(__file__).parent / "compose.bluegreen.yaml").read_text()
        self.assertIn("location ^~ /.well-known/acme-challenge/", nginx)
        self.assertIn("try_files $uri =404", nginx)
        self.assertEqual(compose.count("./acme-webroot:/var/www/certbot:ro"), 2)


if __name__ == "__main__":
    unittest.main()
