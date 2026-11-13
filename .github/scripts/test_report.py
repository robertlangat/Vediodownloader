#!/usr/bin/env python3
"""Summarise JUnit XML results (unit + emulator) into a Markdown report."""
import glob
import sys
import xml.etree.ElementTree as ET

out = sys.argv[1] if len(sys.argv) > 1 else "test-report.md"
sections = [
    ("Android unit tests (JVM)", "app/build/test-results/testDebugUnitTest/*.xml"),
    ("Android emulator tests (instrumented)", "app/build/outputs/androidTest-results/connected/**/*.xml"),
]
lines = ["# Social Video Downloader – automated test report", ""]
total_fail = 0
for title, pattern in sections:
    files = glob.glob(pattern, recursive=True)
    lines.append(f"## {title}")
    if not files:
        lines += ["", "_No results found (step did not run or failed before tests)._", ""]
        total_fail += 1
        continue
    lines += ["", "| Test | Result | Time (s) |", "|---|---|---|"]
    for f in sorted(files):
        root = ET.parse(f).getroot()
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for suite in suites:
            device = ""
            for p in suite.iter("property"):
                if p.get("name") == "device":
                    device = p.get("value")
            for case in suite.findall("testcase"):
                name = case.get("name")
                t = case.get("time", "")
                fail = case.find("failure")
                if fail is None:
                    fail = case.find("error")
                skipped = case.find("skipped") is not None
                if fail is not None:
                    total_fail += 1
                    msg = (fail.get("message") or fail.text or "").strip().splitlines()
                    res = "FAIL: " + (msg[0][:160] if msg else "")
                elif skipped:
                    res = "skipped"
                else:
                    res = "PASS"
                lines.append(f"| `{name}` | {res} | {t} |")
            if device:
                lines.append(f"\n_Device: {device}_")
    lines.append("")

backend = "backend-tests.txt"
try:
    with open(backend) as fh:
        txt = fh.read()
    lines += ["## Backend tests (Node)", "", "```", "\n".join(l for l in txt.splitlines() if l.startswith(("ok ", "not ok", "# pass", "# fail"))), "```", ""]
    if "not ok" in txt:
        total_fail += 1
except FileNotFoundError:
    pass

try:
    with open("live-checks.txt") as fh:
        lines += ["## Live platform checks (real network, no credentials)", "", "```", fh.read().strip(), "```", ""]
except FileNotFoundError:
    pass

lines.append(f"**Overall: {'ALL PASSED' if total_fail == 0 else str(total_fail) + ' problem(s)'}**")
with open(out, "w") as fh:
    fh.write("\n".join(lines) + "\n")
print("\n".join(lines))
