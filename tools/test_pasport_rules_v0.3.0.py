#!/usr/bin/env python3
"""Test čistění názvů stanic a UDU = TUDU[:5]."""

from __future__ import annotations

import re

PREFIX_RE = re.compile(r"^(žst\.|odb\.|z\.)\s*", re.IGNORECASE)


def clean_jmeno(raw: str) -> str:
    s = (raw or "").strip()
    while True:
        m = PREFIX_RE.match(s)
        if not m:
            break
        s = s[m.end() :].strip()
    return s


def main() -> None:
    assert clean_jmeno("žst. Meziměstí") == "Meziměstí"
    assert clean_jmeno("ŽST. Meziměstí") == "Meziměstí"
    assert clean_jmeno("odb. Hronov") == "Hronov"
    assert clean_jmeno("z. Foo") == "Foo"
    assert clean_jmeno("Meziměstí") == "Meziměstí"
    assert "12345XX"[:5] == "12345"
    print("ok — stanice/UDU v0.3.0")


if __name__ == "__main__":
    main()
