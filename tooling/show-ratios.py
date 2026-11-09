#!/usr/bin/env python3
"""Print derived ratios for target files from an audit TSV."""
import csv
import sys

tsv_path = sys.argv[1]
names = set(sys.argv[2:])
rows = list(csv.reader(open(tsv_path, encoding="utf-8"), delimiter="\t"))
header = rows[0]
for r in rows[1:]:
    if r[1] != "src":
        continue
    base = r[2].split("/")[-1]
    if not names or base in names:
        print(f"{r[6]}\t{r[0]}\t{base}")
