"""Versioned, deliberately small public status contract (no promo codes)."""

FIELDS = {
    "schema_version", "observed_at_ms", "expiry_at_ms", "expiry_source",
    "expiry_observed_at_ms", "code_deadline_at_ms", "automatic_renewal",
    "applied_uses", "max_uses", "renewal_state", "last_applied_at_ms",
}
MAX_TIMESTAMP = 253402300799999  # Last millisecond of year 9999.


def validate(value):
    if not isinstance(value, dict) or set(value) != FIELDS:
        raise ValueError("invalid_fields")
    if type(value["schema_version"]) is not int or value["schema_version"] != 1:
        raise ValueError("unsupported_schema")
    for field in ("observed_at_ms", "expiry_at_ms", "expiry_observed_at_ms",
                  "code_deadline_at_ms", "last_applied_at_ms"):
        item = value[field]
        if item is None and field != "observed_at_ms":
            continue
        if type(item) is not int or not 0 < item <= MAX_TIMESTAMP:
            raise ValueError("invalid_timestamp")
    if value["expiry_source"] not in ("unknown", "manual", "estimated", "server"):
        raise ValueError("invalid_expiry_source")
    if type(value["automatic_renewal"]) is not bool:
        raise ValueError("invalid_automatic_renewal")
    for field in ("applied_uses", "max_uses"):
        if type(value[field]) is not int or not 0 <= value[field] <= 2147483647:
            raise ValueError("invalid_uses")
    if value["applied_uses"] > value["max_uses"]:
        raise ValueError("invalid_uses")
    if value["renewal_state"] not in (
        "unknown", "idle", "applying", "retrying", "auth_required",
        "needs_review", "completed", "code_expired",
    ):
        raise ValueError("invalid_renewal_state")
    return value


def public_status(value, received_at_ms, now_ms):
    expiry = value["expiry_at_ms"]
    return dict(value, received_at_ms=received_at_ms, server_time_ms=now_ms,
                stale=now_ms - received_at_ms >= 900000,
                remaining_seconds=None if expiry is None else max(0, (expiry - now_ms) // 1000),
                renewal_confirmation_pending=(value["expiry_source"] == "estimated"
                                              or (expiry is not None and expiry <= now_ms)))
