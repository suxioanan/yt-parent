# third-pay-starter

微信支付 + 支付宝 Spring Boot Starter，提供统一的扫码支付、条码支付、退款接口。

## 项目结构

```
third-pay-starter/
├── pay-common/                     # 统一接口 + 通用模型
│   └── com.yt.pay/
│       ├── PayService.java         # 7 个方法：generateQrCode / microPay / queryOrder / closeOrder / refund / refundQuery / handleNotify
│       └── model/
│           ├── PayRequest.java     # 下单请求
│           ├── MicroPayRequest.java# 条码支付请求
│           ├── PayResult.java      # 支付结果
│           ├── PayStatus.java      # SUCCESS / REFUND / CLOSED / NOTPAY / USERPAYING
│           ├── RefundRequest.java  # 退款请求（支持全额/部分）
│           ├── RefundResult.java   # 退款结果
│           └── PayNotifyResult.java# 回调通知结果
│
├── pay-wechat-starter/             # 微信支付（SDK: wechatpay-java 0.2.17）
│   └── com.yt.pay.wechat/
│       ├── WechatPayService.java
│       ├── WechatPayServiceImpl.java
│       └── config/
│           ├── WechatPayProperties.java   # @ConfigurationProperties("pay.wechat")
│           └── WechatPayAutoConfiguration.java
│
├── pay-alipay-starter/             # 支付宝（SDK: alipay-sdk-java 4.40.837）
│   └── com.yt.pay.alipay/
│       ├── AlipayService.java
│       ├── AlipayServiceImpl.java
│       └── config/
│           ├── AlipayProperties.java      # @ConfigurationProperties("pay.alipay")
│           └── AlipayAutoConfiguration.java
│
└── pay-all-starter/                # 全家桶聚合
    └── 依赖 pay-wechat-starter + pay-alipay-starter
```

---

## 一、微信支付

### 1.1 前置条件

| 条件 | 说明 |
|------|------|
| 微信商户号 | 在 [微信支付商户平台](https://pay.weixin.qq.com/) 注册 |
| 商户 API 证书 | 商户平台 → 账户中心 → API 安全 → 申请 API 证书 |
| API V3 密钥 | 商户平台 → 账户中心 → API 安全 → 设置 APIv3 密钥（32 位随机字符串） |
| 通过微信认证的公众号/小程序 | Native 扫码支付不需要，JSAPI/付款码支付需要 |

### 1.2 配置项获取

#### `merchant-id`（商户号）

1. 登录 [微信支付商户平台](https://pay.weixin.qq.com/)
2. 首页右上角 → 账户信息 → **商户号**（10 位数字）
3. 配置示例：`"1234567890"`

#### `merchant-serial-number`（商户 API 证书序列号）

1. 商户平台 → 账户中心 → **API 安全**
2. 点击 **申请 API 证书**，下载后得到一个压缩包，内含 `apiclient_cert.pem` 等文件
3. 在同样的 API 安全页面 → **API 证书** → 查看证书序列号（40 位十六进制字符串）
4. 配置示例：`"7CA1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9"`

#### `private-key-path`（商户私钥路径）

1. 证书压缩包中的 `apiclient_key.pem` 文件
2. 将其放在服务器安全目录，如 `/etc/wechat/apiclient_key.pem`
3. 配置示例：`"/etc/wechat/apiclient_key.pem"`

> **注意**：不要将 `.pem` 文件打包进 JAR 或提交到 Git！

#### `api-v3-key`（API V3 密钥）

1. 商户平台 → 账户中心 → **API 安全** → **APIv3 密钥** → 设置
2. 自行生成一个 **32 位**随机字符串（字母+数字）
3. 设置后请妥善保存，微信只显示一次
4. 配置示例：`"a1b2c3d4e5f6a7b8c9d0e1f2A3B4C5D"`

#### `notify-url`（支付结果回调地址）

1. 必须为 **HTTPS** 公网可访问地址
2. 在应用的 Controller 中自建端点（参考下方「回调处理」）
3. 配置示例：`"https://your-domain.com/pay/wechat/notify"`

### 1.3 使用方式

```java
@Autowired
private WechatPayService wechatPayService;

// 1. 手机扫码支付 — 生成二维码链接
PayRequest req = new PayRequest();
req.setOutTradeNo("ORDER-20240601-001");
req.setTotalAmount(new BigDecimal("0.01"));
req.setDescription("测试商品");
String codeUrl = wechatPayService.generateQrCode(req);
// codeUrl 是一个 weixin://wxpay/bizpayurl?pr=xxx 的链接
// 前端将其渲染为二维码，用户用微信扫码支付

// 2. 查询订单
PayResult result = wechatPayService.queryOrder("ORDER-20240601-001");
if (result.getStatus() == PayStatus.SUCCESS) {
    // 支付成功，更新业务订单状态
}

// 3. 关单
wechatPayService.closeOrder("ORDER-20240601-001");

// 4. 全额退款
RefundRequest refundReq = RefundRequest.builder()
        .outTradeNo("ORDER-20240601-001")
        .outRefundNo("REFUND-20240601-001")
        .totalAmount(new BigDecimal("0.01"))
        .refundAmount(new BigDecimal("0.01"))
        .reason("用户申请退款")
        .build();
RefundResult refundResult = wechatPayService.refund(refundReq);

// 5. 部分退款（退 0.01 元中的 0.005 元）
RefundRequest partialRefund = RefundRequest.builder()
        .outTradeNo("ORDER-20240601-001")
        .outRefundNo("REFUND-20240601-002")
        .totalAmount(new BigDecimal("0.01"))
        .refundAmount(new BigDecimal("0.005"))
        .reason("部分退款")
        .build();
wechatPayService.refund(partialRefund);

// 6. 退款查询
RefundResult queryRefund = wechatPayService.refundQuery("REFUND-20240601-001");

// 7. 处理支付回调（在你的 Controller 中）
@PostMapping("/pay/wechat/notify")
public String wechatNotify(@RequestBody String body,
                           @RequestHeader Map<String, String> headers) {
    PayNotifyResult notify = wechatPayService.handleNotify(body, headers);
    if (notify.getStatus() == PayStatus.SUCCESS) {
        // 更新你的订单状态
        return "SUCCESS";  // 必须返回成功，否则微信会重复通知
    }
    return "FAIL";
}
```

> **注意**：`microPay`（付款码支付）在 wechatpay-java 0.2.17 中暂不可用，需升级 SDK 或自行实现。

---

## 二、支付宝

### 2.1 前置条件

| 条件 | 说明 |
|------|------|
| 支付宝账号 | 在 [支付宝开放平台](https://open.alipay.com/) 注册并实名认证 |
| 应用创建 | 开放平台 → 控制台 → 创建应用 |
| 签约产品 | 应用下签约「**当面付**」/「**电脑网站支付**」/「**手机网站支付**」等 |
| 密钥 | 生成 RSA2 应用私钥 + 上传公钥 |

### 2.2 配置项获取

#### `app-id`（应用 ID）

1. 登录 [支付宝开放平台](https://open.alipay.com/)
2. 控制台 → 我的应用 → 选择你的应用
3. 应用详情页 → **APPID**（格式如 `2021001234567890`）
4. 配置示例：`"2021001234567890"`

#### `private-key-path`（应用私钥路径）

1. 下载 [支付宝密钥生成工具](https://opendocs.alipay.com/open/291/105971)（或使用 `openssl`）
2. 生成 RSA2 密钥对（2048 位）
3. **私钥**保存在服务器，如 `/etc/alipay/private_key.pem`
4. **公钥**上传到支付宝开放平台：应用详情 → 开发设置 → 接口加签方式 → 上传公钥证书
5. 配置示例：`"/etc/alipay/private_key.pem"`

```bash
# 使用 openssl 生成密钥对
openssl genrsa -out private_key.pem 2048
openssl rsa -in private_key.pem -pubout -out public_key.pem
```

#### `alipay-public-key-path`（支付宝公钥路径）

1. 上传你的公钥后，支付宝会生成一个**支付宝公钥**
2. 在 应用详情 → 开发设置 → 接口加签方式 → **查看支付宝公钥**
3. 将支付宝公钥保存到服务器，如 `/etc/alipay/alipay_public_key.pem`
4. 配置示例：`"/etc/alipay/alipay_public_key.pem"`

#### `notify-url`（支付结果回调地址）

1. 必须为 **HTTPS** 公网可访问地址
2. 配置示例：`"https://your-domain.com/pay/alipay/notify"`

#### `gateway-url`（网关地址，可选）

- 默认：`https://openapi.alipay.com/gateway.do`
- 沙箱环境（测试用）：`https://openapi-sandbox.dl.alipaydev.com/gateway.do`

### 2.3 使用方式

```java
@Autowired
private AlipayService alipayService;

// 1. 手机扫码支付 — 生成二维码链接
PayRequest req = new PayRequest();
req.setOutTradeNo("ORDER-20240601-001");
req.setTotalAmount(new BigDecimal("0.01"));
req.setDescription("测试商品");
String codeUrl = alipayService.generateQrCode(req);
// 返回的 codeUrl 是支付宝二维码链接
// 前端将其渲染为二维码，用户用支付宝扫码支付

// 2. 条码支付（商户扫用户付款码）
MicroPayRequest microReq = new MicroPayRequest();
microReq.setOutTradeNo("ORDER-20240601-002");
microReq.setTotalAmount(new BigDecimal("10.00"));
microReq.setDescription("收银台支付");
microReq.setAuthCode("281234567890123456");  // 扫码枪扫到的付款码
PayResult payResult = alipayService.microPay(microReq);
if (payResult.isSuccess()) {
    // 支付成功
} else {
    // 重试查询
    PayResult query = alipayService.queryOrder("ORDER-20240601-002");
}

// 3. 查询订单
PayResult result = alipayService.queryOrder("ORDER-20240601-001");

// 4. 关单
alipayService.closeOrder("ORDER-20240601-001");

// 5. 退款
RefundRequest refundReq = RefundRequest.builder()
        .outTradeNo("ORDER-20240601-001")
        .outRefundNo("REFUND-001")
        .totalAmount(new BigDecimal("0.01"))
        .refundAmount(new BigDecimal("0.01"))
        .reason("退款原因")
        .build();
RefundResult refundResult = alipayService.refund(refundReq);

// 6. 退款查询
RefundResult query = alipayService.refundQuery("REFUND-001");

// 7. 处理支付回调（在你的 Controller 中）
@PostMapping("/pay/alipay/notify")
public String alipayNotify(@RequestBody String body,
                           @RequestParam Map<String, String> params) {
    PayNotifyResult notify = alipayService.handleNotify(body, params);
    if (notify.getStatus() == PayStatus.SUCCESS) {
        // 更新订单
        return "success";  // 必须返回 success
    }
    return "failure";
}
```

---

## 三、支付宝沙箱环境（开发测试用）

开发阶段可使用支付宝沙箱，无需真实商户资质：

1. 登录 [支付宝开放平台沙箱](https://open.alipay.com/develop/sandbox/app)
2. 系统自动生成沙箱应用（有 APPID、网关地址等）
3. 沙箱网关：`https://openapi-sandbox.dl.alipaydev.com/gateway.do`
4. 配置：

```yaml
pay:
  alipay:
    app-id: "9021000xxxxxxxx"     # 沙箱 APPID
    private-key-path: /path/to/private_key.pem
    alipay-public-key-path: /path/to/alipay_public_key.pem
    notify-url: http://localhost:8090/test/pay/alipay/notify
    gateway-url: https://openapi-sandbox.dl.alipaydev.com/gateway.do
```

5. 使用沙箱版支付宝 App 扫码测试（沙箱页面可下载）

---

## 四、完整配置参考

```yaml
pay:
  wechat:
    merchant-id: "1234567890"
    merchant-serial-number: "7CA1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9"
    private-key-path: /etc/wechat/apiclient_key.pem
    api-v3-key: "a1b2c3d4e5f6a7b8c9d0e1f2A3B4C5D"
    notify-url: https://your-domain.com/pay/wechat/notify
  alipay:
    app-id: "2021001234567890"
    private-key-path: /etc/alipay/private_key.pem
    alipay-public-key-path: /etc/alipay/alipay_public_key.pem
    notify-url: https://your-domain.com/pay/alipay/notify
    gateway-url: https://openapi.alipay.com/gateway.do
```

---

## 五、回调端点示例

```java
@RestController
public class PayNotifyController {

    @Autowired
    private WechatPayService wechatPayService;
    @Autowired
    private AlipayService alipayService;

    /** 微信支付回调 */
    @PostMapping("/pay/wechat/notify")
    public Map<String, String> wechatNotify(
            @RequestBody String body,
            @RequestHeader Map<String, String> headers) {
        PayNotifyResult result = wechatPayService.handleNotify(body, headers);
        // TODO: 更新你的订单状态
        if (result.getStatus() == PayStatus.SUCCESS) {
            return Map.of("code", "SUCCESS", "message", "成功");
        }
        return Map.of("code", "FAIL", "message", "失败");
    }

    /** 支付宝回调 */
    @PostMapping("/pay/alipay/notify")
    public String alipayNotify(
            @RequestBody String body,
            @RequestParam Map<String, String> params) {
        PayNotifyResult result = alipayService.handleNotify(body, params);
        // TODO: 更新你的订单状态
        if (result.getStatus() == PayStatus.SUCCESS) {
            return "success";
        }
        return "failure";
    }
}
```

---

## 六、PayStatus 状态说明

| 状态 | 含义 | 处理建议 |
|------|------|---------|
| `SUCCESS` | 支付成功 | 更新订单为已支付 |
| `NOTPAY` | 未支付 | 等待或关单 |
| `USERPAYING` | 用户支付中 | 轮询 queryOrder 确认 |
| `CLOSED` | 已关闭 | 订单作废 |
| `REFUND` | 已退款 | 更新退款记录 |

---

## 七、Maven 依赖

```xml
<!-- 只要微信 -->
<dependency>
    <groupId>com.yt.third</groupId>
    <artifactId>pay-wechat-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 只要支付宝 -->
<dependency>
    <groupId>com.yt.third</groupId>
    <artifactId>pay-alipay-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 两个都要 -->
<dependency>
    <groupId>com.yt.third</groupId>
    <artifactId>pay-all-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```
