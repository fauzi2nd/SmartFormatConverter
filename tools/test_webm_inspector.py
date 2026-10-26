"""Regression coverage for the FFmpeg-independent EBML Opus inspection tool."""
import struct
import tempfile
import unittest
from pathlib import Path

from inspect_webm_opus import inspect, element_id, element_size


def el(tag: int, payload: bytes) -> bytes:
    if len(payload) >= 127:
        raise ValueError("Test fixture element is unexpectedly large")
    id_width = (tag.bit_length() + 7) // 8
    return tag.to_bytes(id_width, "big") + bytes([0x80 | len(payload)]) + payload


def sample(channels: int) -> bytes:
    opus = b"OpusHead" + bytes([1, channels, 0x38, 0x01]) + struct.pack("<I", 48000) + bytes([0, 0, 0])
    track = el(0xD7, b"\x01") + el(0x83, b"\x02") + el(0x86, b"A_OPUS")
    track += el(0x63A2, opus)
    audio = el(0x9F, bytes([channels])) + el(0xB5, struct.pack(">f", 48000.0))
    track += el(0xE1, audio)
    segment = el(0x1654AE6B, el(0xAE, track))
    return el(0x1A45DFA3, b"") + bytes.fromhex("18538067") + b"\xff" + segment


class WebmInspectorTest(unittest.TestCase):
    def check_sample(self, channels: int) -> dict:
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, "test.webm")
            path.write_bytes(sample(channels))
            return inspect(path)

    def test_ebml_element_id_vint_width(self):
        for raw, expected in [(bytes.fromhex("1a45dfa3"), 0x1A45DFA3),
                              (bytes.fromhex("1654ae6b"), 0x1654AE6B),
                              (bytes.fromhex("ae"), 0xAE),
                              (bytes.fromhex("63a2"), 0x63A2)]:
            self.assertEqual((expected, len(raw)), element_id(raw, 0))
        self.assertEqual((None, 1), element_size(b"\xff", 0))

    def test_valid_opus_stereo_in_actual_ebml_track(self):
        result = self.check_sample(2)
        self.assertTrue(result["container_header_valid"])
        self.assertEqual(1, result["opus_tracks"])
        track = result["tracks"][0]
        self.assertEqual(2, track["audio_element_channels"])
        self.assertEqual(2, track["opus_head_channels"])
        self.assertEqual(48000.0, track["audio_sample_rate"])
        self.assertEqual(19, track["codec_private_length"])

    def test_zero_channel_header_is_rejected(self):
        result = self.check_sample(0)
        self.assertFalse(result["container_header_valid"])
        self.assertTrue(result["tracks"][0]["issues"])


if __name__ == "__main__":
    unittest.main()
