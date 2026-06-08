"""
PaddleOCR 多模型服务
供后端 Java 调用，支持不同模型/任务

API:
  GET  /                健康检查
  POST /ocr/ch          PP-OCRv4 中文文字识别
  POST /ocr/plate       车牌识别
  POST /structure       PP-Structure 文档结构分析（表格/标题/段落）
  POST /convert/docx-to-pdf  docx/docx -> PDF 转换
"""

from fastapi import FastAPI, UploadFile, File, HTTPException, Form, Query, BackgroundTasks
from fastapi.responses import FileResponse
from paddleocr import PaddleOCR, PPStructure
import uvicorn
import tempfile
import shutil
import os
import logging
import urllib.request
import socket
import threading
from urllib.parse import urlparse
from logging.handlers import RotatingFileHandler

# ============================================================
# 日志配置（同时输出到控制台和文件）
# ============================================================
LOG_DIR = "/app/logs"
os.makedirs(LOG_DIR, exist_ok=True)

log_formatter = logging.Formatter(
    "%(asctime)s | %(levelname)-5s | %(name)s | %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

file_handler = RotatingFileHandler(
    os.path.join(LOG_DIR, "ocr_server.log"),
    maxBytes=10 * 1024 * 1024,
    backupCount=7,
    encoding="utf-8",
)
file_handler.setFormatter(log_formatter)

console_handler = logging.StreamHandler()
console_handler.setFormatter(log_formatter)

root_logger = logging.getLogger()
root_logger.setLevel(logging.INFO)
root_logger.addHandler(file_handler)
root_logger.addHandler(console_handler)

logger = logging.getLogger("paddleocr")

# ============================================================
# 车牌省份简称列表 & 车牌正则
# ============================================================
PLATE_PROVINCES = ["京", "津", "沪", "渝", "冀", "豫", "云", "辽", "黑", "湘",
                   "皖", "鲁", "新", "苏", "浙", "赣", "鄂", "桂", "甘", "晋",
                   "蒙", "陕", "吉", "闽", "贵", "粤", "川", "青", "藏", "琼", "宁"]

import re as _re
PLATE_PATTERN_STR = r'^[' + ''.join(PLATE_PROVINCES) + r'][A-Z][A-Z0-9·\s]{4,7}$'
PLATE_PATTERN = _re.compile(PLATE_PATTERN_STR)

# ============================================================
# 安全与限制常量
# ============================================================
MAX_DOWNLOAD_SIZE = 20 * 1024 * 1024   # URL 下载最大 20MB
MAX_UPLOAD_SIZE = 50 * 1024 * 1024     # 文件上传最大 50MB
DOWNLOAD_TIMEOUT = 30                    # URL 下载超时（秒）
MAX_PDF_PAGES = 50                       # PDF 最大页数

# 内网/私有 IP 段（SSRF 防护）
# 永远禁止的危险地址（loopback、link-local、云元数据等）
_BLOCKED_IP_PREFIXES = (
    "127.",          # loopback
    "169.254.",      # link-local / 云元数据 (AWS/阿里云等)
    "0.",            # 0.0.0.0/8
)

# 默认禁止的私有地址段（可通过 SSRF_ALLOWED_NETS 环境变量逐条放开）
_PRIVATE_IP_PREFIXES = (
    "10.",           # A 类私有
    "172.16.", "172.17.", "172.18.", "172.19.",
    "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
    "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
    "192.168.",      # C 类私有
    "100.64.", "100.65.", "100.66.", "100.67.", "100.68.", "100.69.",
    "100.70.", "100.71.", "100.72.", "100.73.", "100.74.", "100.75.",
    "100.76.", "100.77.", "100.78.", "100.79.", "100.80.", "100.81.",
    "100.82.", "100.83.", "100.84.", "100.85.", "100.86.", "100.87.",
    "100.88.", "100.89.", "100.90.", "100.91.", "100.92.", "100.93.",
    "100.94.", "100.95.", "100.96.", "100.97.", "100.98.", "100.99.",
    "100.100.", "100.101.", "100.102.", "100.103.", "100.104.", "100.105.",
    "100.106.", "100.107.", "100.108.", "100.109.", "100.110.", "100.111.",
    "100.112.", "100.113.", "100.114.", "100.115.", "100.116.", "100.117.",
    "100.118.", "100.119.", "100.120.", "100.121.", "100.122.", "100.123.",
    "100.124.", "100.125.", "100.126.", "100.127.",  # CGNAT
)

# SSRF 白名单：逗号分隔的 IP 前缀，匹配到的内网地址放行
# 示例: "192.168.1.,10.0.0." — 允许访问 192.168.1.x 和 10.0.0.x 网段
_SSRF_ALLOWED_NETS = tuple(
    p.strip() for p in os.environ.get("SSRF_ALLOWED_NETS", "").split(",") if p.strip()
)


def _is_internal_ip(ip: str) -> bool:
    """检查 IP 是否为应被禁止的内网地址。
    永远禁止：loopback、link-local、0.x、IPv6 内网
    默认禁止：10.x、172.16-31.x、192.168.x、100.64-127.x（可通过 SSRF_ALLOWED_NETS 逐条放开）
    """
    # IPv6 loopback / link-local / unique-local（永远禁止）
    if ip in ("::1", "::") or ip.startswith(("fe80:", "fc", "fd")):
        return True
    # IPv4 危险地址（永远禁止）
    for prefix in _BLOCKED_IP_PREFIXES:
        if ip.startswith(prefix):
            return True
    # 优先检查白名单：匹配到则放行
    for prefix in _SSRF_ALLOWED_NETS:
        if ip.startswith(prefix):
            return False
    # IPv4 私有地址（默认禁止）
    for prefix in _PRIVATE_IP_PREFIXES:
        if ip.startswith(prefix):
            return True
    return False


def _check_ssrf(hostname: str):
    """解析域名全部 IP 地址，任一指向内网即拒绝（防御 DNS Rebinding）"""
    # 先检查 hostname 本身是不是 IP（避免 DNS 解析绕过）
    try:
        import ipaddress
        ipaddress.ip_address(hostname)
        # hostname 本身是 IP，直接检查
        if _is_internal_ip(hostname):
            raise HTTPException(status_code=400, detail="Access to internal IP is forbidden")
        return
    except ValueError:
        pass  # 不是 IP，继续 DNS 解析

    try:
        addrinfo = socket.getaddrinfo(hostname, None)
    except socket.gaierror:
        raise HTTPException(status_code=400, detail=f"Cannot resolve hostname: {hostname}")

    for result in addrinfo:
        ip = result[4][0]  # (family, type, proto, canonname, sockaddr)
        if _is_internal_ip(ip):
            raise HTTPException(status_code=400, detail="Access to internal IP is forbidden")


app = FastAPI(title="PaddleOCR Multi-Model Server", version="1.0.0")


@app.on_event("startup")
def _warmup_models():
    """后台预热 PP-Structure 模型，避免首次请求卡 10-20 秒"""
    def _warmup():
        logger.info("[Warmup] pre-loading PP-Structure engine...")
        try:
            get_structure_engine()
            logger.info("[Warmup] PP-Structure engine ready")
        except Exception as e:
            logger.warning(f"[Warmup] PP-Structure pre-load failed: {e}")

    threading.Thread(target=_warmup, daemon=True).start()


# ============================================================
# 模型全局实例（首次请求时懒加载，双重检查锁保证线程安全）
# ============================================================
ocr_ch: PaddleOCR | None = None           # PP-OCRv4 中文
ocr_plate: PaddleOCR | None = None        # 车牌识别
structure_engine: PPStructure | None = None       # PP-Structure (含表格)
_structure_no_table: PPStructure | None = None    # PP-Structure (不含表格，更快)

_ocr_ch_lock = threading.Lock()
_ocr_plate_lock = threading.Lock()
_structure_lock = threading.Lock()
_structure_no_table_lock = threading.Lock()


def get_ocr_ch() -> PaddleOCR:
    global ocr_ch
    if ocr_ch is not None:
        return ocr_ch
    with _ocr_ch_lock:
        if ocr_ch is None:
            ocr_ch = PaddleOCR(
                use_angle_cls=True,
                lang="ch",
                use_gpu=False,
                show_log=False,
                det_db_thresh=0.2,        # 默认 0.3
                det_db_box_thresh=0.3,    # 默认 0.5
                det_db_unclip_ratio=2.0,  # 默认 1.6
            )
    return ocr_ch


def get_ocr_plate() -> PaddleOCR:
    global ocr_plate
    if ocr_plate is not None:
        return ocr_plate
    with _ocr_plate_lock:
        if ocr_plate is None:
            ocr_plate = PaddleOCR(
                use_angle_cls=True,
                lang="ch",
                use_gpu=False,     # GPU 时改 True
                show_log=False,
                det_db_thresh=0.15,       # 降低检测阈值，捕获省份小汉字
                det_db_box_thresh=0.25,   # 降低框置信度阈值
                det_db_unclip_ratio=2.0,  # 增大扩展比例
                rec_image_shape="3, 48, 320",   # 车牌专用分辨率
                max_text_length=10,             # 车牌号长度
                drop_score=0.2,                 # 过滤低置信度检测框，对模糊车牌更友好
            )
    return ocr_plate


def get_structure_engine(extract_table: bool = True) -> PPStructure:
    """获取 PP-Structure 引擎实例。
    extract_table=False 时跳过表格识别，速度提升 3-5 倍，适合纯文档。
    """
    global structure_engine, _structure_no_table
    if extract_table:
        if structure_engine is not None:
            return structure_engine
        with _structure_lock:
            if structure_engine is None:
                structure_engine = PPStructure(
                    show_log=False,
                    lang="ch",
                    use_gpu=False,     # GPU 时改 True
                    ocr=True,
                    table=True,
                )
        return structure_engine
    else:
        if _structure_no_table is not None:
            return _structure_no_table
        with _structure_no_table_lock:
            if _structure_no_table is None:
                _structure_no_table = PPStructure(
                    show_log=False,
                    lang="ch",
                    use_gpu=False,
                    ocr=True,
                    table=False,        # 跳过表格识别，大幅提速
                )
        return _structure_no_table


# ============================================================
# 车牌颜色识别
# ============================================================
# 中国车牌颜色规则：
#   蓝牌 — 小型汽车（最常见）
#   黄牌 — 大型汽车、摩托车
#   绿牌 — 新能源汽车
#   白牌 — 警车、军车
#   黑牌 — 涉外车辆

PLATE_COLORS = {
    "blue": {"name": "蓝牌", "desc": "小型汽车"},
    "yellow": {"name": "黄牌", "desc": "大型汽车/摩托车"},
    "green": {"name": "绿牌", "desc": "新能源汽车"},
    "white": {"name": "白牌", "desc": "警车/军车"},
    "black": {"name": "黑牌", "desc": "涉外车辆"},
}


def detect_plate_color(image_path: str, image: "np.ndarray | None" = None) -> dict:
    """
    基于车牌中心区域 + 背景色投票识别车牌颜色。
    策略：取中心 60% 排除边框，仅对蓝/绿/黄三色计数，
    白色和黑色（字符/边框）不参与颜色投票，避免误判。
    image: 可选的预加载图片 (BGR numpy array)，避免重复读图
    返回: {"type": "blue", "name": "蓝牌", "desc": "小型汽车"}
    """
    try:
        import cv2
        import numpy as np

        if image is not None:
            img = image
        else:
            img = cv2.imread(image_path)
        if img is None:
            return {"type": "unknown", "name": "未知", "desc": ""}

        h, w = img.shape[:2]

        # ---- 1. 只取中心 60% 区域，排除边框干扰 ----
        cx1, cx2 = int(w * 0.2), int(w * 0.8)
        cy1, cy2 = int(h * 0.2), int(h * 0.8)
        if cx2 <= cx1 or cy2 <= cy1:
            # 图太小，回退到全图
            cx1, cx2, cy1, cy2 = 0, w, 0, h
        center_region = img[cy1:cy2, cx1:cx2]
        hsv = cv2.cvtColor(center_region, cv2.COLOR_BGR2HSV)

        # ---- 2. 只对车牌背景色（蓝/绿/黄）投票，白/黑不参与 ----
        color_ranges = {
            "blue":   ([100, 43, 46], [130, 255, 255]),
            "green":  ([60, 43, 46],  [90, 255, 255]),
            "yellow": ([20, 43, 46],  [40, 255, 255]),
        }

        color_pixels = {}
        for color_name, (lower, upper) in color_ranges.items():
            mask = cv2.inRange(hsv, np.array(lower), np.array(upper))
            color_pixels[color_name] = cv2.countNonZero(mask)

        total_center = (cx2 - cx1) * (cy2 - cy1)
        max_color = max(color_pixels, key=color_pixels.get)
        max_ratio = color_pixels[max_color] / total_center if total_center > 0 else 0

        # ---- 3. 有足够背景色 → 直接判定 ----
        if color_pixels[max_color] > 0 and max_ratio >= 0.05:
            info = PLATE_COLORS.get(max_color, {"name": "未知", "desc": ""})
            return {
                "type": max_color,
                "name": info["name"],
                "desc": info["desc"],
                "confidence": round(max_ratio, 4),
            }

        # ---- 4. 无彩色背景 → 回退判断白牌/黑牌 ----
        white_mask = cv2.inRange(hsv, np.array([0, 0, 200]), np.array([180, 30, 255]))
        black_mask = cv2.inRange(hsv, np.array([0, 0, 0]), np.array([180, 255, 46]))
        white_px = cv2.countNonZero(white_mask)
        black_px = cv2.countNonZero(black_mask)
        if white_px > black_px and white_px / total_center >= 0.3:
            return {"type": "white", "name": "白牌", "desc": "警车/军车", "confidence": round(white_px / total_center, 4)}
        if black_px > white_px and black_px / total_center >= 0.3:
            return {"type": "black", "name": "黑牌", "desc": "涉外车辆", "confidence": round(black_px / total_center, 4)}

        return {"type": "unknown", "name": "未知", "desc": ""}
    except Exception as e:
        return {"type": "error", "name": "分析失败", "desc": str(e)}


# ============================================================
# 支持的文件格式
# ============================================================
# 图片格式
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".webp"}
# 文档格式（需转换后再处理）
DOCUMENT_SUFFIXES = {".doc", ".docx", ".pdf"}
# 所有允许上传的格式
ALLOWED_SUFFIXES = IMAGE_SUFFIXES | DOCUMENT_SUFFIXES


# ============================================================
# 文档格式转换（doc/docx/pdf -> 图片列表）
# ============================================================


def convert_document_to_images(document_path: str) -> list[str]:
    """
    将 doc/docx/pdf 转换为图片列表，每页一张临时图片。
    返回图片路径列表，调用方负责清理。
    """
    suffix = os.path.splitext(document_path)[-1].lower()

    if suffix in DOCUMENT_SUFFIXES:
        pdf_path = document_path

        # doc/docx -> PDF
        if suffix in {".doc", ".docx"}:
            pdf_path = _convert_doc_to_pdf(document_path)
            logger.info(f"[Convert] doc -> pdf: {pdf_path}")

        # PDF -> 图片列表
        return _convert_pdf_to_images(pdf_path, cleanup_pdf=(pdf_path != document_path))
    else:
        return [document_path]


def _convert_doc_to_pdf(doc_path: str) -> str:
    """
    将 .doc / .docx 转换为 PDF（使用 LibreOffice）。
    返回 PDF 临时文件路径。
    """
    import subprocess

    out_dir = tempfile.mkdtemp(prefix="ocrtmp_")
    try:
        result = subprocess.run(
            ["libreoffice", "--headless", "--convert-to", "pdf",
             "--outdir", out_dir, doc_path],
            capture_output=True, text=True, timeout=120
        )
        if result.returncode != 0:
            raise RuntimeError(
                f"LibreOffice convert failed (rc={result.returncode}): "
                f"{result.stderr or result.stdout}"
            )

        # 查找生成的 PDF
        base = os.path.splitext(os.path.basename(doc_path))[0]
        for f in os.listdir(out_dir):
            if f.lower().startswith(base.lower()) and f.lower().endswith(".pdf"):
                return os.path.join(out_dir, f)

        raise RuntimeError("PDF not found after LibreOffice conversion")

    except FileNotFoundError:
        raise RuntimeError(
            "LibreOffice not installed. "
            "Install with: apt-get install -y libreoffice-writer-nogui"
        )


def _convert_pdf_to_images(pdf_path: str, cleanup_pdf: bool = False) -> list[str]:
    """
    将 PDF 每页渲染为独立的 JPEG 图片。
    返回图片路径列表。
    """
    try:
        import fitz  # PyMuPDF
    except ImportError:
        raise RuntimeError(
            "PyMuPDF not installed. Run: pip install PyMuPDF"
        )

    doc = fitz.open(pdf_path)
    image_paths = []

    # 大 PDF 防护：超过最大页数直接拒绝
    if len(doc) > MAX_PDF_PAGES:
        doc.close()
        raise RuntimeError(
            f"PDF has {len(doc)} pages, exceeding limit of {MAX_PDF_PAGES}. "
            f"Please split the document or reduce resolution."
        )

    try:
        for page_num in range(len(doc)):
            page = doc[page_num]
            # 渲染为中等分辨率图片 (200 DPI) —— 300 DPI 对 PPStructure 可能太大了
            mat = fitz.Matrix(200 / 72, 200 / 72)  # 72pt -> 200dpi
            pix = page.get_pixmap(matrix=mat, alpha=False)

            # PyMuPDF 输出 RGB，转存为 JPEG
            img_path = tempfile.NamedTemporaryFile(
                delete=False, suffix=f"_page{page_num + 1}.jpg"
            ).name
            pix.save(img_path)
            image_paths.append(img_path)

        logger.info(f"[Convert] pdf -> {len(image_paths)} images")
        return image_paths

    finally:
        doc.close()
        if cleanup_pdf:
            # 清理临时的 PDF（由 doc→pdf 生成的）
            pdf_dir = os.path.dirname(pdf_path)
            try:
                os.remove(pdf_path)
                os.rmdir(pdf_dir)
            except OSError:
                pass


# ============================================================
# 图片获取（支持 file 上传 或 url 下载）
# ============================================================


def save_upload(file: UploadFile) -> str:
    """保存上传文件到临时目录，返回路径（含大小限制）"""
    if not file.filename:
        raise HTTPException(status_code=400, detail="Filename is required")

    suffix = os.path.splitext(file.filename)[-1].lower()
    if suffix not in ALLOWED_SUFFIXES:
        raise HTTPException(status_code=400, detail=f"Unsupported format: {suffix}")

    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    try:
        # 边写入边检查大小，防止超大文件打爆磁盘
        total = 0
        while True:
            chunk = file.file.read(8192)
            if not chunk:
                break
            total += len(chunk)
            if total > MAX_UPLOAD_SIZE:
                tmp.close()
                os.remove(tmp.name)
                raise HTTPException(
                    status_code=413,
                    detail=f"File too large ({total} bytes received, max {MAX_UPLOAD_SIZE})"
                )
            tmp.write(chunk)
        tmp.close()
        return tmp.name
    except HTTPException:
        raise
    except Exception:
        tmp.close()
        try:
            os.remove(tmp.name)
        except OSError:
            pass
        raise


def download_url(url: str) -> str:
    """下载 URL 图片到临时目录，返回路径（含 SSRF 防护、超时、流式下载+大小限制）"""
    if not url.startswith(("http://", "https://")):
        raise HTTPException(status_code=400, detail="Invalid URL")

    hostname = urlparse(url).hostname
    if not hostname:
        raise HTTPException(status_code=400, detail="Invalid URL: cannot extract hostname")

    # ---- SSRF 防护：getaddrinfo 解析全部 IP，任一为内网即拒绝 ----
    _check_ssrf(hostname)

    suffix = os.path.splitext(url.split("?")[0])[-1].lower()
    if suffix not in ALLOWED_SUFFIXES:
        suffix = ".jpg"  # 默认

    logger.info(f"Downloading image from URL: {url}")
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    try:
        tmp.close()
        # 使用 urlopen 替代 urlretrieve，支持 timeout 和流式读取
        req = urllib.request.Request(url, headers={"User-Agent": "OCR-Server/1.0"})
        with urllib.request.urlopen(req, timeout=DOWNLOAD_TIMEOUT) as response:
            # 检查 Content-Length 头部
            content_length = response.headers.get("Content-Length")
            if content_length and int(content_length) > MAX_DOWNLOAD_SIZE:
                os.remove(tmp.name)
                raise HTTPException(
                    status_code=400,
                    detail=f"File size ({content_length} bytes) exceeds maximum ({MAX_DOWNLOAD_SIZE})"
                )
            # 流式写入，边下边检查大小
            downloaded = 0
            with open(tmp.name, "wb") as f:
                while True:
                    chunk = response.read(8192)
                    if not chunk:
                        break
                    downloaded += len(chunk)
                    if downloaded > MAX_DOWNLOAD_SIZE:
                        os.remove(tmp.name)
                        raise HTTPException(
                            status_code=400,
                            detail=f"Downloaded file too large (>{MAX_DOWNLOAD_SIZE} bytes)"
                        )
                    f.write(chunk)
        return tmp.name
    except HTTPException:
        raise
    except Exception as e:
        try:
            os.remove(tmp.name)
        except OSError:
            pass
        raise HTTPException(status_code=400, detail=f"Failed to download URL: {str(e)}")


def run_ocr(ocr: PaddleOCR, image_path: str, cls: bool = True) -> dict:
    """执行 OCR 识别，返回统一格式。cls=False 角度分类跳过可提速 20-30%"""
    try:
        result = ocr.ocr(image_path, cls=cls)
        if not result or not result[0]:
            return {"success": True, "text": "", "items": []}

        items = []
        for line in result:
            for item in line:
                box = [round(float(c), 1) for point in item[0] for c in point]
                items.append({
                    "text": item[1][0],
                    "confidence": round(float(item[1][1]), 4),
                    "box": box,
                })

        full_text = "\n".join(item["text"] for item in items)
        return {
            "success": True,
            "text": full_text,
            "items": items,
            "count": len(items),
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"OCR failed: {str(e)}")


# ============================================================
# 车牌识别 + 省份简称补全
# ============================================================
# 策略：
# 1. 全图 OCR（使用降低后的检测阈值）
# 2. 如果省份缺失，基于已检测字符的 bounding box 推算省份位置
# 3. 对该区域做图像预处理（二值化 + 对比度增强）后重识别
# 4. 对识别结果做省份模糊匹配

def run_plate_ocr(image_path: str, image: "np.ndarray | None" = None, cls: bool = True) -> dict:
    """
    车牌识别：PaddleOCR 识别 + 省份简称补全
    image: 可选的预加载图片 (BGR numpy array)，避免重复读图
    cls: 是否启用角度分类，False 可提速 20-30%
    """

    # 第一轮：全图 OCR
    result = run_ocr(get_ocr_plate(), image_path, cls=cls)
    plate_text = result.get("text", "").strip()
    first_line = plate_text.split("\n")[0].strip() if plate_text else ""

    # 检查是否已经是完整车牌
    if PLATE_PATTERN.match(first_line):
        result["model"] = "paddleocr"
        return result

    # 第一行看起来像车牌（含字母数字）但缺少省份简称
    plate_like = _re.sub(r"[^A-Z0-9·\s]", "", first_line).strip()
    if len(plate_like) >= 4:
        logger.info(f"[Plate] province missing, text={first_line!r}, plate_like={plate_like!r}")
        province = _try_recover_province(image_path, first_line, result, image=image)
        if province:
            full_plate = province + plate_like
            result["text"] = full_plate
            if result.get("items"):
                result["items"][0]["text"] = full_plate
            logger.info(f"[Plate] province fixed: {first_line} -> {full_plate}")

    result["model"] = "paddleocr"
    return result


def _try_recover_province(image_path: str, first_line: str, result: dict,
                        image: "np.ndarray | None" = None) -> str | None:
    """
    尝试从图片中恢复省份简称。
    image: 可选的预加载图片 (BGR numpy array)，避免重复读图
    返回省份字符，或 None。
    """
    try:
        import cv2
        import numpy as np

        # 优先使用传入的图片数组，减少重复读图
        if image is not None:
            img = image
        else:
            img = cv2.imread(image_path)
        if img is None:
            return None

        h, w = img.shape[:2]

        # 策略 1：基于已有检测框推算省份位置
        items = result.get("items", [])
        if items and "box" in items[0]:
            box = items[0]["box"]
            xs = [box[i] for i in range(0, len(box), 2)]
            min_x = int(min(xs))
            ys = [box[i] for i in range(1, len(box), 2)]
            min_y = int(min(ys))
            max_y = int(max(ys))

            char_width = max(40, min_x - int(xs[0]) + 5)
            crop_x1 = max(0, min_x - char_width * 2)
            crop_x2 = min(min_x + 10, w)
            crop_y1 = max(0, min_y - 10)
            crop_y2 = min(h, max_y + 10)

            if crop_x2 > crop_x1 and crop_y2 > crop_y1:
                province = _ocr_province_region(
                    img[crop_y1:crop_y2, crop_x1:crop_x2], "box-based crop"
                )
                if province:
                    return province

        # 策略 2：回退到左侧区域裁剪（扩大到左侧 1/3）
        left_region = img[0:h, 0:max(w // 3, 200)]
        province = _ocr_province_region(left_region, "left 1/3 fallback")
        if province:
            return province

        # 策略 3：全图二值化后重识别（直接传数组，避免写临时文件）
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        _, binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
        binary_bgr = cv2.cvtColor(binary, cv2.COLOR_GRAY2BGR)
        binary_result = get_ocr_plate().ocr(binary_bgr, cls=True)
        if binary_result and binary_result[0]:
            for item in binary_result[0]:
                t = item[1][0].strip()
                for p in PLATE_PROVINCES:
                    if p in t:
                        return p

        return None

    except Exception as e:
        logger.warning(f"[Plate] province recovery failed: {e}")
        return None


def _ocr_province_region(region, label: str) -> str | None:
    """
    对指定区域做图像预处理后识别省份字符。
    直接传递 numpy 数组给 PaddleOCR，避免 JPEG 编解码。
    """
    import cv2
    import numpy as np

    if region.size == 0:
        return None

    gray = cv2.cvtColor(region, cv2.COLOR_BGR2GRAY)

    candidates = [
        ("raw", region),
        ("gray_thresh", _otsu_binary(gray)),
        ("adaptive_thresh", _adaptive_binary(gray)),
        ("contrast_enhanced", _enhance_contrast(region)),
    ]

    for name, processed in candidates:
        # PaddleOCR 直接接受 numpy 数组，无需写磁盘再读回
        ocr_result = get_ocr_plate().ocr(processed, cls=True)
        if ocr_result and ocr_result[0]:
            for item in ocr_result[0]:
                t = item[1][0].strip()
                conf = item[1][1]
                # 直接匹配
                for p in PLATE_PROVINCES:
                    if p in t:
                        logger.info(
                            f"[Plate] province found via {label}/{name}: "
                            f"{p!r} (conf={conf:.4f}, text={t!r})"
                        )
                        return p
                # 模糊匹配
                if len(t) <= 2 and conf > 0.5:
                    matched = _fuzzy_match_province(t)
                    if matched:
                        logger.info(
                            f"[Plate] province fuzzy-matched via {label}/{name}: "
                            f"{matched!r} from {t!r} (conf={conf:.4f})"
                        )
                        return matched

    return None


def _otsu_binary(gray):
    """Otsu 自适应二值化"""
    import cv2
    _, binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    return cv2.cvtColor(binary, cv2.COLOR_GRAY2BGR)


def _adaptive_binary(gray):
    """自适应阈值二值化"""
    import cv2
    binary = cv2.adaptiveThreshold(
        gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY, 11, 2
    )
    return cv2.cvtColor(binary, cv2.COLOR_GRAY2BGR)


def _enhance_contrast(img):
    """对比度增强 (CLAHE)"""
    import cv2
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
    l = clahe.apply(l)
    enhanced = cv2.merge([l, a, b])
    return cv2.cvtColor(enhanced, cv2.COLOR_LAB2BGR)


# 省份字符形近字映射（OCR 误识别时的模糊匹配）
PROVINCE_FUZZY_MAP = {
    "鱼": "鲁", "龟": "鲁", "曾": "鲁", "冒": "鲁", "兽": "鲁",
    "享": "京", "亭": "京", "高": "京", "亮": "京",
    "律": "津", "建": "津",
    "户": "沪", "护": "沪",
    "偷": "渝", "输": "渝", "榆": "渝",
    "翼": "冀", "异": "冀",
    "预": "豫", "像": "豫", "象": "豫",
    "去": "云",
    "了": "辽", "疗": "辽",
    "里": "黑", "墨": "黑",
    "相": "湘", "想": "湘",
    "完": "皖", "院": "皖",
    "章": "赣", "彰": "赣",
    "鳄": "鄂", "噩": "鄂",
    "佳": "桂", "挂": "桂", "杜": "桂",
    "于": "甘", "廿": "甘",
    "普": "晋", "亚": "晋",
    "朦": "蒙", "檬": "蒙",
    "峡": "陕", "狭": "陕",
    "古": "吉", "士": "吉", "告": "吉",
    "门": "闽", "间": "闽",
    "责": "贵", "遗": "贵",
    "奥": "粤", "澳": "粤",
    "三": "川", "州": "川",
    "表": "青", "毒": "青", "清": "青",
    "臧": "藏", "盐": "藏",
    "球": "琼", "环": "琼",
    "宇": "宁", "守": "宁", "安": "宁",
    "亲": "新", "昕": "新", "析": "新",
    "办": "苏", "东": "苏",
    "折": "浙", "拆": "浙", "淅": "浙",
}


def _fuzzy_match_province(text: str) -> str | None:
    """根据 OCR 识别结果做模糊匹配，返回最可能的省份字符。"""
    if not text:
        return None
    if text in PLATE_PROVINCES:
        return text
    if len(text) == 1:
        return PROVINCE_FUZZY_MAP.get(text)
    for char in text:
        if char in PLATE_PROVINCES:
            return char
        mapped = PROVINCE_FUZZY_MAP.get(char)
        if mapped:
            return mapped
    return None


# ============================================================
# API Endpoints
# ============================================================

@app.get("/")
def home():
    logger.info("Health check")
    return {
        "status": "running",
        "service": "PaddleOCR Multi-Model",
        "endpoints": {
            "ocr_ch": "POST /ocr/ch       - 中文文字识别 (PP-OCRv4)",
            "ocr_plate": "POST /ocr/plate  - 车牌识别",
            "structure": "POST /structure  - 文档结构分析 (PP-Structure)",
        }
    }


@app.post("/ocr/ch")
@app.post("/ocr/ch/url")
def ocr_chinese(
        file: UploadFile = File(None),
        url: str = Query(None, description="图片 URL，与 file 二选一"),
        cls: bool = Query(True, description="是否启用角度分类，身份证/截图等设为 false 可提速 20-30%"),
):
    """PP-OCRv4 中文通用文字识别"""
    if file:
        logger.info(f"[PP-OCRv4] file={file.filename} cls={cls}")
        path = save_upload(file)
    elif url:
        path = download_url(url)
    else:
        raise HTTPException(status_code=400, detail="Must provide 'file' or 'url'")

    try:
        result = run_ocr(get_ocr_ch(), path, cls=cls)
        logger.info(f"[PP-OCRv4] done, count={result.get('count', 0)}")
        return result
    finally:
        os.remove(path)


@app.post("/ocr/plate")
@app.post("/ocr/plate/url")
def ocr_license_plate(
        file: UploadFile = File(None),
        url: str = Query(None, description="图片 URL，与 file 二选一"),
        cls: bool = Query(True, description="是否启用角度分类，设为 false 可提速 20-30%"),
):
    """车牌文字识别（优先 PaddleX 专用模型，回退 PaddleOCR）"""
    if file:
        logger.info(f"[Plate] file={file.filename} cls={cls}")
        path = save_upload(file)
    elif url:
        path = download_url(url)
    else:
        raise HTTPException(status_code=400, detail="Must provide 'file' or 'url'")

    try:
        # 预加载图片到内存，后续各步骤复用，避免重复读图
        import cv2
        import numpy as np
        img = cv2.imread(path)

        result = run_plate_ocr(path, image=img, cls=cls)
        logger.info(f"[Plate] done, text={result.get('text', '')}, model={result.get('model', '')}")

        # 基于 OCR 检测框裁剪车牌区域后识别颜色，排除蓝天/绿树等背景干扰
        plate_img = img  # 默认用整图
        items = result.get("items", [])
        if items and "box" in items[0]:
            box = items[0]["box"]
            xs = [box[i] for i in range(0, len(box), 2)]
            ys = [box[i] for i in range(1, len(box), 2)]
            x1, x2 = max(0, int(min(xs))), min(img.shape[1], int(max(xs)))
            y1, y2 = max(0, int(min(ys))), min(img.shape[0], int(max(ys)))
            if x2 > x1 and y2 > y1:
                plate_img = img[y1:y2, x1:x2]

        color = detect_plate_color(path, image=plate_img)
        result["plate_color"] = color
        return result
    finally:
        os.remove(path)


@app.post("/structure")
@app.post("/structure/url")
def structure_analysis(
        file: UploadFile = File(None),
        url: str = Query(None, description="图片 URL，与 file 二选一"),
        extract_table: bool = Query(True, description="是否提取表格，纯文档设为 false 可提速 3-5x"),
):
    """
    PP-Structure 文档结构分析
    返回: 表格(html)、标题、段落等结构化信息
    """
    if file:
        logger.info(f"[Structure] file={file.filename}")
        path = save_upload(file)
    elif url:
        path = download_url(url)
    else:
        raise HTTPException(status_code=400, detail="Must provide 'file' or 'url'")

    # ----- 文档格式转换链 -----
    # 收集所有需要清理的临时文件
    temp_files = [path]
    suffix = os.path.splitext(path)[-1].lower()

    if suffix in DOCUMENT_SUFFIXES:
        logger.info(f"[Structure] converting document to images: {path}")
        try:
            page_images = convert_document_to_images(path)
            temp_files.extend(page_images)
            # 用第一页图片替代原始路径（后续逻辑用列表处理）
            image_paths = page_images
        except Exception as e:
            for f in temp_files:
                try:
                    os.remove(f)
                except OSError:
                    pass
            raise HTTPException(
                status_code=500,
                detail=f"Document conversion failed: {str(e)}"
            )
    else:
        image_paths = [path]

    # ----- 文档结构分析（逐页处理） -----
    all_items = []
    engine = get_structure_engine(extract_table=extract_table)

    try:
        for img_path in image_paths:
            logger.info(f"[Structure] analyzing: {img_path}")
            result = engine(img_path)

            for item in result:
                entry = {"type": item.get("type", "")}
                if "res" in item:
                    res = item["res"]
                    if isinstance(res, dict):
                        # 表格结果
                        entry["html"] = res.get("html", "")
                        if "cell" in res:
                            entry["cells"] = res["cell"]
                    elif isinstance(res, list):
                        # OCR 结果（标题/段落）
                        # PPStructure ≤2.8: [[bbox, (text, conf)], ...]
                        # PPStructure ≥2.9: [{text: ..., confidence: ..., bbox: ...}, ...]
                        texts = []
                        for r in res:
                            if isinstance(r, dict):
                                txt = r.get("text") or r.get("content", "")
                                if txt:
                                    texts.append(str(txt))
                            elif isinstance(r, (list, tuple)) and len(r) >= 2:
                                # 旧格式: [[bbox], (text, conf)]
                                txt_data = r[1]
                                if isinstance(txt_data, (list, tuple)) and len(txt_data) > 0:
                                    txt = str(txt_data[0])
                                    if txt:
                                        texts.append(txt)
                        entry["text"] = " ".join(texts)
                if "img" in item:
                    entry["bbox"] = item.get("bbox", [])
                all_items.append(entry)

        return {
            "success": True,
            "count": len(all_items),
            "items": all_items,
        }
    except Exception as e:
        import traceback
        tb = traceback.format_exc()
        logger.error(f"[Structure] PPStructure analysis failed:\n{tb}")
        raise HTTPException(
            status_code=500,
            detail=f"Structure analysis failed: {str(e)}"
        )
    finally:
        logger.info(f"[Structure] cleaning up {len(temp_files)} temp files")
        for f in temp_files:
            try:
                os.remove(f)
            except OSError:
                pass


# ============================================================
# 文档格式转换独立接口（doc/docx -> PDF）
# ============================================================


@app.post("/convert/docx-to-pdf")
@app.post("/convert/docx-to-pdf/url")
def convert_docx_to_pdf(
        file: UploadFile = File(None),
        url: str = Query(None, description="Word 文档 URL，与 file 二选一"),
        background_tasks: BackgroundTasks = None,
):
    """
    doc/docx -> PDF 转换
    上传 Word 文档或提供 URL，返回 PDF 文件流
    """
    docx_path = None
    pdf_path = None
    downloaded = False

    try:
        # 通过 URL 下载
        if url:
            logger.info(f"[Convert] docx-to-pdf from URL: {url}")
            if not url.startswith(("http://", "https://")):
                raise HTTPException(status_code=400, detail="Invalid URL")

            suffix = os.path.splitext(url.split("?")[0])[-1].lower()
            if suffix not in {".doc", ".docx"}:
                suffix = ".docx"

            tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
            urllib.request.urlretrieve(url, tmp.name)
            docx_path = tmp.name
            tmp.close()
            downloaded = True

        # 上传文件
        elif file:
            if not file.filename:
                raise HTTPException(status_code=400, detail="Filename is required")

            suffix = os.path.splitext(file.filename)[-1].lower()
            if suffix not in {".doc", ".docx"}:
                raise HTTPException(
                    status_code=400,
                    detail=f"Unsupported format: {suffix}. Only .doc / .docx allowed"
                )

            logger.info(f"[Convert] docx-to-pdf: {file.filename}")
            tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
            shutil.copyfileobj(file.file, tmp)
            docx_path = tmp.name
            tmp.close()

        else:
            raise HTTPException(status_code=400, detail="Must provide 'file' or 'url'")

        pdf_path = _convert_doc_to_pdf(docx_path)
        logger.info(f"[Convert] done -> {pdf_path}")

        # 后台清理（响应完成后执行）
        if background_tasks:
            background_tasks.add_task(_cleanup_temp_files, docx_path, pdf_path)

        pdf_filename = os.path.splitext(
            file.filename if file else url.split("?")[0]
        )[0] + ".pdf"
        return FileResponse(
            pdf_path,
            media_type="application/pdf",
            filename=pdf_filename,
            background=background_tasks,
        )
    except HTTPException:
        raise
    except Exception as e:
        _cleanup_temp_files(docx_path, pdf_path)
        raise HTTPException(
            status_code=500,
            detail=f"Document conversion failed: {str(e)}"
        )


def _cleanup_temp_files(*paths: str | None):
    """后台清理临时文件"""
    for p in paths:
        if p and os.path.exists(p):
            try:
                # 如果是临时目录（LibreOffice 的 out_dir），整个目录删掉
                p_dir = os.path.dirname(p)
                os.remove(p)
                if p_dir and p_dir.startswith(tempfile.gettempdir()):
                    try:
                        os.rmdir(p_dir)
                    except OSError:
                        pass
            except OSError:
                pass


# ============================================================
# 启动
# ============================================================
if __name__ == "__main__":
    # 启动时记录
    logger.info(f"Starting PaddleOCR server on 0.0.0.0:8000")
    logger.info(f"Logs directory: {LOG_DIR}")

    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8000,
        log_level="info",
    )
