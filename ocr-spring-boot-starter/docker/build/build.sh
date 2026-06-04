#!/bin/bash
# ============================================================
# PaddleOCR 构建脚本
# ============================================================
# CPU 版本：
#   bash build.sh
# GPU 版本：
#   bash build.sh gpu
# ============================================================

set -e

VERSION="1.0.0"
MODE="${1:-cpu}"

echo "========================================"
echo " PaddleOCR Docker Builder v$VERSION"
echo " Mode: $MODE"
echo "========================================"

if [ "$MODE" = "gpu" ]; then
  echo "[Step] Building GPU image..."
  docker build -t "paddleocr-server:$VERSION-gpu" -f Dockerfile ..
  echo ""
  echo "✅ Done! Image: paddleocr-server:$VERSION-gpu"
  echo "   Run: docker run -d --name ocr -p 8000:8000 --gpus all paddleocr-server:$VERSION-gpu"
else
  echo "[Step] Building CPU image..."
  docker build -t "paddleocr-server:$VERSION" -f Dockerfile ..
  docker tag "paddleocr-server:$VERSION" paddleocr-server:latest
  echo ""
  echo "✅ Done! Image: paddleocr-server:$VERSION"
  echo "   Run: cd .. && docker compose up -d"
fi
