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

app = FastAPI(title="PaddleOCR Multi-Model Server", version="1.0.0")

# ============================================================
# 模型全局实例（首次请求时懒加载）
# ============================================================
ocr_ch: PaddleOCR | None = None      # PP-OCRv4 中文
ocr_plate: PaddleOCR | None = None   # 车牌识别
structure_engine: PPStructure | None = None  # PP-Structure


def get_ocr_ch() -> PaddleOCR:
    global ocr_ch
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
        )
    return ocr_plate


def get_structure_engine() -> PPStructure:
    global structure_engine
    if structure_engine is None:
        structure_engine = PPStructure(
            show_log=False,
            lang="ch",
            use_gpu=False,     # GPU 时改 True
            ocr=True,
        )
    return structure_engine


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


def detect_plate_color(image_path: str) -> dict:
    """
    通过图片主色调分析车牌颜色
    返回: {"type": "blue", "name": "蓝牌", "desc": "小型汽车"}
    """
    try:
        import cv2
        import numpy as np

        img = cv2.imread(image_path)
        if img is None:
            return {"type": "unknown", "name": "未知", "desc": ""}

        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)

        # 定义各颜色 HSV 范围
        color_ranges = {
            "blue": ([100, 43, 46], [130, 255, 255]),
            "green": ([60, 43, 46], [90, 255, 255]),
            "yellow": ([20, 43, 46], [40, 255, 255]),
            "white": ([0, 0, 200], [180, 30, 255]),
            "black": ([0, 0, 0], [180, 255, 46]),
        }

        max_pixels = 0
        detected_color = "unknown"
        for color_name, (lower, upper) in color_ranges.items():
            mask = cv2.inRange(hsv, np.array(lower), np.array(upper))
            pixels = cv2.countNonZero(mask)
            if pixels > max_pixels:
                max_pixels = pixels
                detected_color = color_name

        info = PLATE_COLORS.get(detected_color, {"name": "未知", "desc": ""})
        return {
            "type": detected_color,
            "name": info["name"],
            "desc": info["desc"],
            "confidence": round(max_pixels / (img.shape[0] * img.shape[1]), 4),
        }
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
    """保存上传文件到临时目录，返回路径"""
    if not file.filename:
        raise HTTPException(status_code=400, detail="Filename is required")

    suffix = os.path.splitext(file.filename)[-1].lower()
    if suffix not in ALLOWED_SUFFIXES:
        raise HTTPException(status_code=400, detail=f"Unsupported format: {suffix}")

    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    shutil.copyfileobj(file.file, tmp)
    tmp.close()
    return tmp.name


def download_url(url: str) -> str:
    """下载 URL 图片到临时目录，返回路径"""
    if not url.startswith(("http://", "https://")):
        raise HTTPException(status_code=400, detail="Invalid URL")

    suffix = os.path.splitext(url.split("?")[0])[-1].lower()
    if suffix not in ALLOWED_SUFFIXES:
        suffix = ".jpg"  # 默认

    logger.info(f"Downloading image from URL: {url}")
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    try:
        urllib.request.urlretrieve(url, tmp.name)
        tmp.close()
        return tmp.name
    except Exception as e:
        os.remove(tmp.name)
        raise HTTPException(status_code=400, detail=f"Failed to download URL: {str(e)}")


def run_ocr(ocr: PaddleOCR, image_path: str) -> dict:
    """执行 OCR 识别，返回统一格式"""
    try:
        result = ocr.ocr(image_path, cls=True)
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

def run_plate_ocr(image_path: str) -> dict:
    """
    车牌识别：PaddleOCR 识别 + 省份简称补全
    """
    import re as _re

    # 第一轮：全图 OCR
    result = run_ocr(get_ocr_plate(), image_path)
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
        province = _try_recover_province(image_path, first_line, result)
        if province:
            full_plate = province + plate_like
            result["text"] = full_plate
            if result.get("items"):
                result["items"][0]["text"] = full_plate
            logger.info(f"[Plate] province fixed: {first_line} -> {full_plate}")

    result["model"] = "paddleocr"
    return result


def _try_recover_province(image_path: str, first_line: str, result: dict) -> str | None:
    """
    尝试从图片中恢复省份简称。
    返回省份字符，或 None。
    """
    try:
        import cv2
        import numpy as np

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

        # 策略 3：全图二值化后重识别
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        _, binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
        binary_path = image_path + ".binary.jpg"
        cv2.imwrite(binary_path, binary)
        try:
            binary_result = get_ocr_plate().ocr(binary_path, cls=True)
            if binary_result and binary_result[0]:
                for item in binary_result[0]:
                    t = item[1][0].strip()
                    for p in PLATE_PROVINCES:
                        if p in t:
                            return p
        finally:
            os.remove(binary_path)

        return None

    except Exception as e:
        logger.warning(f"[Plate] province recovery failed: {e}")
        return None


def _ocr_province_region(region, label: str) -> str | None:
    """
    对指定区域做图像预处理后识别省份字符。
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
        tmp_path = f"/tmp/plate_province_{name}.jpg"
        cv2.imwrite(tmp_path, processed)
        try:
            ocr_result = get_ocr_plate().ocr(tmp_path, cls=True)
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
        finally:
            try:
                os.remove(tmp_path)
            except OSError:
                pass

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
async def ocr_chinese(
        file: UploadFile = File(None),
        url: str = Query(None, description="图片 URL，与 file 二选一"),
):
    """PP-OCRv4 中文通用文字识别"""
    if file:
        logger.info(f"[PP-OCRv4] file={file.filename}")
        path = save_upload(file)
    elif url:
        path = download_url(url)
    else:
        raise HTTPException(status_code=400, detail="Must provide 'file' or 'url'")

    try:
        result = run_ocr(get_ocr_ch(), path)
        logger.info(f"[PP-OCRv4] done, count={result.get('count', 0)}")
        return result
    finally:
        os.remove(path)


@app.post("/ocr/plate")
@app.post("/ocr/plate/url")
async def ocr_license_plate(
        file: UploadFile = File(None),
        url: str = Query(None, description="图片 URL，与 file 二选一"),
):
    """车牌文字识别（优先 PaddleX 专用模型，回退 PaddleOCR）"""
    if file:
        logger.info(f"[Plate] file={file.filename}")
        path = save_upload(file)
    elif url:
        path = download_url(url)
    else:
        raise HTTPException(status_code=400, detail="Must provide 'file' or 'url'")

    try:
        result = run_plate_ocr(path)
        logger.info(f"[Plate] done, text={result.get('text', '')}, model={result.get('model', '')}")
        # 同时返回颜色
        color = detect_plate_color(path)
        result["plate_color"] = color
        return result
    finally:
        os.remove(path)


@app.post("/structure")
@app.post("/structure/url")
async def structure_analysis(
        file: UploadFile = File(None),
        url: str = Query(None, description="图片 URL，与 file 二选一"),
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
    engine = get_structure_engine()

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
async def convert_docx_to_pdf(
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