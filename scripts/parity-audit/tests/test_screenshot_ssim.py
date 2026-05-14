# scripts/parity-audit/tests/test_screenshot_ssim.py
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
FIX = pathlib.Path(__file__).resolve().parent / "fixtures"
sys.path.insert(0, str(ROOT))

import screenshot_ssim


def test_identical_images_have_ssim_one():
    s = screenshot_ssim.compute(FIX / "img_a.png", FIX / "img_a_copy.png")
    assert s["ssim"] > 0.99
    assert s["verdict"] == "match"


def test_different_images_have_low_ssim():
    s = screenshot_ssim.compute(FIX / "img_a.png", FIX / "img_b.png", threshold=0.92)
    assert s["ssim"] < 0.92
    assert s["verdict"] == "visual_diff"


def test_mismatched_dimensions_handled():
    import numpy as np, cv2, tempfile, pathlib as p
    with tempfile.TemporaryDirectory() as td:
        big = (p.Path(td) / "big.png")
        small = (p.Path(td) / "small.png")
        cv2.imwrite(str(big), (np.ones((400, 200, 3), dtype=np.uint8) * 200))
        cv2.imwrite(str(small), (np.ones((200, 100, 3), dtype=np.uint8) * 200))
        s = screenshot_ssim.compute(big, small)
        assert "resized" in s
        assert s["verdict"] in ("match", "visual_diff")
