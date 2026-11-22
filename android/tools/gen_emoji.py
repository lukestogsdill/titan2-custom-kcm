"""Turn Unicode's emoji-test.txt into the compact asset the keyboard reads.

    curl -fsSLO https://www.unicode.org/Public/emoji/latest/emoji-test.txt
    python3 tools/gen_emoji.py emoji-test.txt app/src/main/assets/emoji.txt

Output format (UTF-8), one entry per line:
    #<group name>              (no tab: starts a group)
    <emoji>\t<lowercase name>
Keeps fully-qualified emoji only, drops the Component group and skin-tone
variants (the base, yellow emoji is kept).
"""
import re
import sys

SKIN_TONES = {chr(c) for c in range(0x1F3FB, 0x1F400)}
LINE = re.compile(r"^([0-9A-F ]+?)\s*;\s*fully-qualified\s*#\s*(\S+)\s+E\d+\.\d+\s+(.*)$")

src, dst = sys.argv[1], sys.argv[2]
out, group, count = [], None, 0
with open(src, encoding="utf-8") as f:
    for raw in f:
        line = raw.rstrip("\n")
        if line.startswith("# group:"):
            group = line.split(":", 1)[1].strip()
            if group != "Component":
                out.append("#" + group)
            continue
        if group == "Component":
            continue
        m = LINE.match(line)
        if not m:
            continue
        emoji = "".join(chr(int(cp, 16)) for cp in m.group(1).split())
        if SKIN_TONES & set(emoji):
            continue
        name = m.group(3).strip().lower().replace("\t", " ")
        out.append(f"{emoji}\t{name}")
        count += 1

with open(dst, "w", encoding="utf-8") as f:
    f.write("\n".join(out) + "\n")
print(f"{count} emoji -> {dst}")
