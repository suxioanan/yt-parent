"""
预下载所有 PaddleOCR 模型文件（Docker 构建时执行）
直接下载 tar 包到缓存目录，不跑推理（兼容 ARM 宿主）
"""

import os
import sys
import tarfile
import urllib.request
import shutil
from pathlib import Path

MODELS_DIR = os.path.expanduser("~/.paddleocr/whl")

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

STRUCTURE_MODELS = [
    {
        "name": "PP-Structure Layout",
        "url": "https://paddleocr.bj.bcebos.com/ppstructure/models/layout/picodet_lcnet_x1_0_layout_infer.tar",
        "target": "structure/layout/picodet_lcnet_x1_0_layout_infer",
    },
    # PP-Structure Table: 运行时首次调用时会自动下载
]


def download_and_extract(url: str, target_dir: str, name: str):
    """下载 tar 包并解压到目标目录"""
    target_path = os.path.join(MODELS_DIR, target_dir)
    if os.path.exists(os.path.join(target_path, "inference.pdmodel")):
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
            # PaddlePaddle tar 包内有顶层目录，需要移除顶层
            members = tar.getmembers()
            for member in members:
                # 移除顶层目录
                parts = member.name.split("/", 1)
                if len(parts) > 1:
                    member.name = parts[1]
                else:
                    continue
                if member.name:  # 跳过空路径
                    tar.extract(member, target_path)
        print(f"  ✓ {name} ready", flush=True)
    except Exception as e:
        print(f"  ✗ {name} extract failed: {e}")
    finally:
        if os.path.exists(tar_path):
            os.remove(tar_path)


def main():
    print("=" * 60)
    print("Downloading PaddleOCR models...")
    print("(Download only, no inference - ARM compatible)")
    print("=" * 60, flush=True)

    os.makedirs(MODELS_DIR, exist_ok=True)

    all_models = MODELS + STRUCTURE_MODELS

    for i, model in enumerate(all_models, 1):
        print(f"\n[{i}/{len(all_models)}] {model['name']}", flush=True)
        download_and_extract(model["url"], model["target"], model["name"])

    # 验证下载结果
    print("\n" + "=" * 60)
    print("Model summary:")
    total_size = 0
    for root, dirs, files in os.walk(MODELS_DIR):
        for f in files:
            fp = os.path.join(root, f)
            total_size += os.path.getsize(fp)

    model_count = sum(1 for f in Path(MODELS_DIR).rglob("inference.pdmodel"))
    print(f"  Models with inference.pdmodel: {model_count}")
    print(f"  Total size: {total_size / 1024 / 1024:.1f} MB")
    print("=" * 60, flush=True)


if __name__ == "__main__":
    main()
