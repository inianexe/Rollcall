#!/usr/bin/env python3
"""Summarize app-layer frame and scheduling data from Rollcall Macrobenchmark traces."""

import argparse
import collections
import glob
import json
import os
import statistics
from perfetto.trace_processor import TraceProcessor


def percentile(values, p):
    if not values:
        return None
    ordered = sorted(values)
    index = (len(ordered) - 1) * p
    low = int(index)
    high = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[high] - ordered[low]) * (index - low)


def rows(processor, sql):
    return [row.__dict__ for row in processor.query(sql)]


def analyze(path, package, activity):
    processor = TraceProcessor(trace=path)
    try:
        frames = rows(processor, f"""
            SELECT dur / 1e6 AS duration_ms, jank_type, layer_name
            FROM actual_frame_timeline_slice
            WHERE dur > 0 AND layer_name LIKE '%{activity}%'
        """)
        durations = [r["duration_ms"] for r in frames]
        jank = collections.Counter(r["jank_type"] or "Unknown" for r in frames)
        cpu = rows(processor, """
            SELECT thread.name AS thread, SUM(sched_slice.dur) / 1e9 AS cpu_seconds
            FROM sched_slice
            JOIN thread USING (utid)
            JOIN process USING (upid)
            WHERE process.name = '{package}'
            GROUP BY thread.name ORDER BY SUM(sched_slice.dur) DESC
        """)
        slices = rows(processor, """
            SELECT slice.name, COUNT(*) AS count, SUM(slice.dur) / 1e9 AS total_seconds
            FROM slice
            JOIN thread_track ON slice.track_id = thread_track.id
            JOIN thread USING (utid)
            JOIN process USING (upid)
            WHERE process.name = '{package}'
              AND (slice.name LIKE '%measureAndLayout%' OR slice.name LIKE '%recompos%'
                   OR slice.name LIKE '%GC%')
            GROUP BY slice.name ORDER BY SUM(slice.dur) DESC
        """)
        return {
            "trace": os.path.basename(path),
            "frames": len(durations),
            "frameDurationMs": {f"p{p}": percentile(durations, p / 100) for p in (50, 90, 95, 99)},
            "slowFrameCounts": {
                "over16_67ms": sum(d > 16.67 for d in durations),
                "over33_33ms": sum(d > 33.33 for d in durations),
                "over700ms": sum(d > 700 for d in durations),
            },
            "frameTimelineClassifications": dict(jank),
            "appCpuSecondsByThread": cpu,
            "appSlices": slices,
        }
    finally:
        processor.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("trace_directory")
    parser.add_argument("--output")
    parser.add_argument("--package", default="com.iniexe.rollcall")
    parser.add_argument("--activity", default="CoursesBenchmarkActivity")
    args = parser.parse_args()
    paths = [args.trace_directory] if os.path.isfile(args.trace_directory) else sorted(
        glob.glob(os.path.join(args.trace_directory, "**", "*Benchmark_*_iter*.perfetto-trace"), recursive=True)
    )
    if not paths:
        raise SystemExit("No CoursesBenchmark Perfetto traces found")
    report = [analyze(path, args.package, args.activity) for path in paths]
    payload = json.dumps(report, indent=2)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(payload + "\n")
    print(payload)


if __name__ == "__main__":
    main()
