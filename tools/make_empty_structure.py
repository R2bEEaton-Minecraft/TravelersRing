"""Writes the empty GameTest structure template (dev only; used by src/gametest)."""
import gzip
import struct
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/gametest/resources/data/travelersring/structures/empty.nbt"


def s(t):
    b = t.encode()
    return struct.pack(">H", len(b)) + b


def named(tid, name, payload):
    return bytes([tid]) + s(name) + payload


SIZE = (9, 8, 9)
body = b""
body += named(3, "DataVersion", struct.pack(">i", 3465))
body += named(9, "size", bytes([3]) + struct.pack(">i", 3) + b"".join(struct.pack(">i", v) for v in SIZE))
body += named(9, "entities", bytes([10]) + struct.pack(">i", 0))
body += named(9, "blocks", bytes([10]) + struct.pack(">i", 0))
palette_entry = named(8, "Name", s("minecraft:air")) + b"\x00"
body += named(9, "palette", bytes([10]) + struct.pack(">i", 1) + palette_entry)
body += b"\x00"
data = bytes([10]) + s("") + body
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(gzip.compress(data))
print("wrote", OUT, len(data))
