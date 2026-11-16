import io
import json
import unittest
from urllib.error import HTTPError, URLError

from verify_web_api import CheckFailed, NoRedirect, verify

API = "https://api.example.invalid"
WEB = "https://web.example.invalid"
HTML = b"<html>not Java</html>"


class Response(io.BytesIO):
    def __init__(self, headers, body, status=200):
        super().__init__(body)
        self.status = status
        self.headers = headers


class FakeServer:
    def __init__(self, fault=""):
        self.fault = fault
        self.requests = []

    def open(self, request, timeout):
        path = request.selector
        self.requests.append((request.get_method(), path, dict(request.header_items())))
        if self.fault == "tls":
            raise URLError("DO NOT PRINT server detail")
        if path == "/healthz":
            if self.fault == "redirect":
                raise HTTPError(request.full_url, 308, "Moved", {"Location": WEB}, io.BytesIO())
            return Response({}, b"tls-ok")
        headers = {"Access-Control-Allow-Origin": "*" if self.fault == "origin" else WEB}
        if request.get_method() == "OPTIONS":
            headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
            headers["Access-Control-Allow-Headers"] = (
                "Content-Type" if self.fault == "headers" else "Content-Type, Authorization"
            )
            return Response(headers, b"", status=204)
        headers["Content-Type"] = "text/html" if self.fault == "html" else "application/json"
        symbol = "QQQ" if "QQQ" in path and self.fault != "wrong-room" else "SPY"
        body = HTML if self.fault == "html" else json.dumps({
            "success": True, "data": [{"symbol": symbol, "content": "DO NOT PRINT member content"}],
        }).encode()
        return Response(headers, body)


class VerifyWebApiTest(unittest.TestCase):
    def test_valid_proxy_java_and_preflight_contract(self):
        server = FakeServer()
        reports = []
        verify(API, WEB, server, reports.append)
        self.assertEqual([m for m, _, _ in server.requests], ["GET", "GET", "GET", "OPTIONS", "OPTIONS"])
        self.assertEqual(len(reports), 5)
        self.assertNotIn("DO NOT PRINT", " ".join(reports))
        self.assertEqual(server.requests[-1][2]["Access-control-request-headers"], "authorization, content-type")

    def test_proxy_success_does_not_mask_bad_backend_or_cors(self):
        for fault, detail in (
            ("html", "Expected JSON"),
            ("origin", "did not allow"),
            ("headers", "headers were not allowed"),
            ("wrong-room", "Unexpected chat response"),
        ):
            with self.subTest(fault=fault), self.assertRaisesRegex(CheckFailed, detail):
                verify(API, WEB, FakeServer(fault), lambda _: None)

    def test_tls_and_redirect_fail_without_exposing_details(self):
        for fault, detail in (("tls", "certificate"), ("redirect", "HTTP 308")):
            with self.subTest(fault=fault), self.assertRaisesRegex(CheckFailed, detail) as error:
                verify(API, WEB, FakeServer(fault), lambda _: None)
            self.assertNotIn("DO NOT PRINT", str(error.exception))
        self.assertIsNone(NoRedirect().redirect_request(None, None, 302, "", {}, WEB))

    def test_bad_origins_never_make_a_request(self):
        for api in ("http://example.invalid", f"{API}/", f"{API}?token=x", "https://user@api.invalid"):
            with self.subTest(api=api), self.assertRaises(CheckFailed):
                fake = FakeServer()
                verify(api, WEB, fake)
            self.assertEqual(fake.requests, [])


if __name__ == "__main__":
    unittest.main()
