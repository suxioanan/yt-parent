# PaddleOCR 服务

## 目录结构

```
docker/
├── README.md                  ← 本文档
├── build/                     ← 镜像构建文件
│   ├── Dockerfile             ← 基于 PaddlePaddle 官方镜像
│   ├── build.sh               ← 构建脚本（CPU/GPU）
│   ├── download_models.py     ← 构建时自动下载模型
│   ├── DemoUtil.java          ← Java 调用工具类
│   └── .gitignore
├── docker-compose.yml         ← 容器编排（挂载配置）
├── ocr_server.py              ← 多模型 API 服务（运行时挂载，可热更新）
├── requirements.txt           ← Python 依赖
├── models/                    ← 模型文件（宿主机管理，镜像更新不丢失）
├── logs/                      ← 日志目录（运行时挂载）
└── data/                      ← 数据目录（运行时挂载）
```

---

## 部署

### 方式一：一键部署（推荐）

```bash
cd docker
docker compose up -d --build
```

自动完成：构建镜像 → 下载模型 → 启动容器

### 方式二：分步构建

```bash
cd docker/build

# CPU 版本
bash build.sh

# GPU 版本（需 NVIDIA GPU + nvidia-container-toolkit）
bash build.sh gpu

# 启动
cd ..
docker compose up -d
```

### 导入已有镜像（无需构建）

```bash
docker load -i paddleocr-server.tar
docker compose up -d
```

---

## 验证

```bash
# 健康检查
curl http://localhost:8000/

# 中文 OCR（PP-OCRv4）
curl -s -X POST -F "file=@test.jpg" http://localhost:8000/ocr/ch

# 车牌识别
curl -s -X POST -F "file=@plate.jpg" http://localhost:8000/ocr/plate

# 文档结构分析（PP-Structure）
curl -s -X POST -F "file=@doc.jpg" http://localhost:8000/structure

# URL 传参（服务端下载）
curl -s -X POST "http://localhost:8000/ocr/ch/url?url=http://image-server/xxx.jpg"
```

---

## API 文档

| 端点 | 功能 | 模型 |
|------|------|------|
| `POST /ocr/ch` | 中文文字识别 | PP-OCRv4 |
| `POST /ocr/ch/url` | 中文 OCR（URL 传参） | PP-OCRv4 |
| `POST /ocr/plate` | 车牌文字 + 颜色识别 | PP-OCRv4（车牌参数） |
| `POST /ocr/plate/url` | 车牌识别（URL 传参） | PP-OCRv4（车牌参数） |
| `POST /structure` | 文档结构分析 | PP-Structure |
| `POST /structure/url` | 文档分析（URL 传参） | PP-Structure |
| `GET /` | 健康检查 | - |

---

## Java 调用

### 复制 DemoUtil.java

将 `build/DemoUtil.java` 复制到项目的 `utils` 包下。

### 使用示例

```java
// 方式一：本地 File
OcrResult r1 = DemoUtil.ocrChinese(new File("/data/test.jpg"));

// 方式二：网络 URL（服务端下载）
OcrResult r2 = DemoUtil.ocrChineseByUrl("http://内网地址/xxx.jpg");

// 方式三：Spring MultipartFile（控制器接收）
@PostMapping("/upload")
public OcrResult upload(@RequestParam("file") MultipartFile file) throws IOException {
    return DemoUtil.ocrChinese(file);
}

// 车牌识别（含颜色）
OcrResult plate = DemoUtil.ocrPlateByUrl("http://内网地址/车牌.jpg");
System.out.println(plate.getText());         // 粤B12345F
System.out.println(plate.getPlateColor().getName());  // 蓝牌
System.out.println(plate.getPlateColor().getDesc());  // 小型汽车

// 文档结构分析
StructureResult doc = DemoUtil.structureAnalysis("表格.png");
```

### 身份证解析

```java
OcrResult r = DemoUtil.ocrChineseByUrl("http://内网地址/身份证.jpg");
IdCardResult card = DemoUtil.parseIdCard(r);

card.getName();       // 尹涛
card.getIdNumber();   // 42122119910120185X
card.getAddress();    // 湖北省嘉鱼县高铁岭镇八斗角村一组9号
card.getGender();     // 男
card.getEthnicity();  // 汉
card.getBirthDate();  // 1991-01-20
```

### 返回值结构

```
OcrResult
  ├── isSuccess()     → true/false
  ├── getText()       → 全部识别文字
  ├── getCount()      → 识别行数
  ├── getPlateColor() → 车牌颜色（仅 plate 接口）
  │    ├── getName()  → "蓝牌"/"黄牌"/"绿牌"/"白牌"/"黑牌"
  │    ├── getDesc()  → "小型汽车"/"新能源汽车"等
  │    └── getConfidence() → 颜色置信度
  └── getItems()      → List<OcrItem>
       ├── getText()        → 单行文字
       ├── getConfidence()  → 置信度 0~1
       └── getBox()         → double[8] 坐标

StructureResult
  ├── isSuccess()   → true/false
  ├── getCount()    → 元素数量
  └── getItems()    → List<StructureItem>
       ├── getType()  → "table"/"text"/"title"
       ├── getHtml()  → 表格 HTML
       └── getText()  → 文字内容

IdCardResult
  ├── getName()       → 姓名
  ├── getIdNumber()   → 身份证号
  ├── getAddress()    → 住址
  ├── getGender()     → 性别
  ├── getEthnicity()  → 民族
  └── getBirthDate()  → 出生日期
```

---

## 切换 CPU / GPU

### Dockerfile

```dockerfile
# CPU（默认）
FROM paddlepaddle/paddle:2.6.1

# GPU（取消注释上行，注释掉上行）
# FROM paddlepaddle/paddle:2.6.1-gpu-cuda11.8-cudnn8.6-trt8.5
```

### docker-compose.yml

```yaml
# GPU 支持（取消注释）
# deploy:
#   resources:
#     reservations:
#       devices:
#         - driver: nvidia
#           count: all
#           capabilities: [gpu]
```

构建 GPU 版：

```bash
cd build
bash build.sh gpu
# 或
docker build -t paddleocr-server:gpu .

# 运行
docker run -d --name ocr -p 8000:8000 --gpus all paddleocr-server:gpu
```

---

## 热更新

挂载的宿主机文件修改后，只需 `docker compose restart`，无需重新构建：

| 修改的文件 | 执行命令 |
|-----------|---------|
| `ocr_server.py` | `docker compose restart` |
| `requirements.txt` | `docker compose up -d --build` |
| 其他代码 | `docker compose up -d --build` |

---

## 查看日志

```bash
docker compose logs -f              # 实时日志
docker compose logs --tail 100      # 最近 100 条
ls -la logs/                        # 宿主机日志文件（自动轮转）
```
