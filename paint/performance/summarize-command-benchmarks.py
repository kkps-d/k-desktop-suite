"""Print complete-command comparisons from the three saved renderer stages."""
import json
import math
from pathlib import Path
from statistics import median

root = Path(__file__).resolve().parent
reports = {
    stage: json.loads((root / f"commands-{stage}.json").read_text(encoding="utf-8-sig"))
    for stage in ("before", "candidate", "after")
}
hashes = {report["metadata"]["harnessSha256"] for report in reports.values()}
assert len(hashes) == 1, "All stages must use the same command harness"
assert all(len(report["runs"]) == 3 for report in reports.values()), "Expected three runs per stage"

def timings(report):
    runs = report["runs"]
    return {
        name: median(run["commandTimings"][name]["medianMs"] for run in runs)
        for name in runs[0]["commandTimings"]
        if name != "environment"
    }

times = {stage: timings(report) for stage, report in reports.items()}
print("| Target | Canvas aggregate speedup over Kotlin | Worst case time change |")
print("|---|---:|---:|")
for target in ("canvas", "selection", "floatingAlpha", "clipped", "composed33", "small64"):
    names = [name for name in times["before"] if f" {target} " in name]
    ratios = [times["candidate"][name] / times["before"][name] for name in names]
    ratio = math.exp(sum(math.log(value) for value in ratios) / len(ratios))
    print(f"| {target} | {1 / ratio:.2f}x | {(max(ratios) - 1) * 100:+.1f}% |")

print()
print("| Command case | Kotlin ms | Canvas ms | Production ms |")
print("|---|---:|---:|---:|")
for name in times["before"]:
    print(f"| {name} | {times['before'][name]:.2f} | {times['candidate'][name]:.2f} | {times['after'][name]:.2f} |")

print("\n| Stage | 4MP case | Cumulative managed MB | PSS before/after MB | Native heap before/after MB |")
print("|---|---|---:|---:|---:|")
for stage, report in reports.items():
    for case in ("canvas rotate90", "selection rotate90", "floatingAlpha flipH", "small64 flipV", "composed33 rotate90"):
        values = [run["commandTimings"][f"4MP {case}"] for run in report["runs"]]
        allocated = median(value["allocatedBytes"] for value in values) / 1e6
        pss = [median(value[key]["totalPssKb"] for value in values) / 1024 for key in ("memoryBefore", "memoryAfter")]
        native = [median(value[key]["nativeHeapBytes"] for value in values) / 1e6 for key in ("memoryBefore", "memoryAfter")]
        print(f"| {stage} | {case} | {allocated:.1f} | {pss[0]:.1f}/{pss[1]:.1f} | {native[0]:.1f}/{native[1]:.1f} |")
