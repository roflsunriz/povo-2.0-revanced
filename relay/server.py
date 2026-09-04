"""Bounded HTTP service, intended for a private PC or a TLS reverse proxy."""

import argparse
import hmac
import json
import os
import re
import sqlite3
import ssl
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .model import public_status, validate
from .storage import StatusStore, reset_database

MAX_BODY = 4096


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate_field")
        result[key] = value
    return result


def check_tokens(write_token, read_token):
    for token in (write_token, read_token):
        if not isinstance(token, str) or not re.fullmatch(r"[A-Za-z0-9_-]{32,256}", token):
            raise ValueError("tokens_must_be_32_to_256_urlsafe_characters")
    if hmac.compare_digest(write_token, read_token):
        raise ValueError("read_and_write_tokens_must_differ")


class RelayServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 16

    def __init__(self, address, store, write_token, read_token, clock=None):
        check_tokens(write_token, read_token)
        self.store = store
        self.write_token = write_token.encode("ascii")
        self.read_token = read_token.encode("ascii")
        self.clock = clock or (lambda: time.time_ns() // 1000000)
        self.slots = threading.BoundedSemaphore(16)
        super().__init__(address, Handler)

    def get_request(self):
        connection, address = super().get_request()
        connection.settimeout(10)
        return connection, address

    def process_request(self, request, client_address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.slots.release()

    def handle_error(self, request, client_address):
        print("relay: request failed", file=sys.stderr)


class Handler(BaseHTTPRequestHandler):
    server_version = "PovoRelay/1"
    sys_version = ""

    def log_message(self, format, *args):
        # Never log request URLs, headers, peer addresses or payloads.
        pass

    def reply(self, status, value):
        body = json.dumps(value, separators=(",", ":"), allow_nan=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Connection", "close")
        if status == 401:
            self.send_header("WWW-Authenticate", "Bearer")
        self.end_headers()
        self.close_connection = True
        self.wfile.write(body)

    def send_error(self, code, message=None, explain=None):
        self.reply(code, {"error": "invalid_request"})

    def authorized(self, expected):
        headers = self.headers.get_all("Authorization", [])
        actual = headers[0].encode("utf-8") if len(headers) == 1 else b""
        if not hmac.compare_digest(actual, b"Bearer " + expected):
            self.reply(401, {"error": "unauthorized"})
            return False
        return True

    def route(self):
        if self.path != "/api/v1/status":
            self.reply(404, {"error": "not_found"})
            return False
        return True

    def do_GET(self):
        if not self.route() or not self.authorized(self.server.read_token):
            return
        try:
            saved = self.server.store.get()
            if saved is None:
                self.reply(503, {"error": "status_unavailable"})
                return
            self.reply(200, public_status(*saved, self.server.clock()))
        except (sqlite3.Error, ValueError):
            self.reply(503, {"error": "storage_unavailable"})

    def do_PUT(self):
        if not self.route() or not self.authorized(self.server.write_token):
            return
        lengths = self.headers.get_all("Content-Length", [])
        if self.headers.get("Transfer-Encoding") is not None or len(lengths) != 1:
            self.reply(400, {"error": "content_length_required"})
            return
        if not re.fullmatch(r"[0-9]{1,10}", lengths[0]):
            self.reply(400, {"error": "invalid_content_length"})
            return
        length = int(lengths[0])
        if length > MAX_BODY:
            self.reply(413, {"error": "payload_too_large"})
            return
        if self.headers.get_content_type() != "application/json":
            self.reply(415, {"error": "application_json_required"})
            return
        try:
            raw = self.rfile.read(length)
            if len(raw) != length:
                raise ValueError("incomplete_body")
            value = validate(json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object))
            now = self.server.clock()
            if value["observed_at_ms"] > now + 300000:
                raise ValueError("snapshot_in_future")
        except (ValueError, UnicodeError, RecursionError):
            self.reply(400, {"error": "invalid_status"})
            return
        except TimeoutError:
            self.reply(408, {"error": "request_timeout"})
            return
        try:
            if not self.server.store.put(value, now):
                self.reply(409, {"error": "outdated_snapshot"})
                return
        except sqlite3.Error:
            self.reply(503, {"error": "storage_unavailable"})
            return
        self.reply(200, {"accepted": True, "received_at_ms": now})


def main():
    parser = argparse.ArgumentParser(description="povo CYD 中継 API")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--database", default="povo-status.sqlite3")
    parser.add_argument("--tls-cert")
    parser.add_argument("--tls-key")
    parser.add_argument("--reset-database", action="store_true",
                        help="停止中の DB をバックアップ名へ退避して初期化")
    args = parser.parse_args()
    if bool(args.tls_cert) != bool(args.tls_key):
        parser.error("--tls-cert と --tls-key は両方指定してください")
    try:
        write_token, read_token = os.environ.get("POVO_WRITE_TOKEN"), os.environ.get("POVO_READ_TOKEN")
        check_tokens(write_token, read_token)
        if args.reset_database:
            reset_database(args.database)
        store = StatusStore(args.database)
        with RelayServer((args.host, args.port), store, write_token, read_token) as server:
            if args.tls_cert:
                context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                context.minimum_version = ssl.TLSVersion.TLSv1_2
                context.load_cert_chain(args.tls_cert, args.tls_key)
                # Handshake happens in the bounded worker with a socket timeout.
                server.socket = context.wrap_socket(server.socket, server_side=True,
                                                    do_handshake_on_connect=False)
            print("中継 API を開始しました。終了: Ctrl+C", flush=True)
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass
    except (ValueError, OSError, sqlite3.Error):
        print("起動失敗: トークン設定、証明書、ポート、DBを確認してください。"
              "DB破損時の復旧は relay/README.md を参照してください。", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
