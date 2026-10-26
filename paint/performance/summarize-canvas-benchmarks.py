"""Compare three-pass Canvas measurements; no third-party dependencies."""
import json
import math
from pathlib import Path
from statistics import median

root = Path(__file__).resolve().parent
stages = {}
for stage in ("before", "candidate", "after"):
    path = root / f"canvas-{stage}.json"
    if path.exists():
        stages[stage] = json.loads(path.read_text(encoding="utf-8-sig"))


def cases(stage, method="rasterTimings"):
    runs = stages[stage]["runs"]
    assert len(runs) == 3, f"{stage}: expected three independent runs"
    return {
        name: median(run[method][name]["medianMs"] for run in runs)
        for name, value in runs[0][method].items()
        if "medianMs" in value
    }


before = cases("before")
candidate = cases("candidate")
assert stages["before"]["metadata"]["harnessSha256"] == stages["candidate"]["metadata"]["harnessSha256"]
families = stages["before"]["runs"][0]["rasterTimings"]
print("| Family | Canvas aggregate speedup | Worst case time change | Selected |")
print("|---|---:|---:|---|")
for family in ("quarterTurns", "flips", "resize", "affine"):
    names = [name for name in before if families[name]["family"] == family]
    ratios = [candidate[name] / before[name] for name in names]
    ratio = math.exp(sum(math.log(value) for value in ratios) / len(ratios))
    selected = "Canvas" if ratio <= 0.9 and max(ratios) <= 1.1 else "Kotlin"
    print(f"| {family} | {1 / ratio:.2f}x | {(max(ratios) - 1) * 100:+.1f}% | {selected} |")

after = cases("after") if "after" in stages else None
if after:
    assert stages["before"]["metadata"]["harnessSha256"] == stages["after"]["metadata"]["harnessSha256"]
print("\n| Case | Kotlin baseline ms | Canvas candidate ms | Final ms |")
print("|---|---:|---:|---:|")
for name in before:
    final = f"{after[name]:.2f}" if after else "-"
    print(f"| {name} | {before[name]:.2f} | {candidate[name]:.2f} | {final} |")

print("\n| Command | Baseline ms | Candidate ms | Final ms |")
print("|---|---:|---:|---:|")
commands = {stage: cases(stage, "livePreviewAndApplyTimings") for stage in stages}
for name in commands["before"]:
    final = f"{commands['after'][name]:.2f}" if after else "-"
    print(f"| {name} | {commands['before'][name]:.2f} | {commands['candidate'][name]:.2f} | {final} |")

print("\n| Stage | Image | Drag managed MB | P95 frame ms | PSS before/after MB | Native heap before/after MB |")
print("|---|---|---:|---:|---:|---:|")
for stage in stages:
    for size in ("1MP", "4MP"):
        values = [run["livePreviewAndApplyTimings"][f"{size} live"] for run in stages[stage]["runs"]]
        allocated = median(value["allocatedBytes"] for value in values) / 1e6
        p95 = median(value["frameP95Ms"] for value in values)
        pss = [median(value[key]["totalPssKb"] for value in values) / 1024 for key in ("memoryBefore", "memoryAfter")]
        native = [median(value[key]["nativeHeapBytes"] for value in values) / 1e6 for key in ("memoryBefore", "memoryAfter")]
        print(f"| {stage} | {size} | {allocated:.2f} | {p95:.2f} | {pss[0]:.1f}/{pss[1]:.1f} | {native[0]:.1f}/{native[1]:.1f} |")
