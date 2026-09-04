"""One account per database; transactionally reject delayed snapshots."""

import json
import sqlite3
from contextlib import contextmanager
from pathlib import Path
import uuid

from .model import validate


class StatusStore:
    def __init__(self, path):
        self.path = str(path)
        with self.connect() as db:
            version = db.execute("PRAGMA user_version").fetchone()[0]
            if version not in (0, 1):
                raise ValueError("unsupported_database_version")
            db.execute("CREATE TABLE IF NOT EXISTS status "
                       "(id INTEGER PRIMARY KEY CHECK (id=1), payload TEXT NOT NULL, "
                       "observed INTEGER NOT NULL, received INTEGER NOT NULL)")
            db.execute("PRAGMA user_version=1")
            row = db.execute("SELECT payload, received, observed FROM status WHERE id=1").fetchone()
            if row:
                value = validate(json.loads(row[0]))
                if type(row[1]) is not int or row[1] <= 0:
                    raise ValueError("invalid_database_timestamp")
                if row[2] != value["observed_at_ms"]:
                    raise ValueError("invalid_database_observed")

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=5)
        try:
            with db:
                yield db
        finally:
            db.close()

    def put(self, value, received):
        with self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT observed FROM status WHERE id=1").fetchone()
            if row and value["observed_at_ms"] <= row[0]:
                return False
            db.execute("INSERT OR REPLACE INTO status VALUES (1, ?, ?, ?)",
                       (json.dumps(value, separators=(",", ":")), value["observed_at_ms"], received))
            return True

    def get(self):
        with self.connect() as db:
            row = db.execute("SELECT payload, received FROM status WHERE id=1").fetchone()
        return None if row is None else (validate(json.loads(row[0])), row[1])


def reset_database(path):
    """Explicit operator recovery; retain the complete old DB for inspection."""
    source = Path(path)
    if source.exists():
        suffix = ".backup-" + uuid.uuid4().hex
        # A stopped process may leave a rollback journal after a crash.
        # Keep sidecars with the backup, never beside the replacement database.
        for ending in ("-journal", "-wal", "-shm", ""):
            item = source.with_name(source.name + ending)
            if item.exists():
                item.rename(source.with_name(source.name + suffix + ending))
