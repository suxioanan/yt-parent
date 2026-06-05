# 支付模块集成设计文档

> 设计日期：2026-06-05
> 状态：待审核

## 1. 目标

在现有 `yt-parent` 项目基础上集成**微信支付**和**支付宝**，提供统一的支付/退款/回调接口，支持：

- **手机扫码支付** — 用户扫商户二维码付款
- **扫描器扫码支付** — 商户扫用户付款码（条码支付）
- **退款** + **退款查询**
- **订单查询** + **关单**

---

## 2. 技术决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 实现方式 | **官方 SDK** | 签名/验签/加解密复杂度高，SDK 开箱即用 |
| 微信支付 SDK | `com.github.wechatpay-apiv3:wechatpay-java` | 微信支付 V3 官方 Java SDK |
| 支付宝 SDK | `com.alipay.sdk:alipay-sdk-java` | 支付宝官方 Java SDK |
| 回调方式 | **业务方自建端点**，starter 提供验签工具 | 灵活，业务方控制路由、日志、幂等 |
| 架构模式 | 照搬 SSO 模块：common 接口 + 平台 starter + all 聚合 | 和现有 10 个模块风格一致 |

---

## 3. 模块结构

```
yt-parent/
├── pom.xml                                      # 父 POM（不变）
│
├── third-pay-starter/                           # 🆕 支付聚合模块（pom）
│   ├── pom.xml                                  # 〈modules〉4 个子模块〈/modules〉
│   │
│   ├── pay-common/                              # 统一接口 + 通用模型
│   │   ├── pom.xml
│   │   └── src/main/java/com/yt/pay/
│   │       ├── PayService.java                  # 统一支付接口
│   │       ├── PayNotifyService.java            # 回调处理接口（验签+解密）
│   │       └── model/
│   │           ├── PayRequest.java              # 下单请求
│   │           ├── MicroPayRequest.java         # 条码支付请求
│   │           ├── PayResult.java               # 支付结果
│   │           ├── RefundRequest.java           # 退款请求
│   │           ├── RefundResult.java            # 退款结果
│   │           └── PayNotifyResult.java         # 回调通知结果
│   │
│   ├── pay-wechat-starter/                      # 微信支付实现
│   │   ├── pom.xml                              # deps: pay-common + wechatpay-java
│   │   └── src/main/java/com/yt/pay/wechat/
│   │       ├── WechatPayService.java            # extends PayService（无额外方法）
│   │       ├── WechatPayServiceImpl.java        # 实现
│   │       └── config/
│   │           ├── WechatPayProperties.java      # @ConfigProperties("pay.wechat")
│   │           └── WechatPayAutoConfiguration.java
│   │
│   ├── pay-alipay-starter/                      # 支付宝实现
│   │   ├── pom.xml                              # deps: pay-common + alipay-sdk-java
│   │   └── src/main/java/com/yt/pay/alipay/
│   │       ├── AlipayService.java               # extends PayService（无额外方法）
│   │       ├── AlipayServiceImpl.java           # 实现
│   │       └── config/
│   │           ├── AlipayProperties.java         # @ConfigProperties("pay.alipay")
│   │           └── AlipayAutoConfiguration.java
│   │
│   └── pay-all-starter/                         # 全家桶聚合
│       └── pom.xml                              # deps: pay-wechat + pay-alipay
│
└── pay-test/                                    # 🆕 HTTP 测试应用
    ├── pom.xml                                  # deps: pay-all-starter
    └── src/main/java/com/yt/pay/test/
        ├── PayTestApplication.java
        └── controller/
            ├── WechatPayTestController.java
            └── AlipayTestController.java
```

---

## 4. 统一接口设计

### 4.1 PayService — 支付服务接口

```java
package com.yt.pay;

public interface PayService {

    /**
     * 手机扫码支付 — 生成二维码链接（用户扫商户）
     * @return 支付链接/二维码 URL
     */
    String generateQrCode(PayRequest request);

    /**
     * 扫描器扫码支付 — 商户扫用户付款码（条码支付）
     */
    PayResult microPay(MicroPayRequest request);

    /**
     * 查询订单状态
     */
    PayResult queryOrder(String outTradeNo);

    /**
     * 关单/取消订单
     */
    void closeOrder(String outTradeNo);

    /**
     * 退款
     */
    RefundResult refund(RefundRequest request);

    /**
     * 退款查询
     */
    RefundResult refundQuery(String outRefundNo);

    /**
     * 处理异步回调 — 验签 + 解密 + 转为统一结果
     */
    PayNotifyResult handleNotify(String body, Map<String, String> headers);
}
```

### 4.2 平台接口

```java
// 微信 — 无额外方法
public interface WechatPayService extends PayService {}

// 支付宝 — 无额外方法
public interface AlipayService extends PayService {}
```

---

## 5. 数据模型

### PayRequest（扫码下单）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| outTradeNo | String | ✅ | 商户订单号 |
| totalAmount | BigDecimal | ✅ | 金额（元） |
| description | String | ✅ | 商品描述 |
| notifyUrl | String | ❌ | 回调地址（覆盖配置默认值） |
| expireMinutes | Integer | ❌ | 过期时间（分钟），默认 5 |

### MicroPayRequest（条码支付）

继承 `PayRequest`，额外：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| authCode | String | ✅ | 用户付款码（扫码枪扫到的条码） |

### PayResult

| 字段 | 类型 | 说明 |
|------|------|------|
| outTradeNo | String | 商户订单号 |
| transactionId | String | 平台交易号（微信/支付宝） |
| status | PayStatus | SUCCESS / REFUND / CLOSED / NOTPAY / USERPAYING |
| totalAmount | BigDecimal | 交易金额 |
| payerOpenid | String | 付款人 openid |
| rawResponse | String | 原始响应 JSON（调试用） |
| success | boolean | 是否成功 |

### PayStatus 枚举

```java
public enum PayStatus {
    SUCCESS,    // 支付成功
    REFUND,     // 已退款
    CLOSED,     // 已关闭
    NOTPAY,     // 未支付
    USERPAYING  // 用户支付中
}
```

### RefundRequest

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| outTradeNo | String | ✅ | 原商户订单号 |
| outRefundNo | String | ✅ | 退款单号（每次退款唯一，用于幂等和退款查询） |
| totalAmount | BigDecimal | ✅ | 原订单金额 |
| refundAmount | BigDecimal | ✅ | 本次退款金额 |
| reason | String | ❌ | 退款原因 |

### 部分退款

支持全额退款和部分退款：

| 场景 | 条件 | 说明 |
|------|------|------|
| 全额退款 | `refundAmount == totalAmount` | 退款后订单状态变为 REFUND |
| 部分退款 | `refundAmount < totalAmount` | 剩余金额可继续退款，可多次部分退款直到累计退款金额 = 原订单金额 |
| 多次部分退款 | 多次调用 `refund()` | 每次 `outRefundNo` 必须不同（唯一），且 `refundAmount` 累计不能超过 `totalAmount` |

平台侧对部分退款的限制：
- **微信支付**：支持多次部分退款，单笔订单退款次数无限制，累计退款金额不能超过原订单金额
- **支付宝**：支持多次部分退款，单笔订单退款次数无限制，累计退款金额不能超过原订单金额

### RefundResult

| 字段 | 类型 | 说明 |
|------|------|------|
| outRefundNo | String | 退款单号 |
| refundId | String | 平台退款单号 |
| status | String | SUCCESS / PROCESSING / FAILED |
| refundAmount | BigDecimal | 退款金额 |
| success | boolean | 是否成功 |

### PayNotifyResult

| 字段 | 类型 | 说明 |
|------|------|------|
| outTradeNo | String | 商户订单号 |
| transactionId | String | 平台交易号 |
| status | PayStatus | 支付状态 |
| totalAmount | BigDecimal | 金额 |
| eventType | String | 回调事件类型（支付/退款） |
| rawBody | String | 原始回调体 |

---

## 6. 平台配置

### 微信支付 `pay.wechat`

```yaml
pay:
  wechat:
    merchant-id: "1234567890"           # 商户号
    merchant-serial-number: "xxx"       # 商户API证书序列号
    private-key-path: /path/to/apiclient_key.pem   # 商户私钥路径
    api-v3-key: "xxx"                   # API V3 密钥
    notify-url: https://your-app.com/pay/wechat/notify   # 回调地址
```

### 支付宝 `pay.alipay`

```yaml
pay:
  alipay:
    app-id: "2021001xxx"                # 应用ID
    private-key-path: /path/to/private_key.pem    # 应用私钥路径
    alipay-public-key-path: /path/to/alipay_public_key.pem  # 支付宝公钥路径
    notify-url: https://your-app.com/pay/alipay/notify      # 回调地址
    gateway-url: https://openapi.alipay.com/gateway.do      # 网关（默认）
```

---

## 7. API 映射

### 微信支付

| PayService 方法 | 微信 API |
|----------------|---------|
| generateQrCode | `POST /v3/pay/transactions/native` |
| microPay | `POST /v3/pay/transactions/codepay` |
| queryOrder | `GET /v3/pay/transactions/out-trade-no/{outTradeNo}` |
| closeOrder | `POST /v3/pay/transactions/out-trade-no/{outTradeNo}/close` |
| refund | `POST /v3/refund/domestic/refunds` |
| refundQuery | `GET /v3/refund/domestic/refunds/{outRefundNo}` |
| handleNotify | 验签 → AES 解密 → 解析 JSON |

### 支付宝

| PayService 方法 | 支付宝 API |
|----------------|-----------|
| generateQrCode | `alipay.trade.precreate` |
| microPay | `alipay.trade.pay` |
| queryOrder | `alipay.trade.query` |
| closeOrder | `alipay.trade.close` |
| refund | `alipay.trade.refund` |
| refundQuery | `alipay.trade.fastpay.refund.query` |
| handleNotify | RSA 验签 → 解析参数 |

---

## 8. 回调处理流程

虽然业务方自建回调端点，但 starter 提供完整的 `handleNotify` 方法：

```
业务方 Controller
  ↓ 收到 POST /pay/xxx/notify（原始 body + headers）
  ↓ 调用 payService.handleNotify(body, headers)
  ↓
PayServiceImpl.handleNotify:
  ① SDK 验签（headers 中的 signature/timestamp 等）
  ② 解密回调内容（微信 AES，支付宝直接读取）
  ③ 转为 PayNotifyResult 统一模型
  ④ 返回
  ↓
业务方拿到 PayNotifyResult：
  - 判断 status == SUCCESS → 更新订单状态
  - 返回 success 响应给平台
```

---

## 9. 支付场景流程

### 手机扫码支付

```
1. App/网页 → 调用后端接口
2. 后端 → payService.generateQrCode(request) → 返回二维码 URL
3. 前端 → 将 URL 渲染为二维码展示
4. 用户 → 打开微信/支付宝扫二维码 → 输入密码 → 支付
5. 微信/支付宝 → 异步 POST 回调到业务方 /pay/xxx/notify
6. 业务方 Controller → payService.handleNotify(body, headers) → 更新订单状态
```

### 扫描器扫码支付

```
1. 收银员 → 扫码枪扫用户付款码 → 得到 authCode
2. 收银系统 → payService.microPay(microPayRequest) → 同步返回结果
3. 如果成功 → 交易完成
4. 如果返回 USERPAYING → 轮询 queryOrder 确认状态
```

---

## 10. 错误处理

- SDK 调用失败 → `throw new RuntimeException("微信支付/支付宝...失败: " + msg, e)`
- 签名验证失败 → `throw new RuntimeException("验签失败")`
- 网络超时 → 通过 SDK 配置 timeout 参数

不定义自定义异常类，和 SSO 模块保持一致，统一 `RuntimeException`。

---

## 11. 依赖清单

### pay-wechat-starter

```xml
<dependency>
    <groupId>com.github.wechatpay-apiv3</groupId>
    <artifactId>wechatpay-java</artifactId>
    <version>0.2.17</version>
</dependency>
```

### pay-alipay-starter

```xml
<dependency>
    <groupId>com.alipay.sdk</groupId>
    <artifactId>alipay-sdk-java</artifactId>
    <version>4.39.242.ALL</version>
</dependency>
```

---

## 12. pay-test 测试接口

| 平台 | 接口 | 说明 |
|------|------|------|
| 微信 | `POST /test/pay/wechat/qrcode` | 生成扫码支付二维码 |
| 微信 | `POST /test/pay/wechat/micropay` | 条码支付 |
| 微信 | `GET /test/pay/wechat/order/{outTradeNo}` | 查询订单 |
| 微信 | `POST /test/pay/wechat/order/{outTradeNo}/close` | 关单 |
| 微信 | `POST /test/pay/wechat/refund` | 退款 |
| 微信 | `GET /test/pay/wechat/refund/{outRefundNo}` | 退款查询 |
| 微信 | `POST /test/pay/wechat/notify` | 模拟回调（测试用） |
| 支付宝 | 同上 `/test/pay/alipay/*` | 同微信接口结构 |
