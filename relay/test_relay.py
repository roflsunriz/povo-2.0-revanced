import http.client
import json
from pathlib import Path
import tempfile
import threading
import unittest
import shutil
import ssl
import subprocess
from email.message import Message
from unittest.mock import Mock

from relay.server import Handler, RelayServer, check_tokens
from relay.storage import StatusStore, reset_database

WRITE = "w" * 32
READ = "r" * 32
NOW = 1788500000000


def snapshot():
    return dict(schema_version=1, observed_at_ms=NOW, expiry_at_ms=NOW + 3600000,
                expiry_source="server", expiry_observed_at_ms=NOW,
                code_deadline_at_ms=None, automatic_renewal=True, applied_uses=4,
                max_uses=24, renewal_state="idle", last_applied_at_ms=None)


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.db = Path(self.temp.name) / "status.sqlite3"
        self.now = NOW
        self.start()

    def start(self):
        self.server = RelayServer(("127.0.0.1", 0), StatusStore(self.db), WRITE, READ,
                                  clock=lambda: self.now)
        self.thread = threading.Thread(target=self.server.serve_forever)
        self.thread.start()

    def stop(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def tearDown(self):
        self.stop()
        self.temp.cleanup()

    def request(self, method="GET", token=READ, value=None, raw=None, headers=None, path="/api/v1/status"):
        body = raw if raw is not None else (json.dumps(value) if value is not None else None)
        auth = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
        auth.update(headers or {})
        connection = http.client.HTTPConnection(*self.server.server_address, timeout=3)
        try:
            connection.request(method, path, body=body, headers=auth)
            response = connection.getresponse()
            self.assertEqual("no-store", response.getheader("Cache-Control"))
            return response.status, json.loads(response.read())
        finally:
            connection.close()

    def test_round_trip_and_restart(self):
        self.assertEqual((503, {"error": "status_unavailable"}), self.request())
        self.assertEqual(200, self.request("PUT", WRITE, snapshot())[0])
        self.stop()
        self.start()
        status, result = self.request()
        self.assertEqual(200, status)
        self.assertEqual(3600, result.pop("remaining_seconds"))
        self.assertFalse(result.pop("stale"))
        self.assertFalse(result.pop("renewal_confirmation_pending"))
        self.assertEqual(NOW, result.pop("server_time_ms"))
        self.assertEqual(NOW, result.pop("received_at_ms"))
        self.assertEqual(snapshot(), result)

    def test_separate_authentication(self):
        for method, wrong in (("GET", WRITE), ("PUT", READ), ("GET", "bad")):
            self.assertEqual(401, self.request(method, wrong, snapshot())[0])
        self.assertEqual(503, self.request()[0])

    def test_invalid_payloads_do_not_replace(self):
        self.request("PUT", WRITE, snapshot())
        invalid = [[], {}, dict(snapshot(), promo_code="secret"),
                   dict(snapshot(), observed_at_ms=True), dict(snapshot(), expiry_at_ms=-1),
                   dict(snapshot(), max_uses=3), dict(snapshot(), applied_uses=False),
                   dict(snapshot(), automatic_renewal=1), dict(snapshot(), schema_version=True),
                   dict(snapshot(), expiry_source=[]), dict(snapshot(), renewal_state={}),
                   dict(snapshot(), observed_at_ms=NOW + 300001),
                   dict(snapshot(), expiry_at_ms=1.5)]
        for value in invalid:
            with self.subTest(value=value):
                self.assertEqual(400, self.request("PUT", WRITE, value)[0])
        for raw in ('{"schema_version":1,"schema_version":1}', '{', '[' * 2000):
            self.assertEqual(400, self.request("PUT", WRITE, raw=raw)[0])
        self.assertEqual(413, self.request("PUT", WRITE, raw=" " * 4097)[0])
        self.assertEqual(415, self.request("PUT", WRITE, snapshot(), headers={"Content-Type": "text/plain"})[0])
        self.assertEqual(NOW, self.request()[1]["observed_at_ms"])

    def test_delayed_and_duplicate_updates(self):
        self.request("PUT", WRITE, snapshot())
        for observed in (NOW, NOW - 1):
            self.assertEqual(409, self.request("PUT", WRITE, dict(snapshot(), observed_at_ms=observed))[0])
        self.now += 1
        self.assertEqual(200, self.request("PUT", WRITE, dict(snapshot(), observed_at_ms=self.now))[0])

    def test_stale_expired_unknown_and_estimated(self):
        self.request("PUT", WRITE, snapshot())
        self.now += 899999
        self.assertFalse(self.request()[1]["stale"])
        self.now += 1
        self.assertTrue(self.request()[1]["stale"])
        self.now = NOW + 3600000
        result = self.request()[1]
        self.assertEqual(0, result["remaining_seconds"])
        self.assertTrue(result["renewal_confirmation_pending"])
        self.request("PUT", WRITE, dict(snapshot(), observed_at_ms=self.now,
                                       expiry_at_ms=None, expiry_source="unknown"))
        result = self.request()[1]
        self.assertIsNone(result["remaining_seconds"])
        self.assertFalse(result["renewal_confirmation_pending"])
        self.now += 1
        self.request("PUT", WRITE, dict(snapshot(), observed_at_ms=self.now,
                                       expiry_at_ms=self.now + 60000, expiry_source="estimated"))
        self.assertTrue(self.request()[1]["renewal_confirmation_pending"])

    def test_routes(self):
        self.assertEqual(404, self.request(path="/api/v1/status?token=secret")[0])
        self.assertEqual(501, self.request("POST")[0])


class TokenTests(unittest.TestCase):
    def test_corrupt_database_recovery_retains_backup(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "status.sqlite3"
            path.write_bytes(b"broken database")
            import sqlite3
            with self.assertRaises(sqlite3.DatabaseError):
                StatusStore(path)
            reset_database(path)
            self.assertIsNone(StatusStore(path).get())
            backups = list(Path(folder).glob("*.backup-*"))
            self.assertEqual(1, len(backups))
            self.assertEqual(b"broken database", backups[0].read_bytes())

    def test_ambiguous_framing(self):
        # OS HTTP filtering can reject ambiguous framing before loopback reaches
        # the handler. Exercise this boundary directly, without changing policy.
        for lengths in ([], ["1", "2"], ["-1"], ["1"]):
            handler = object.__new__(Handler)
            handler.server = Mock(write_token=WRITE.encode())
            handler.headers = Message()
            handler.headers["Authorization"] = "Bearer " + WRITE
            for length in lengths:
                handler.headers["Content-Length"] = length
            if lengths == ["1"]:
                handler.headers["Transfer-Encoding"] = "chunked"
            handler.path = "/api/v1/status"
            handler.reply = Mock()
            handler.do_PUT()
            self.assertEqual(400, handler.reply.call_args.args[0])

    def test_bad_configuration(self):
        for write, read in ((None, READ), (WRITE, WRITE), ("short", READ), ("あ" * 32, READ)):
            with self.assertRaises(ValueError):
                check_tokens(write, read)


class TlsTests(unittest.TestCase):
    def test_trusted_certificate_round_trip_and_untrusted_rejection(self):
        openssl = shutil.which("openssl")
        fallback = Path("C:/Program Files/Git/usr/bin/openssl.exe")
        if openssl is None and fallback.is_file():
            openssl = str(fallback)
        if openssl is None:
            self.skipTest("openssl is required to generate a temporary TLS certificate")
        with tempfile.TemporaryDirectory() as folder:
            cert = Path(folder) / "cert.pem"
            key = Path(folder) / "key.pem"
            subprocess.run([openssl, "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                            "-keyout", str(key), "-out", str(cert), "-days", "1",
                            "-subj", "/CN=localhost", "-addext", "subjectAltName=DNS:localhost"],
                           check=True, capture_output=True, timeout=30)
            server = RelayServer(("127.0.0.1", 0), StatusStore(Path(folder) / "state.sqlite3"),
                                 WRITE, READ, clock=lambda: NOW)
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.minimum_version = ssl.TLSVersion.TLSv1_2
            context.load_cert_chain(cert, key)
            server.socket = context.wrap_socket(server.socket, server_side=True,
                                                do_handshake_on_connect=False)
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            try:
                trusted = ssl.create_default_context(cafile=str(cert))
                for method, token, payload in (("PUT", WRITE, json.dumps(snapshot())),
                                               ("GET", READ, None)):
                    connection = http.client.HTTPSConnection("localhost", server.server_port,
                                                             context=trusted, timeout=5)
                    try:
                        connection.request(method, "/api/v1/status", payload,
                                           {"Authorization": "Bearer " + token,
                                            "Content-Type": "application/json"})
                        response = connection.getresponse()
                        self.assertEqual(200, response.status)
                        result = json.loads(response.read())
                        if method == "GET":
                            self.assertEqual(3600, result["remaining_seconds"])
                            self.assertEqual("server", result["expiry_source"])
                    finally:
                        connection.close()
                untrusted = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
                self.assertTrue(untrusted.check_hostname)
                self.assertEqual(ssl.CERT_REQUIRED, untrusted.verify_mode)
                connection = http.client.HTTPSConnection("localhost", server.server_port,
                                                         context=untrusted, timeout=5)
                try:
                    with self.assertRaises(ssl.SSLCertVerificationError):
                        connection.request("GET", "/api/v1/status")
                finally:
                    connection.close()
            finally:
                server.shutdown()
                server.server_close()
                thread.join()


if __name__ == "__main__":
    unittest.main()
