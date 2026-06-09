"""
预下载所有模型文件（Docker 构建时执行）
直接下载 tar/zip 包到缓存目录，不跑推理（兼容 ARM 宿主）
"""

import os
import sys
import tarfile
import zipfile
import urllib.request
import shutil
import io
from pathlib import Path

MODELS_DIR = os.path.expanduser("~/.paddleocr/whl")

# ============================================================
# PaddleOCR 模型（PP-OCRv4 + 角度分类）
# ============================================================
MODELS = [
    {
        "name": "PP-OCRv4 Detection",
        "url": "https://paddleocr.bj.bcebos.com/PP-OCRv4/chinese/ch_PP-OCRv4_det_infer.tar",
        "target": "det/ch/ch_PP-OCRv4_det_infer",
    },
    {
        "name": "PP-OCRv4 Recognition",
        "url": "https://paddleocr.bj.bcebos.com/PP-OCRv4/chinese/ch_PP-OCRv4_rec_infer.tar",
        "target": "rec/ch/ch_PP-OCRv4_rec_infer",
    },
    {
        "name": "Angle Classification",
        "url": "https://paddleocr.bj.bcebos.com/dygraph_v2.0/ch/ch_ppocr_mobile_v2.0_cls_infer.tar",
        "target": "cls/ch_ppocr_mobile_v2.0_cls_infer",
    },
]

# ============================================================
# PP-Structure 模型（版面分析 + 表格识别）
# ============================================================
STRUCTURE_MODELS = [
    {
        "name": "PP-Structure Layout",
        "url": "https://paddleocr.bj.bcebos.com/ppstructure/models/layout/picodet_lcnet_x1_0_layout_infer.tar",
        "target": "structure/layout/picodet_lcnet_x1_0_layout_infer",
    },
    {
        "name": "PP-Structure Table (SLANet)",
        "url": "https://paddleocr.bj.bcebos.com/ppstructure/models/slanet/ch_ppstructure_mobile_v2.0_SLANet_infer.tar",
        "target": "table/ch_ppstructure_mobile_v2.0_SLANet_infer",
    },
]

# ============================================================
# HyperLPR3 车牌识别模型（zip 格式，不同于 PaddleOCR 的 tar）
# ============================================================
HYPERLPR3_MODEL = {
    "name": "HyperLPR3 License Plate",
    "url": "http://hyperlpr.tunm.top/raw/20230229.zip",
    "target": "hyperlpr3",  # zip 内部自带 20230229/ 顶层目录
}


def download_and_extract(url: str, target_dir: str, name: str):
    """下载 tar 包并解压到目标目录"""
    target_path = os.path.join(MODELS_DIR, target_dir)
    if os.path.exists(os.path.join(target_path, "inference.pdmodel")) or \
       os.path.exists(os.path.join(target_path, "model.pdmodel")):
        print(f"  ✓ {name} already exists, skipping")
        return

    os.makedirs(target_path, exist_ok=True)
    tar_path = os.path.join(target_path, "model.tar")

    print(f"  → Downloading {name}...", flush=True)
    try:
        urllib.request.urlretrieve(url, tar_path)
    except Exception as e:
        print(f"  ✗ {name} download failed: {e}")
        if os.path.exists(tar_path):
            os.remove(tar_path)
        return

    print(f"  → Extracting {name}...", flush=True)
    try:
        with tarfile.open(tar_path, "r") as tar:
            members = tar.getmembers()
            for member in members:
                # 移除顶层目录
                parts = member.name.split("/", 1)
                if len(parts) > 1:
                    member.name = parts[1]
                else:
                    continue
                if member.name:
                    tar.extract(member, target_path)
        print(f"  ✓ {name} ready", flush=True)
    except Exception as e:
        print(f"  ✗ {name} extract failed: {e}")
    finally:
        if os.path.exists(tar_path):
            os.remove(tar_path)


def download_hyperlpr3():
    """下载 HyperLPR3 模型 zip 并解压到 hyperlpr3/ 目录"""
    target_path = os.path.join(MODELS_DIR, HYPERLPR3_MODEL["target"])
    # 检查关键文件是否已存在
    check_file = os.path.join(target_path, "20230229", "onnx", "rpv3_mdict_160_r3.onnx")
    if os.path.exists(check_file):
        print(f"  ✓ {HYPERLPR3_MODEL['name']} already exists, skipping")
        return

    os.makedirs(target_path, exist_ok=True)

    print(f"  → Downloading {HYPERLPR3_MODEL['name']}...", flush=True)
    try:
        with urllib.request.urlopen(HYPERLPR3_MODEL["url"]) as resp:
            data = resp.read()
    except Exception as e:
        print(f"  ✗ {HYPERLPR3_MODEL['name']} download failed: {e}")
        return

    print(f"  → Extracting {HYPERLPR3_MODEL['name']}...", flush=True)
    try:
        with zipfile.ZipFile(io.BytesIO(data), "r") as zf:
            zf.extractall(target_path)
        # 清理 macOS 打包时混入的无用文件
        for root, dirs, files in os.walk(target_path):
            for f in files:
                if f == ".DS_Store" or f.startswith("._"):
                    os.remove(os.path.join(root, f))
            for d in list(dirs):
                if d == "__MACOSX":
                    shutil.rmtree(os.path.join(root, d))
        print(f"  ✓ {HYPERLPR3_MODEL['name']} ready", flush=True)
    except Exception as e:
        print(f"  ✗ {HYPERLPR3_MODEL['name']} extract failed: {e}")


def main():
    print("=" * 60)
    print("Downloading all models...")
    print("(Download only, no inference - ARM compatible)")
    print("=" * 60, flush=True)

    os.makedirs(MODELS_DIR, exist_ok=True)

    all_models = MODELS + STRUCTURE_MODELS

    for i, model in enumerate(all_models, 1):
        print(f"\n[{i}/{len(all_models) + 1}] {model['name']}", flush=True)
        download_and_extract(model["url"], model["target"], model["name"])

    # HyperLPR3（zip 格式，单独处理）
    print(f"\n[{len(all_models) + 1}/{len(all_models) + 1}] {HYPERLPR3_MODEL['name']}", flush=True)
    download_hyperlpr3()

    # 验证下载结果
    print("\n" + "=" * 60)
    print("Model summary:")
    total_size = 0
    model_count = 0
    for root, dirs, files in os.walk(MODELS_DIR):
        for f in files:
            fp = os.path.join(root, f)
            total_size += os.path.getsize(fp)
            if f.endswith(".pdmodel") or f.endswith(".onnx"):
                model_count += 1

    print(f"  Model files (.pdmodel / .onnx): {model_count}")
    print(f"  Total size: {total_size / 1024 / 1024:.1f} MB")
    print("=" * 60, flush=True)


if __name__ == "__main__":
    main()
