#!/usr/bin/env python3

import argparse
import re
import sys
import xml.etree.ElementTree as ET


STATE_RULES = {
    "home-top": {
        "required_ids": [
            "screen.home.root",
            "home.navBar.root",
            "home.operations.root",
        ],
        "required_patterns": [],
    },
    "home-sheets": {
        "required_ids": [
            "home.sheets.root",
        ],
        "required_patterns": [
            r"^home\.sheets\.item\.",
        ],
    },
    "home-scroll": {
        "required_ids": [
            "screen.home.root",
        ],
        "required_patterns": [
            r"^home\.sheets\.item\.",
        ],
    },
    "drawer-open": {
        "required_ids": [
            "home.drawer.root",
        ],
        "required_patterns": [],
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Assert that a captured UI dump reached the requested state."
    )
    parser.add_argument("--xml", required=True, help="Path to the pulled XML dump file")
    parser.add_argument("--state", required=True, choices=sorted(STATE_RULES))
    parser.add_argument("--fragment", required=True, help="Capture fragment name")
    return parser.parse_args()


def collect_resource_ids(root: ET.Element) -> list[str]:
    resource_ids: list[str] = []
    for node in root.iter():
        resource_id = node.attrib.get("resource-id", "").strip()
        if resource_id:
            resource_ids.append(resource_id)
    return resource_ids


def main() -> int:
    args = parse_args()

    try:
        root = ET.parse(args.xml).getroot()
    except ET.ParseError as exc:
        print(f"Failed to parse dump XML {args.xml}: {exc}", file=sys.stderr)
        return 1

    resource_ids = collect_resource_ids(root)
    rule = STATE_RULES[args.state]

    missing_ids = [anchor for anchor in rule["required_ids"] if anchor not in resource_ids]
    if missing_ids:
        print(
            "Capture state assertion failed "
            f"for state={args.state} fragment={args.fragment}: "
            f"missing resource ids {missing_ids}",
            file=sys.stderr,
        )
        return 1

    for pattern in rule["required_patterns"]:
        if not any(re.search(pattern, resource_id) for resource_id in resource_ids):
            print(
                "Capture state assertion failed "
                f"for state={args.state} fragment={args.fragment}: "
                f"missing resource id pattern {pattern}",
                file=sys.stderr,
            )
            return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
