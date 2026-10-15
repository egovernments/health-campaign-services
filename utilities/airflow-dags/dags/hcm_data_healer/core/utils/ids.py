"""
Compact id keys to reduce memory used by reconciliation id sets.

UUID strings are stored as 16 raw bytes. pack() is applied to both the DB and
ES sides, and only when the round-trip is lossless; other ids stay as strings.
"""

import uuid


def pack(s):
    """UUID string -> 16 raw bytes if reversible, else the string unchanged."""
    s = str(s)
    try:
        b = uuid.UUID(s).bytes
        if str(uuid.UUID(bytes=b)) == s:
            return b
    except (ValueError, AttributeError, TypeError):
        pass
    return s


def unpack(k):
    """Restore the original clientReferenceId string from a packed key."""
    if isinstance(k, (bytes, bytearray)):
        return str(uuid.UUID(bytes=bytes(k)))
    return k
