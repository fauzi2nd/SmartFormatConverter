#!/usr/bin/env python3
"""Inspect actual EBML TrackEntry / CodecPrivate bytes in a WebM file.

Pure Python, no ffprobe needed. Designed for diagnosing WebM files that
FFmpeg refuses to probe, especially Opus channel-count/extradata errors.
"""
from __future__ import annotations

import argparse
import json
import struct
import sys
from pathlib import Path

EBML = 0x1A45DFA3
SEGMENT = 0x18538067
TRACKS = 0x1654AE6B
TRACK_ENTRY = 0xAE
AUDIO = 0xE1
TRACK_NUMBER = 0xD7
TRACK_TYPE = 0x83
CODEC_ID = 0x86
CODEC_PRIVATE = 0x63A2
AUDIO_CHANNELS = 0x9F
SAMPLING_FREQUENCY = 0xB5
MAX_READ = 8 * 1024 * 1024


class EbmlError(ValueError):
    pass


def element_id(data: bytes, offset: int) -> tuple[int, int]:
    if offset >= len(data):
        raise EbmlError("Unexpected end of file while reading EBML element ID")
    lead = data[offset]
    if lead == 0:
        raise EbmlError(f"Zero/invalid EBML ID byte at offset {offset}")
    length = next((n for n in range(1, 5) if lead & (0x80 >> (n - 1))), None)
    if length is None or offset + length > len(data):
        raise EbmlError(f"Invalid/truncated EBML ID at offset {offset}")
    return int.from_bytes(data[offset:offset+length], "big"), length


def element_size(data: bytes, offset: int) -> tuple[int | None, int]:
    if offset >= len(data):
        raise EbmlError("Unexpected end of file while reading EBML size")
    lead = data[offset]
    if lead == 0:
        raise EbmlError(f"Invalid EBML size at offset {offset}")
    length = next((n for n in range(1, 9) if lead & (0x80 >> (n - 1))), None)
    if length is None or offset + length > len(data):
        raise EbmlError(f"Invalid/truncated EBML size at offset {offset}")
    val = int.from_bytes(data[offset:offset+length], "big")
    val &= (1 << (7 * length)) - 1
    if val == (1 << (7 * length)) - 1:
        return None, length
    return val, length


def children(data: bytes, start: int, end: int):
    at = start
    end = min(end, len(data))
    while at < end:
        code, id_len = element_id(data, at)
        size, size_len = element_size(data, at + id_len)
        payload = at + id_len + size_len
        if payload > end:
            raise EbmlError(f"Element 0x{code:X} header crosses parent boundary")
        to = end if size is None else payload + size
        if size is not None and to > end:
            # Partial reads are OK only when the file's leading window ended.
            raise EbmlError(f"Element 0x{code:X} length goes beyond readable boundary")
        yield code, payload, to
        at = to


def unsigned(data: bytes) -> int:
    return int.from_bytes(data, "big")


def inspect(path: Path) -> dict:
    file_size = path.stat().st_size
    with path.open("rb") as file:
        data = file.read(MAX_READ)
    if len(data) < 4 or data[:4] != bytes.fromhex("1a45dfa3"):
        raise EbmlError("File does not begin with the EBML/WebM signature")
    segment_range = None
    for code, start, end in children(data, 0, len(data)):
        if code == SEGMENT:
            segment_range = (start, end)
            break
    if segment_range is None:
        raise EbmlError("No EBML Segment was found in the inspected prefix")
    track_entries = []
    found_tracks = False
    # Skip clusters entirely. The track metadata is normally near the beginning.
    for code, first, last in children(data, *segment_range):
        if code != TRACKS:
            if code == 0x1F43B675 and not found_tracks:
                raise EbmlError("Cluster appears before Tracks; increase header scan")
            continue
        found_tracks = True
        for k, entry_start, entry_end in children(data, first, last):
            if k != TRACK_ENTRY:
                continue
            info = {"track_number": None, "track_type": None, "codec_id": None,
                    "codec_private_length": 0, "audio_element_channels": None,
                    "audio_sample_rate": None, "opus_head_channels": None,
                    "opus_head_version": None, "opus_mapping_family": None,
                    "opus_head_prefix_hex": None, "issues": []}
            for tag, a, b in children(data, entry_start, entry_end):
                payload = data[a:b]
                if tag == TRACK_NUMBER:
                    info["track_number"] = unsigned(payload)
                elif tag == TRACK_TYPE:
                    info["track_type"] = unsigned(payload)
                elif tag == CODEC_ID:
                    info["codec_id"] = payload.decode("ascii", "replace")
                elif tag == CODEC_PRIVATE:
                    info["codec_private_length"] = len(payload)
                    info["opus_head_prefix_hex"] = payload[:32].hex(" ")
                    if payload.startswith(b"OpusHead"):
                        if len(payload) >= 19:
                            info["opus_head_version"] = payload[8]
                            info["opus_head_channels"] = payload[9]
                            info["opus_mapping_family"] = payload[18]
                        else:
                            info["issues"].append("Truncated OpusHead (fewer than 19 bytes)")
                    elif payload.startswith(b"AOPUSHDR"):
                        info["issues"].append("Android AOPUSHDR envelope leaked into CodecPrivate")
                elif tag == AUDIO:
                    for subtag, sa, sb in children(data, a, b):
                        raw = data[sa:sb]
                        if subtag == AUDIO_CHANNELS:
                            info["audio_element_channels"] = unsigned(raw)
                        elif subtag == SAMPLING_FREQUENCY and len(raw) in (4, 8):
                            info["audio_sample_rate"] = struct.unpack(
                                ">f" if len(raw) == 4 else ">d", raw)[0]
            if info["codec_id"] == "A_OPUS":
                if info["opus_head_channels"] is None:
                    info["issues"].append("Missing/unrecognized OpusHead in CodecPrivate")
                elif info["opus_head_channels"] not in (1, 2):
                    info["issues"].append("OpusHead channel count is not supported mono/stereo")
                if info["audio_element_channels"] not in (1, 2):
                    info["issues"].append("WebM Audio/Channels is missing or invalid")
                if (info["opus_head_channels"] in (1, 2) and
                        info["audio_element_channels"] in (1, 2) and
                        info["opus_head_channels"] != info["audio_element_channels"]):
                    info["issues"].append("OpusHead channel count disagrees with WebM Audio/Channels")
                if info["opus_mapping_family"] == 0 and info["codec_private_length"] != 19:
                    info["issues"].append("Mapping family 0 requires exactly 19-byte OpusHead")
            track_entries.append(info)
        break
    if not found_tracks:
        raise EbmlError("No Tracks element in the first 8 MiB")
    return {
        "file": str(path), "size_bytes": file_size,
        "tracks": track_entries,
        "opus_tracks": sum(t["codec_id"] == "A_OPUS" for t in track_entries),
        "container_header_valid": len(track_entries) > 0 and not any(
            t["issues"] for t in track_entries)
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("webm", type=Path)
    ap.add_argument("--json-out", type=Path)
    args = ap.parse_args()
    if not args.webm.is_file():
        ap.error(f"File not found: {args.webm}")
    try:
        result = inspect(args.webm)
    except (OSError, EbmlError, ValueError, struct.error) as error:
        print(f"WebM inspection failed: {error}", file=sys.stderr)
        return 2
    report = json.dumps(result, indent=2)
    print(report)
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(report + "\n", encoding="utf-8")
    return 0 if result["container_header_valid"] else 1


if __name__ == "__main__":
    sys.exit(main())
