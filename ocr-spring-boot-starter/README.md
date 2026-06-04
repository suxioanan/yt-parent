# ocr-spring-boot-starter

Spring Boot 3.x 快速集成 PaddleOCR 的 Starter，支持中文文字识别、车牌识别、文档分析和身份证信息解析。

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.yt.ocr</groupId>
    <artifactId>ocr-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. yml 配置

```yaml
ocr:
  base-url: http://172.16.41.107:8000    # PaddleOCR 服务地址
  timeout: 120000                         # 请求超时(ms)，默认 120000
  endpoints:
    chinese: /ocr/ch                      # 中文识别端点，默认 /ocr/ch
    plate: /ocr/plate                     # 车牌识别端点，默认 /ocr/plate
    structure: /structure                 # 文档分析端点，默认 /structure
    wordtopdf: /convert/docx-to-pdf       # 文档转换PDF，默认 /structure
```

仅 `base-url` 为必填项，其余均有默认值。

### 3. 注入使用

```java
@RestController
public class OcrController {

    @Autowired
    private OcrChineseService chineseService;   // 中文文字识别
    @Autowired
    private OcrPlateService plateService;       // 车牌识别
    @Autowired
    private StructureService structureService;  // 文档结构分析
    @Autowired
    private IdCardService idCardService;        // 身份证识别
}
```

## 构建安装

```bash
# 进入项目目录
cd ocr-spring-boot-starter

# 编译并安装到本地仓库
mvn clean install

# 跳过测试（可选）
mvn clean install -DskipTests
```

执行后 jar 会安装到本地 Maven 仓库，其他项目即可通过依赖坐标引入。

## Service 说明

### 统一接口

每个 Service 均实现 `OcrOperations<T>` 接口，提供四种调用方式：

| 方法 | 参数 | 说明 |
|------|------|------|
| `ocr(File file)` | 本地文件对象 | 直接上传本地文件 |
| `ocr(MultipartFile file)` | Spring 文件上传对象 | 适用于 Controller 接收上传 |
| `ocrByUrl(String imageUrl)` | 图片网络 URL | 服务端自行下载 |
| `ocrByPath(String imagePath)` | 本地文件路径字符串 | 等同于 new File(path) |

### OcrChineseService — 中文文字识别

```java
OcrResult result = chineseService.ocr(new File("/path/to/image.jpg"));
// result.getText()      → 识别出的完整文本
// result.getItems()     → 逐行结果（含置信度、坐标框）
// result.getCount()     → 识别行数
```

### OcrPlateService — 车牌识别

```java
OcrResult result = plateService.ocrByUrl("http://example.com/car.jpg");
// 同上，返回车牌号文本及位置信息
```

### StructureService — 文档结构分析

```java
StructureResult result = structureService.ocr(multipartFile);
// result.getItems()     → 文档结构片段列表（type / html / text）
```

### IdCardService — 身份证识别

继承 `OcrChineseService`，额外提供身份证结构化解析能力。

**分步调用：**

```java
// 先识别
OcrResult ocrResult = idCardService.ocr(file);
// 再解析
IdCardResult card = idCardService.parseIdCard(ocrResult);
```

**便捷方法（一步到位）：**

```java
IdCardResult card = idCardService.parseIdCard(new File("/path/to/idcard.jpg"));
IdCardResult card = idCardService.parseIdCard(multipartFile);
IdCardResult card = idCardService.parseIdCardByUrl("http://example.com/idcard.jpg");
IdCardResult card = idCardService.parseIdCardByPath("/path/to/idcard.jpg");
```

**IdCardResult 字段：**

| 字段 | 说明 |
|------|------|
| `name` | 姓名 |
| `idNumber` | 身份证号 |
| `gender` | 性别 |
| `ethnicity` | 民族 |
| `birthDate` | 出生日期 |
| `address` | 住址 |

## 模型说明

### OcrResult

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 识别是否成功 |
| `text` | String | 完整识别文本 |
| `count` | int | 识别行数 |
| `items` | List\<OcrItem\> | 逐行结果 |

### OcrItem

| 字段 | 类型 | 说明 |
|------|------|------|
| `text` | String | 本行文本 |
| `confidence` | double | 置信度 |
| `box` | double[] | 文本框坐标 |

### StructureResult

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 分析是否成功 |
| `count` | int | 结构片段数 |
| `items` | List\<StructureItem\> | 结构片段列表 |

### StructureItem

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | String | 片段类型 |
| `html` | String | HTML 格式内容 |
| `text` | String | 纯文本内容 |

## 类关系

```
OcrOperations<T>                  ← 统一接口
  ├── OcrChineseService           → /ocr/ch  返回 OcrResult
  │     └── IdCardService         → 继承，追加 parseIdCard()
  ├── OcrPlateService             → /ocr/plate  返回 OcrResult
  └── StructureService            → /structure  返回 StructureResult
```

## 自动装配

通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 实现 Spring Boot 3.x 自动扫描，无需 `@ComponentScan` 或 `spring.factories`。

内部自动创建以下 Bean（均支持 `@ConditionalOnMissingBean` 覆盖）：

- `RestTemplate` — 带超时配置
- `ObjectMapper` — JSON 解析
- `OcrClient` — HTTP 请求封装
- `OcrChineseService` — 标记 `@Primary`
- `OcrPlateService`
- `StructureService`
- `IdCardService`
