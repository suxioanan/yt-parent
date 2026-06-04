# ============================================================
# PaddleOCR Dockerfile
# ============================================================
#
# 构建上下文为 docker/ 目录，Dockerfile 在 build/ 子目录。
# 文件引用以 docker/ 为根路径。
#
# 构建命令:
#   CPU: docker build -t paddleocr-server:1.0.0 -f build/Dockerfile .
#   GPU: docker build -t paddleocr-server:1.0.0-gpu -f build/Dockerfile .
#
# 或通过 docker-compose:
#   docker compose up -d --build
#
# Java 后端调用:
#   POST /ocr/ch       - 中文文字识别 (PP-OCRv4)
#   POST /ocr/plate    - 车牌识别 + 颜色识别
#   POST /structure    - 文档结构分析 (PP-Structure)
# ============================================================

# ===================== CPU（默认） =====================
FROM paddlepaddle/paddle:2.6.1
# ===================== GPU（有 NVIDIA GPU + nvidia-container-toolkit） =====================
# FROM paddlepaddle/paddle:2.6.1-gpu-cuda11.8-cudnn8.6-trt8.5
#   ⚠️ GPU 镜像已内置 paddlepaddle-gpu
#      且无需 apt-get 装任何系统库
# ============================================================

WORKDIR /app

# ---------- 系统依赖 ----------
# LibreOffice：用于 doc/docx 转 PDF
# 中文字体：保证 PDF 渲染时中文不会变成方块
RUN apt-get update && apt-get install -y \
    libreoffice-writer-nogui \
    fonts-wqy-microhei \
    fonts-wqy-zenhei \
    fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

# ---------- Python 依赖 ----------
# paddlepaddle 已由基础镜像自带，无需重复安装
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ---------- 预下载模型 ----------
COPY build/download_models.py download_models.py
RUN python download_models.py

# ---------- 应用代码 ----------
COPY ocr_server.py .

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/')" || exit 1

CMD ["python", "ocr_server.py"]
