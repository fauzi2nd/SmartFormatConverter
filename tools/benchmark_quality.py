#!/usr/bin/env python3
"""Offline, reproducible objective video-quality benchmark (no bundled FFmpeg).

Compare an original and converted file with SSIM, PSNR and optional VMAF.
Outputs JSON, enforces optional thresholds, and reports relative file growth.
Runs on a developer machine/CI worker with ffmpeg + ffprobe installed.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path


def command(args: list[str]) -> str:
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if result.returncode:
        raise RuntimeError(
            f"Command failed ({result.returncode}): {args[0]}\n"
            + result.stderr[-3000:]
        )
    return result.stdout + "\n" + result.stderr


def probe(path: Path) -> dict:
    try:
        text = command(["ffprobe", "-v", "error", "-select_streams", "v:0",
                        "-show_entries",
                        "stream=codec_name,width,height,avg_frame_rate:"
                        "format=duration,size",
                        "-of", "json", str(path)])
    except RuntimeError as error:
        message = str(error)
        if "Error parsing Ogg extradata" in message:
            # A malformed Opus header can abort ffprobe even when the VP9
            # video stream is decodable. Never silently claim the audio is OK.
            video_only = path.with_name(path.stem + "_video_only.mkv")
            raise RuntimeError(
                f"ffprobe failed while reading Opus audio extradata in: {path}\\n"
                "No PSNR/SSIM/VMAF was computed. Test audio separately; the "
                "media may have an incompatible or malformed Opus header.\\n"
                "To benchmark VIDEO ONLY (no re-encoding), run:\\n"
                f'  ffmpeg -i "{path}" -map 0:v:0 -c:v copy -an "{video_only}"\\n'
                "Then rerun this benchmark with the resulting MKV in place "
                "of the affected file. Check ffmpeg's exit status and output "
                "before using the MKV. Keep the original file for diagnosis.\\n"
                f"Original ffprobe error:\\n{message}"
            ) from error
        raise RuntimeError(f"ffprobe could not inspect {path}: {message}") from error
    result = json.loads(text)
    video = (result.get("streams") or [{}])[0]
    if not video.get("width") or not video.get("height"):
        raise ValueError(f"No readable video in {path}")
    return {"codec": video.get("codec_name"),
            "width": int(video["width"]), "height": int(video["height"]),
            "duration": float(result["format"].get("duration") or 0),
            "size": path.stat().st_size}


def filter_metric(source: Path, output: Path, metric: str,
                  width: int, height: int, fps: int, seconds: float) -> float | None:
    # Normalize frame timestamps/dimensions; SSIM and PSNR need aligned frames.
    setup = (
        f"[0:v]fps={fps},scale={width}:{height}:flags=lanczos,"
        "setsar=1,setpts=PTS-STARTPTS[ref];"
        f"[1:v]fps={fps},scale={width}:{height}:flags=lanczos,"
        "setsar=1,setpts=PTS-STARTPTS[dist];"
    )
    filter_name = "ssim" if metric == "ssim" else "psnr"
    logs = command(["ffmpeg", "-hide_banner", "-nostdin", "-v", "info",
                    "-t", str(seconds), "-i", str(source), "-t", str(seconds),
                    "-i", str(output), "-filter_complex",
                    setup + f"[dist][ref]{filter_name}",
                    "-an", "-f", "null", "-"])
    if metric == "ssim":
        matches = re.findall(r"All:([0-9]+(?:\.[0-9]+)?)", logs)
    else:
        matches = re.findall(r"average:([0-9]+(?:\.[0-9]+)?)", logs)
    return float(matches[-1]) if matches else None


def vmaf_metric(source: Path, output: Path, width: int, height: int,
                fps: int, seconds: float, log_file: Path) -> float | None:
    filters = command(["ffmpeg", "-hide_banner", "-filters"])
    if "libvmaf" not in filters:
        return None
    setup = (
        f"[0:v]fps={fps},scale={width}:{height}:flags=lanczos,"
        "setsar=1,setpts=PTS-STARTPTS[ref];"
        f"[1:v]fps={fps},scale={width}:{height}:flags=lanczos,"
        "setsar=1,setpts=PTS-STARTPTS[dist];"
    )
    escape_path = str(log_file.resolve()).replace("\\", "/").replace(":", "\\:")
    command(["ffmpeg", "-hide_banner", "-nostdin", "-v", "error",
             "-t", str(seconds), "-i", str(source), "-t", str(seconds),
             "-i", str(output), "-filter_complex",
             setup + f"[dist][ref]libvmaf=log_fmt=json:log_path={escape_path}",
             "-an", "-f", "null", "-"])
    data = json.loads(log_file.read_text(encoding="utf-8"))
    value = data.get("pooled_metrics", {}).get("vmaf", {}).get("mean")
    return float(value) if value is not None else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("converted", type=Path)
    parser.add_argument("--seconds", type=float, default=30)
    parser.add_argument("--fps", type=int, default=30)
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--min-ssim", type=float)
    parser.add_argument("--min-psnr", type=float)
    parser.add_argument("--min-vmaf", type=float)
    parser.add_argument("--max-size-ratio", type=float)
    parser.add_argument("--vmaf", action="store_true",
                        help="Compute VMAF when ffmpeg has libvmaf")
    args = parser.parse_args()
    if not args.source.is_file() or not args.converted.is_file():
        parser.error("Source and converted video files must exist")
    if args.seconds <= 0 or not 1 <= args.fps <= 120:
        parser.error("seconds must be positive; fps must be 1..120")
    for binary in ("ffmpeg", "ffprobe"):
        if not shutil.which(binary):
            parser.error(f"Install {binary} to run offline benchmarks")

    ref = probe(args.source)
    dist = probe(args.converted)
    duration = min(args.seconds, ref["duration"], dist["duration"])
    if duration <= 0:
        parser.error("Could not obtain a positive shared video duration")
    score = {
        "source": str(args.source),
        "converted": str(args.converted),
        "source_info": ref, "converted_info": dist,
        "seconds_analyzed": duration, "comparison_fps": args.fps,
        "size_ratio": round(dist["size"] / ref["size"], 4),
        "ssim": filter_metric(args.source, args.converted, "ssim",
                              ref["width"], ref["height"], args.fps, duration),
        "psnr_db": filter_metric(args.source, args.converted, "psnr",
                                 ref["width"], ref["height"], args.fps, duration),
        "vmaf": None
    }
    if args.vmaf or args.min_vmaf is not None:
        import tempfile
        with tempfile.TemporaryDirectory(prefix="sfc-vmaf-") as directory:
            score["vmaf"] = vmaf_metric(
                args.source, args.converted, ref["width"], ref["height"],
                args.fps, duration, Path(directory) / "vmaf.json")
    gates = [
        ("ssim", args.min_ssim, score["ssim"]),
        ("psnr_db", args.min_psnr, score["psnr_db"]),
        ("vmaf", args.min_vmaf, score["vmaf"])
    ]
    problems = [
        f"{name} below minimum {limit} (actual {value})"
        for name, limit, value in gates
        if limit is not None and (value is None or value < limit)
    ]
    if args.max_size_ratio is not None and score["size_ratio"] > args.max_size_ratio:
        problems.append(f"size ratio above {args.max_size_ratio}: {score['size_ratio']}")
    score["passed"] = not problems
    score["issues"] = problems
    report = json.dumps(score, indent=2, ensure_ascii=False)
    print(report)
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(report + "\n", encoding="utf-8")
    return 1 if problems else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, ValueError, OSError, KeyError) as error:
        print(f"Quality benchmark error: {error}", file=sys.stderr)
        sys.exit(2)
