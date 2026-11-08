"""Print 'x y' (centre) of the first UI Automator node whose text or content-desc equals the query."""
import re
import sys
import xml.etree.ElementTree as ET


def main() -> None:
    path, query = sys.argv[1], sys.argv[2]
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return
    candidates = []
    for node in root.iter("node"):
        label = node.get("text") or node.get("content-desc") or ""
        if label.strip() != query:
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        if x2 <= x1 or y2 <= y1:
            continue
        candidates.append(((x1 + x2) // 2, (y1 + y2) // 2))
    if candidates:
        x, y = candidates[0]
        print(f"{x} {y}")


if __name__ == "__main__":
    main()
