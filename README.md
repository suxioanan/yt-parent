# yt-parent

Java 17 + Spring Boot 3.2 多模块项目，提供 OCR 识别和第三方 SSO 登录能力。

## 版本信息

| 项 | 值 |
|---|-----|
| Java | 17 |
| Spring Boot | 3.2.0 |
| GroupId | `com.yt.third` |
| Version | `1.0.0` |
| 构建工具 | Maven |

## 项目结构

```
yt-parent/
├── pom.xml                              # 父 POM，统一版本管理
│
├── ocr-spring-boot-starter/             # OCR 识别 Starter（PaddleOCR）
│
├── third-sso-starter/                   # SSO 聚合模块（pom）
│   ├── sso-common/                      # 公共接口 + 数据模型
│   ├── sso-dingtalk-starter/            # 钉钉 SSO
│   ├── sso-wechat-starter/              # 企业微信 SSO
│   ├── sso-feishu-starter/              # 飞书 SSO
│   └── sso-all-starter/                 # 全家桶聚合（依赖以上三个）
│
└── sso-test/                            # SSO HTTP 测试应用
```

---

## 一、SSO 公共模块 (`sso-common`)

定义统一接口和数据模型，各平台模块基于它实现。

### SsoService 接口

```java
public interface SsoService {

    /** 生成扫码登录 URL */
    String getSSOScanUri();

    /** 通过 OAuth 授权码获取用户信息 */
    SsoUser getUserInfoByCode(String authCode);

    /** 根据平台用户 ID 获取用户详情 */
    SsoUser getUserByUserId(String userId);
}
```

### SsoUser 通用用户模型

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | String | 平台用户 ID |
| `name` | String | 姓名 |
| `mobile` | String | 手机号 |
| `email` | String | 邮箱 |
| `gender` | String | 性别（男/女/保密） |
| `avatar` | String | 头像 URL |
| `unionId` | String | 统一 ID（钉钉/飞书有，微信为 null） |
| `openId` | String | 第三方应用内唯一标识 |
| `address` | String | 地址 |
| `jobNumber` | String | 工号 |
| `source` | String | 来源平台：`dingtalk` / `wechat` / `feishu` |
| `extra` | Map | 平台特有字段兜底，不会丢数据 |

### LoginState

OAuth state 参数结构，用于生成扫码 URL 时防重放：

```java
public class LoginState {
    private String from;     // system / customer
    private Long bizId;      // 业务 ID
    private Long timestamp;  // 时间戳
}
```

---

## 二、钉钉 SSO (`sso-dingtalk-starter`)

### Maven

```xml
<dependency>
    <groupId>com.yt.third</groupId>
    <artifactId>sso-dingtalk-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 配置

```yaml
sso:
  dingtalk:
    client-id: your-app-key
    client-secret: your-app-secret
    agent-id: 123456
    corp-id: your-corp-id
    redirect-uri: https://your-app.com/callback
```

### 使用

```java
@Autowired
private DingtalkSSOService dingtalkSSOService;

// 1. 获取扫码登录 URL
String url = dingtalkSSOService.getSSOScanUri();

// 2. 授权码登录
SsoUser user = dingtalkSSOService.getUserInfoByCode("authCode");

// 3. 根据 userId 查用户
SsoUser user = dingtalkSSOService.getUserByUserId("userId");

// 4. [钉钉独有] unionId 换 userId
String userId = dingtalkSSOService.getUserIdByUnionId("unionId");
```

### 接口扩展

`DingtalkSSOService extends SsoService`，额外提供：

```java
String getUserIdByUnionId(String unionId);
```

### 实现方式

- `getUserInfoByCode`: 使用**阿里云钉钉 SDK**（`com.aliyun:dingtalk:2.1.98`）完成 OAuth 流程
- `getUserByUserId` / `getUserIdByUnionId`: 使用 HTTP API，token 内置缓存（30 分钟）

---

## 三、企业微信 SSO (`sso-wechat-starter`)

### Maven

```xml
<dependency>
    <groupId>com.yt.third</groupId>
    <artifactId>sso-wechat-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 配置

```yaml
sso:
  wechat:
    client-id: your-corp-id
    secret: your-corp-secret
    agent-id: 1000002
    redirect-uri: https://your-app.com/callback
```

### 使用

```java
@Autowired
private WechatSSOService wechatSSOService;

// 1. 获取扫码登录 URL
String url = wechatSSOService.getSSOScanUri();

// 2. 授权码登录
SsoUser user = wechatSSOService.getUserInfoByCode("authCode");

// 3. 根据 userId 查用户
SsoUser user = wechatSSOService.getUserByUserId("userId");
```

### 接口

`WechatSSOService extends SsoService`，无额外方法。

### 实现方式

全部使用 HTTP API（hutool），token 内置缓存（30 分钟）。

---

## 四、飞书 SSO (`sso-feishu-starter`)

### Maven

```xml
<dependency>
    <groupId>com.yt.third</groupId>
    <artifactId>sso-feishu-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 配置

```yaml
sso:
  feishu:
    client-id: cli_xxx        # App ID
    secret: xxx               # App Secret
    redirect-uri: https://your-app.com/callback
```

### 使用

```java
@Autowired
private FeishuSSOService feishuSSOService;

// 1. 获取扫码登录 URL
String url = feishuSSOService.getSSOScanUri();

// 2. 授权码登录
SsoUser user = feishuSSOService.getUserInfoByCode("authCode");

// 3. 根据 userId 查用户
SsoUser user = feishuSSOService.getUserByUserId("userId");
```

### OAuth 流程

```
getUserInfoByCode(code):
  ① app_id + app_secret → app_access_token（缓存 2h）
  ② code + app_token → user_access_token
  ③ user_token → /authen/v1/user_info → SsoUser

getUserByUserId(userId):
  ① app_id + app_secret → tenant_access_token（缓存 2h）
  ② tenant_token → /contact/v3/users/{userId} → SsoUser
```

### 接口

`FeishuSSOService extends SsoService`，无额外方法。

### 实现方式

全部使用 HTTP API（hutool），双 token（app + tenant）独立缓存。

---

## 五、全家桶聚合 (`sso-all-starter`)

一键引入钉钉 + 企业微信 + 飞书：

```xml
<dependency>
    <groupId>com.yt.third</groupId>
    <artifactId>sso-all-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

配置三个平台的 YAML 后，三个 Service 均可注入使用。

---

## 六、SSO 测试应用 (`sso-test`)

可直接启动的 Spring Boot 应用，用于通过 HTTP 接口测试 SSO 功能。

### 启动

```bash
cd yt-parent
mvn spring-boot:run -pl sso-test
```

默认端口 `8089`，配置 `sso-test/src/main/resources/application.yml`。

### 测试接口

| 平台 | 接口 | 说明 |
|------|------|------|
| 钉钉 | `GET /test/dingtalk/url` | 获取扫码 URL |
| 钉钉 | `GET /test/dingtalk/login?authCode=xxx` | 授权码登录 |
| 钉钉 | `GET /test/dingtalk/user/{userId}` | 查用户 |
| 钉钉 | `GET /test/dingtalk/unionid/{unionId}` | unionId → userId |
| 微信 | `GET /test/wechat/url` | 获取扫码 URL |
| 微信 | `GET /test/wechat/login?authCode=xxx` | 授权码登录 |
| 微信 | `GET /test/wechat/user/{userId}` | 查用户 |
| 飞书 | `GET /test/feishu/url` | 获取扫码 URL |
| 飞书 | `GET /test/feishu/login?authCode=xxx` | 授权码登录 |
| 飞书 | `GET /test/feishu/user/{userId}` | 查用户 |

返回格式：

```json
{"success": true, "data": { ... }}
{"success": false, "error": "错误信息"}
```

---

## 七、OCR 模块 (`ocr-spring-boot-starter`)

PaddleOCR Spring Boot Starter，提供中英文文字识别、车牌识别、文档结构分析、PDF 转换等能力。

---

## 启动配置参考

```yaml
# 三个平台只需配需要的即可
sso:
  dingtalk:
    client-id: dingxxx
    client-secret: xxx
    agent-id: 3974835501
    corp-id: dingxxx
    redirect-uri: http://your-app.com/callback
  wechat:
    client-id: wwxxx
    secret: xxx
    agent-id: 1000002
    redirect-uri: http://your-app.com/callback
  feishu:
    client-id: cli_xxx
    secret: xxx
    redirect-uri: http://your-app.com/callback
```

---

## 构建命令

```bash
# 全量编译
mvn clean compile

# 全量测试
mvn test

# 仅编译 SSO 模块
mvn compile -pl third-sso-starter

# 启动测试应用
mvn spring-boot:run -pl sso-test
```
