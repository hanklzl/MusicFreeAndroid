# scripts/parity-audit/screenshot_ssim.py
"""Compute SSIM between two screenshots; output JSON verdict."""
from __future__ import annotations

import argparse
import json
import pathlib
import sys
from typing import Any

import cv2
import numpy as np
from skimage.metrics import structural_similarity as ssim


def _load_gray(path: pathlib.Path) -> np.ndarray:
    img = cv2.imread(str(path), cv2.IMREAD_COLOR)
    if img is None:
        raise FileNotFoundError(path)
    return cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)


def compute(rn: pathlib.Path, android: pathlib.Path, threshold: float = 0.92) -> dict[str, Any]:
    a = _load_gray(rn)
    b = _load_gray(android)
    resized = False
    if a.shape != b.shape:
        target = (min(a.shape[1], b.shape[1]), min(a.shape[0], b.shape[0]))
        a = cv2.resize(a, target, interpolation=cv2.INTER_AREA)
        b = cv2.resize(b, target, interpolation=cv2.INTER_AREA)
        resized = True
    score = float(ssim(a, b))
    return {
        "ssim": round(score, 4),
        "threshold": threshold,
        "verdict": "match" if score >= threshold else "visual_diff",
        "resized": resized,
    }


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--rn", required=True)
    p.add_argument("--android", required=True)
    p.add_argument("--threshold", type=float, default=0.92)
    args = p.parse_args()
    out = compute(pathlib.Path(args.rn), pathlib.Path(args.android), threshold=args.threshold)
    print(json.dumps(out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
