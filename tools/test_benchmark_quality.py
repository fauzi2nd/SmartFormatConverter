"""Regression tests for Windows libvmaf filter paths and display-rotation safety."""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from benchmark_quality import probe, vmaf_metric


class BenchmarkQualityTest(unittest.TestCase):
    def test_vmaf_writes_relative_log_path_from_isolated_cwd(self):
        with tempfile.TemporaryDirectory(prefix="vmaf path space ") as directory:
            temp = Path(directory)
            source = temp / "input.mp4"
            converted = temp / "result.webm"
            source.write_bytes(b"a")
            converted.write_bytes(b"b")
            log_file = temp / "vmaf.json"
            seen = []

            def fake_command(args, *, cwd=None):
                seen.append((args, cwd))
                if "-filters" in args:
                    return " ... libvmaf ..."
                graph = args[args.index("-filter_complex") + 1]
                self.assertIn("libvmaf=log_fmt=json:log_path=vmaf.json", graph)
                self.assertNotIn("C:", graph)
                self.assertEqual(cwd, temp)
                self.assertEqual(args[args.index("-i") + 1], str(source.resolve()))
                log_file.write_text(json.dumps({
                    "pooled_metrics": {"vmaf": {"mean": 93.12}}
                }), encoding="utf-8")
                return ""

            with patch("benchmark_quality.command", side_effect=fake_command):
                score = vmaf_metric(source, converted, 320, 240, 30, 1, log_file)
            self.assertEqual(93.12, score)
            self.assertEqual(2, len(seen))

    def test_display_rotation_swaps_source_dimensions(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.mp4"
            source.write_bytes(b"a")
            response = json.dumps({
                "streams": [{
                    "codec_name": "hevc", "width": 1080, "height": 1440,
                    "side_data_list": [{"rotation": -90}]
                }],
                "format": {"duration": "10.2"}
            })
            with patch("benchmark_quality.command", return_value=response):
                result = probe(source)
            self.assertEqual((1440, 1080),
                             (result["display_width"], result["display_height"]))
            self.assertEqual(270, result["rotation_degrees"])

    def test_absent_rotation_preserves_coded_dimensions(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.mp4"
            source.write_bytes(b"a")
            response = json.dumps({
                "streams": [{"codec_name": "hevc", "width": 1080, "height": 1440}],
                "format": {"duration": "10.2"}
            })
            with patch("benchmark_quality.command", return_value=response):
                result = probe(source)
            self.assertEqual((1080, 1440),
                             (result["display_width"], result["display_height"]))
            self.assertEqual(0, result["rotation_degrees"])


if __name__ == "__main__":
    unittest.main()
