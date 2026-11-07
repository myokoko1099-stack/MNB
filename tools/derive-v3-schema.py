#!/usr/bin/env python3
"""Makes the version 3 Room schema JSON from the version 4 one.

Room only writes the schema of the CURRENT version (4.json, when the app is built). Version 4 differs from
version 3 only by the three browser tables, so 3.json is 4.json without them.

Usage:
  python3 tools/derive-v3-schema.py \
      app/schemas/com.mnb.browser.translation.db.MnbDatabase/4.json \
      app/schemas/com.mnb.browser.translation.db.MnbDatabase/3.json
"""
import json
import pathlib
import sys

BROWSER_TABLES = {"bookmarks", "browser_history", "browser_tabs"}
FAKE_HASH = "mnb-schema-v3-derived-from-v4"


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    src = pathlib.Path(sys.argv[1])
    dst = pathlib.Path(sys.argv[2])
    data = json.loads(src.read_text(encoding="utf-8"))
    db = data["database"]
    if db["version"] != 4:
        sys.exit("expected a version 4 schema, found version %s" % db["version"])
    names = {e["tableName"] for e in db["entities"]}
    missing = BROWSER_TABLES - names
    if missing:
        sys.exit("the version 4 schema has no table(s): " + ", ".join(sorted(missing)))
    old_hash = db["identityHash"]
    db["entities"] = [e for e in db["entities"] if e["tableName"] not in BROWSER_TABLES]
    db["version"] = 3
    db["identityHash"] = FAKE_HASH
    db["setupQueries"] = [q.replace(old_hash, FAKE_HASH) for q in db.get("setupQueries", [])]
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote %s (%d tables)" % (dst, len(db["entities"])))


if __name__ == "__main__":
    main()
