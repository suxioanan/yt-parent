# Payment Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 `third-pay-starter` 聚合模块（pay-common / pay-wechat-starter / pay-alipay-starter / pay-all-starter）及 `pay-test` 测试模块，提供微信支付和支付宝的统一接口。

**Architecture:** 照搬 SSO 模块模式 — pay-common 定义 PayService 接口 + 6 个通用模型，各平台 starter 封装官方 SDK，pay-all-starter 聚合。

**Tech Stack:** Java 17, Spring Boot 3.2, hutool 5.8.34, wechatpay-java 0.2.17, alipay-sdk-java 4.39.242.ALL

---

## File Overview

```
yt-parent/
├── pom.xml                                    # Modify: +<module> +<depMgmt>
│
├── third-pay-starter/                         # Create: aggregator pom
│   ├── pom.xml
│   ├── pay-common/
│   │   ├── pom.xml
│   │   └── src/main/java/com/yt/pay/
│   │       ├── PayService.java
│   │       └── model/
│   │           ├── PayRequest.java
│   │           ├── MicroPayRequest.java
│   │           ├── PayResult.java
│   │           ├── PayStatus.java
│   │           ├── RefundRequest.java
│   │           ├── RefundResult.java
│   │           └── PayNotifyResult.java
│   ├── pay-wechat-starter/
│   │   ├── pom.xml
│   │   ├── src/main/java/com/yt/pay/wechat/
│   │   │   ├── WechatPayService.java
│   │   │   ├── WechatPayServiceImpl.java
│   │   │   └── config/
│   │   │       ├── WechatPayProperties.java
│   │   │       └── WechatPayAutoConfiguration.java
│   │   └── src/main/resources/META-INF/spring/
│   │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   ├── pay-alipay-starter/
│   │   ├── pom.xml
│   │   ├── src/main/java/com/yt/pay/alipay/
│   │   │   ├── AlipayService.java
│   │   │   ├── AlipayServiceImpl.java
│   │   │   └── config/
│   │   │       ├── AlipayProperties.java
│   │   │       └── AlipayAutoConfiguration.java
│   │   └── src/main/resources/META-INF/spring/
│   │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── pay-all-starter/
│       └── pom.xml
│
└── pay-test/
    ├── pom.xml
    └── src/main/
        ├── java/com/yt/pay/test/
        │   ├── PayTestApplication.java
        │   └── controller/
        │       ├── WechatPayTestController.java
        │       └── AlipayTestController.java
        └── resources/
            └── application.yml
```

---

### Task 0: 前置准备 — 修改父 POM

**Files:**
- Modify: `pom.xml` (项目根)

- [ ] **Step 1: 添加支付模块声明和 SDK 版本管理**

在 `pom.xml` 中：

1. `<modules>` 部分新增 `<module>third-pay-starter</module>` 和 `<module>pay-test</module>`
2. `<dependencyManagement>` 中新增 wechatpay-java 和 alipay-sdk-java 版本管理

编辑 `<modules>`：
```xml
<modules>
    <module>ocr-spring-boot-starter</module>
    <module>third-sso-starter</module>
    <module>third-pay-starter</module>
    <module>sso-test</module>
    <module>pay-test</module>
</modules>
```

在 `<dependencyManagement><dependencies>` 中 hutool 之后添加：
```xml
<dependency>
    <groupId>com.github.wechatpay-apiv3</groupId>
    <artifactId>wechatpay-java</artifactId>
    <version>0.2.17</version>
</dependency>
<dependency>
    <groupId>com.alipay.sdk</groupId>
    <artifactId>alipay-sdk-java</artifactId>
    <version>4.39.242.ALL</version>
</dependency>
```

- [ ] **Step 2: 验证父 POM 解析**

```bash
cd /Users/sunan/java_project/demo/yt-parent && mvn validate
```

Expected: BUILD SUCCESS

---

### Task 1: 创建 third-pay-starter 聚合 POM

**Files:**
- Create: `third-pay-starter/pom.xml`

- [ ] **Step 1: 创建聚合 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.yt.third</groupId>
        <artifactId>yt-parent</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>third-pay-starter</artifactId>
    <packaging>pom</packaging>
    <name>third-pay-starter</name>
    <description>第三方支付集成 - 聚合模块（微信支付/支付宝）</description>

    <modules>
        <module>pay-common</module>
        <module>pay-wechat-starter</module>
        <module>pay-alipay-starter</module>
        <module>pay-all-starter</module>
    </modules>

</project>
```

- [ ] **Step 2: 验证**

```bash
mvn validate -pl third-pay-starter
```

Expected: BUILD SUCCESS（子目录尚未创建，可能警告，无 fatal 错误即可）

---

### Task 2: 创建 pay-common 模块

**Files:**
- Create: `third-pay-starter/pay-common/pom.xml`
- Create: `third-pay-starter/pay-common/src/main/java/com/yt/pay/PayService.java`
- Create: `third-pay-starter/pay-common/src/main/java/com/yt/pay/model/PayStatus.java`
- Create: `third-pay-starter/pay-common/src/main/java/com/yt/pay/model/PayRequest.java`
- Create: `third-pay-starter/pay-common/src/main/java/com/yt/pay/model/MicroPayRequest.java`
- Create: `third-pay-starter/pay-common/src/main/java/com/yt/pay/model/PayResult.java`
- Create: `third-pay-starter/pay-common/src/main/java/com/yt/pay/model/RefundRequest.java`
- Create: `third-pay-starter/pay-common/src/main/java/com/yt/pay/model/RefundResult.java`
- Create: `third-pay-starter/pay-common/src/main/java/com/yt/pay/model/PayNotifyResult.java`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p third-pay-starter/pay-common/src/main/java/com/yt/pay/model
```

- [ ] **Step 2: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.yt.third</groupId>
        <artifactId>third-pay-starter</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>pay-common</artifactId>
    <packaging>jar</packaging>
    <name>pay-common</name>
    <description>支付公共接口定义 + 通用模型</description>
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 创建 PayStatus 枚举**

`pay-common/src/main/java/com/yt/pay/model/PayStatus.java`:

```java
package com.yt.pay.model;

/**
 * 支付状态
 */
public enum PayStatus {
    /** 支付成功 */
    SUCCESS,
    /** 已退款 */
    REFUND,
    /** 已关闭 */
    CLOSED,
    /** 未支付 */
    NOTPAY,
    /** 用户支付中 */
    USERPAYING
}
```

- [ ] **Step 4: 创建 PayRequest**

`pay-common/src/main/java/com/yt/pay/model/PayRequest.java`:

```java
package com.yt.pay.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 扫码下单请求
 */
@Data
@Builder
public class PayRequest {
    /** 商户订单号 */
    private String outTradeNo;
    /** 金额（元） */
    private BigDecimal totalAmount;
    /** 商品描述 */
    private String description;
    /** 回调地址（覆盖配置默认值） */
    private String notifyUrl;
    /** 过期时间（分钟），默认 5 */
    @Builder.Default
    private Integer expireMinutes = 5;
}
```

- [ ] **Step 5: 创建 MicroPayRequest**

`pay-common/src/main/java/com/yt/pay/model/MicroPayRequest.java`:

```java
package com.yt.pay.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/**
 * 条码支付请求（商户扫用户付款码）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
public class MicroPayRequest extends PayRequest {
    /** 用户付款码 */
    private String authCode;
}
```

Wait — `@Builder` + inheritance 有坑。改用手动 getter/setter 或者不用 Builder 继承。改为两个独立类，MicroPayRequest 包含 PayRequest 的所有字段。

- [ ] **Step 4 revised: 创建 PayRequest（无 Builder 继承问题）**

`pay-common/src/main/java/com/yt/pay/model/PayRequest.java`:

```java
package com.yt.pay.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 扫码下单请求
 */
@Data
public class PayRequest {
    /** 商户订单号 */
    private String outTradeNo;
    /** 金额（元） */
    private BigDecimal totalAmount;
    /** 商品描述 */
    private String description;
    /** 回调地址（覆盖配置默认值） */
    private String notifyUrl;
    /** 过期时间（分钟），默认 5 */
    private Integer expireMinutes = 5;
}
```

- [ ] **Step 5 revised: 创建 MicroPayRequest**

`pay-common/src/main/java/com/yt/pay/model/MicroPayRequest.java`:

```java
package com.yt.pay.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 条码支付请求（商户扫用户付款码）
 */
@Data
public class MicroPayRequest {
    /** 商户订单号 */
    private String outTradeNo;
    /** 金额（元） */
    private BigDecimal totalAmount;
    /** 商品描述 */
    private String description;
    /** 用户付款码（扫码枪扫到的条码） */
    private String authCode;
}
```

- [ ] **Step 6: 创建 PayResult**

`pay-common/src/main/java/com/yt/pay/model/PayResult.java`:

```java
package com.yt.pay.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 支付结果
 */
@Data
@Builder
public class PayResult {
    /** 商户订单号 */
    private String outTradeNo;
    /** 平台交易号 */
    private String transactionId;
    /** 支付状态 */
    private PayStatus status;
    /** 交易金额 */
    private BigDecimal totalAmount;
    /** 付款人 openid */
    private String payerOpenid;
    /** 原始响应 JSON */
    private String rawResponse;
    /** 是否成功 */
    private boolean success;
}
```

- [ ] **Step 7: 创建 RefundRequest**

`pay-common/src/main/java/com/yt/pay/model/RefundRequest.java`:

```java
package com.yt.pay.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 退款请求（支持全额退款和部分退款）
 *
 * 部分退款：refundAmount ＜ totalAmount，剩余金额可多次退款
 * 每次退款 outRefundNo 必须不同，累计退款金额不能超过 totalAmount
 */
@Data
@Builder
public class RefundRequest {
    /** 原商户订单号 */
    private String outTradeNo;
    /** 退款单号（每次退款唯一，用于幂等） */
    private String outRefundNo;
    /** 原订单金额 */
    private BigDecimal totalAmount;
    /** 本次退款金额（partial refund 时小于 totalAmount） */
    private BigDecimal refundAmount;
    /** 退款原因 */
    private String reason;
}
```

- [ ] **Step 8: 创建 RefundResult**

`pay-common/src/main/java/com/yt/pay/model/RefundResult.java`:

```java
package com.yt.pay.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 退款结果
 */
@Data
@Builder
public class RefundResult {
    /** 退款单号 */
    private String outRefundNo;
    /** 平台退款单号 */
    private String refundId;
    /** 退款状态：SUCCESS / PROCESSING / FAILED */
    private String status;
    /** 退款金额 */
    private BigDecimal refundAmount;
    /** 是否成功 */
    private boolean success;
}
```

- [ ] **Step 9: 创建 PayNotifyResult**

`pay-common/src/main/java/com/yt/pay/model/PayNotifyResult.java`:

```java
package com.yt.pay.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 支付回调通知结果
 */
@Data
@Builder
public class PayNotifyResult {
    /** 商户订单号 */
    private String outTradeNo;
    /** 平台交易号 */
    private String transactionId;
    /** 支付状态 */
    private PayStatus status;
    /** 金额 */
    private BigDecimal totalAmount;
    /** 事件类型：支付/退款 */
    private String eventType;
    /** 原始回调体 */
    private String rawBody;
}
```

- [ ] **Step 10: 创建 PayService 统一接口**

`pay-common/src/main/java/com/yt/pay/PayService.java`:

```java
package com.yt.pay;

import com.yt.pay.model.*;
import java.util.Map;

/**
 * 统一支付服务接口
 *
 * @author sunan
 */
public interface PayService {

    /** 手机扫码支付 — 生成二维码链接（用户扫商户） */
    String generateQrCode(PayRequest request);

    /** 扫描器扫码支付 — 商户扫用户付款码 */
    PayResult microPay(MicroPayRequest request);

    /** 查询订单状态 */
    PayResult queryOrder(String outTradeNo);

    /** 关单/取消订单 */
    void closeOrder(String outTradeNo);

    /** 退款 */
    RefundResult refund(RefundRequest request);

    /** 退款查询 */
    RefundResult refundQuery(String outRefundNo);

    /** 处理异步回调 — 验签 + 解密 + 转为统一结果 */
    PayNotifyResult handleNotify(String body, Map<String, String> headers);
}
```

- [ ] **Step 11: 编译 pay-common**

```bash
mvn compile -pl third-pay-starter/pay-common
```

Expected: BUILD SUCCESS

---

### Task 3: 创建 pay-wechat-starter

**Files:**
- Create: `third-pay-starter/pay-wechat-starter/pom.xml`
- Create: `pay-wechat-starter/src/main/java/com/yt/pay/wechat/WechatPayService.java`
- Create: `pay-wechat-starter/src/main/java/com/yt/pay/wechat/WechatPayServiceImpl.java`
- Create: `pay-wechat-starter/src/main/java/com/yt/pay/wechat/config/WechatPayProperties.java`
- Create: `pay-wechat-starter/src/main/java/com/yt/pay/wechat/config/WechatPayAutoConfiguration.java`
- Create: `pay-wechat-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p third-pay-starter/pay-wechat-starter/src/main/java/com/yt/pay/wechat/config
mkdir -p third-pay-starter/pay-wechat-starter/src/main/resources/META-INF/spring
```

- [ ] **Step 2: pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.yt.third</groupId>
        <artifactId>third-pay-starter</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>pay-wechat-starter</artifactId>
    <packaging>jar</packaging>
    <name>pay-wechat-starter</name>
    <description>微信支付 Spring Boot Starter</description>
    <dependencies>
        <dependency>
            <groupId>com.yt.third</groupId>
            <artifactId>pay-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.github.wechatpay-apiv3</groupId>
            <artifactId>wechatpay-java</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: WechatPayProperties**

`pay-wechat-starter/src/main/java/com/yt/pay/wechat/config/WechatPayProperties.java`:

```java
package com.yt.pay.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信支付配置
 *
 * @author sunan
 */
@Data
@ConfigurationProperties("pay.wechat")
public class WechatPayProperties {
    /** 商户号 */
    private String merchantId;
    /** 商户 API 证书序列号 */
    private String merchantSerialNumber;
    /** 商户私钥路径 */
    private String privateKeyPath;
    /** API V3 密钥 */
    private String apiV3Key;
    /** 回调地址 */
    private String notifyUrl;
}
```

- [ ] **Step 4: WechatPayService**

`pay-wechat-starter/src/main/java/com/yt/pay/wechat/WechatPayService.java`:

```java
package com.yt.pay.wechat;

import com.yt.pay.PayService;

/**
 * 微信支付服务接口
 *
 * @author sunan
 */
public interface WechatPayService extends PayService {
}
```

- [ ] **Step 5: WechatPayServiceImpl**

`pay-wechat-starter/src/main/java/com/yt/pay/wechat/WechatPayServiceImpl.java`:

```java
package com.yt.pay.wechat;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.codepay.CodepayService;
import com.wechat.pay.java.service.payments.codepay.model.PayRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.yt.pay.model.*;
import com.yt.pay.wechat.config.WechatPayProperties;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 微信支付服务实现
 */
@Slf4j
public class WechatPayServiceImpl implements WechatPayService {

    private final WechatPayProperties properties;
    private final Config config;
    private final NativePayService nativePayService;
    private final CodepayService codepayService;
    private final RefundService refundService;
    private final NotificationParser notificationParser;

    public WechatPayServiceImpl(WechatPayProperties properties) {
        this.properties = properties;
        this.config = new RSAAutoCertificateConfig.Builder()
                .merchantId(properties.getMerchantId())
                .privateKeyFromPath(properties.getPrivateKeyPath())
                .merchantSerialNumber(properties.getMerchantSerialNumber())
                .apiV3Key(properties.getApiV3Key())
                .build();
        this.nativePayService = new NativePayService.Builder().config(config).build();
        this.codepayService = new CodepayService.Builder().config(config).build();
        this.refundService = new RefundService.Builder().config(config).build();
        this.notificationParser = new NotificationParser((NotificationConfig) config);
    }

    @Override
    public String generateQrCode(com.yt.pay.model.PayRequest request) {
        Amount amount = new Amount();
        amount.setTotal(toFen(request.getTotalAmount()));
        amount.setCurrency("CNY");

        PrepayRequest prepayRequest = new PrepayRequest();
        prepayRequest.setOutTradeNo(request.getOutTradeNo());
        prepayRequest.setDescription(request.getDescription());
        prepayRequest.setAmount(amount);
        prepayRequest.setNotifyUrl(request.getNotifyUrl() != null
                ? request.getNotifyUrl() : properties.getNotifyUrl());

        PrepayResponse response = nativePayService.prepay(prepayRequest);
        log.info("微信 Native 下单成功: outTradeNo={}, codeUrl={}",
                request.getOutTradeNo(), response.getCodeUrl());
        return response.getCodeUrl();
    }

    @Override
    public PayResult microPay(MicroPayRequest request) {
        Amount amount = new Amount();
        amount.setTotal(toFen(request.getTotalAmount()));
        amount.setCurrency("CNY");

        PayRequest payRequest = new PayRequest();
        payRequest.setOutTradeNo(request.getOutTradeNo());
        payRequest.setAuthCode(request.getAuthCode());
        payRequest.setDescription(request.getDescription());
        payRequest.setAmount(amount);

        Transaction transaction = codepayService.pay(payRequest);
        String rawResponse = JSONUtil.toJsonStr(transaction);
        return PayResult.builder()
                .outTradeNo(transaction.getOutTradeNo())
                .transactionId(transaction.getTransactionId())
                .status(convertStatus(transaction.getTradeState()))
                .totalAmount(request.getTotalAmount())
                .payerOpenid(transaction.getPayer() != null
                        ? transaction.getPayer().getOpenid() : null)
                .rawResponse(rawResponse)
                .success("SUCCESS".equals(transaction.getTradeState()))
                .build();
    }

    @Override
    public PayResult queryOrder(String outTradeNo) {
        Transaction transaction = nativePayService.queryOrderByOutTradeNo(outTradeNo);
        String rawResponse = JSONUtil.toJsonStr(transaction);
        return PayResult.builder()
                .outTradeNo(transaction.getOutTradeNo())
                .transactionId(transaction.getTransactionId())
                .status(convertStatus(transaction.getTradeState()))
                .totalAmount(transaction.getAmount() != null
                        ? toYuan(transaction.getAmount().getTotal()) : null)
                .payerOpenid(transaction.getPayer() != null
                        ? transaction.getPayer().getOpenid() : null)
                .rawResponse(rawResponse)
                .success("SUCCESS".equals(transaction.getTradeState()))
                .build();
    }

    @Override
    public void closeOrder(String outTradeNo) {
        nativePayService.closeOrder(outTradeNo);
        log.info("微信关单成功: outTradeNo={}", outTradeNo);
    }

    @Override
    public RefundResult refund(com.yt.pay.model.RefundRequest request) {
        AmountReq amountReq = new AmountReq();
        amountReq.setRefund(toFen(request.getRefundAmount()));
        amountReq.setTotal(toFen(request.getTotalAmount()));
        amountReq.setCurrency("CNY");

        CreateRequest createRequest = new CreateRequest();
        createRequest.setOutTradeNo(request.getOutTradeNo());
        createRequest.setOutRefundNo(request.getOutRefundNo());
        createRequest.setAmount(amountReq);
        createRequest.setReason(request.getReason());

        Refund refund = refundService.create(createRequest);
        String rawResponse = JSONUtil.toJsonStr(refund);
        return RefundResult.builder()
                .outRefundNo(refund.getOutRefundNo())
                .refundId(refund.getRefundId())
                .status(refund.getStatus())
                .refundAmount(request.getRefundAmount())
                .success("SUCCESS".equals(refund.getStatus()))
                .build();
    }

    @Override
    public RefundResult refundQuery(String outRefundNo) {
        QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
        request.setOutRefundNo(outRefundNo);

        Refund refund = refundService.queryByOutRefundNo(request);
        String rawResponse = JSONUtil.toJsonStr(refund);
        return RefundResult.builder()
                .outRefundNo(refund.getOutRefundNo())
                .refundId(refund.getRefundId())
                .status(refund.getStatus())
                .refundAmount(refund.getAmount() != null
                        ? toYuan(refund.getAmount().getRefund()) : null)
                .success("SUCCESS".equals(refund.getStatus()))
                .build();
    }

    @Override
    public PayNotifyResult handleNotify(String body, Map<String, String> headers) {
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(headers.get("wechatpay-serial"))
                .nonce(headers.get("wechatpay-nonce"))
                .signature(headers.get("wechatpay-signature"))
                .timestamp(headers.get("wechatpay-timestamp"))
                .body(body)
                .build();

        Transaction transaction = notificationParser.parse(requestParam, Transaction.class);
        return PayNotifyResult.builder()
                .outTradeNo(transaction.getOutTradeNo())
                .transactionId(transaction.getTransactionId())
                .status(convertStatus(transaction.getTradeState()))
                .totalAmount(transaction.getAmount() != null
                        ? toYuan(transaction.getAmount().getTotal()) : null)
                .eventType("payment")
                .rawBody(body)
                .build();
    }

    // ==================== helper ====================

    private int toFen(BigDecimal yuan) {
        return yuan.multiply(new BigDecimal("100")).intValue();
    }

    private BigDecimal toYuan(Integer fen) {
        return fen == null ? null
                : new BigDecimal(fen).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private PayStatus convertStatus(Transaction.TradeStateEnum state) {
        if (state == null) return PayStatus.NOTPAY;
        switch (state) {
            case SUCCESS: return PayStatus.SUCCESS;
            case CLOSED: return PayStatus.CLOSED;
            case NOTPAY: return PayStatus.NOTPAY;
            case USERPAYING: return PayStatus.USERPAYING;
            default: return PayStatus.NOTPAY;
        }
    }
}
```

- [ ] **Step 6: WechatPayAutoConfiguration**

`pay-wechat-starter/src/main/java/com/yt/pay/wechat/config/WechatPayAutoConfiguration.java`:

```java
package com.yt.pay.wechat.config;

import com.yt.pay.wechat.WechatPayService;
import com.yt.pay.wechat.WechatPayServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(WechatPayProperties.class)
public class WechatPayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WechatPayService wechatPayService(WechatPayProperties properties) {
        return new WechatPayServiceImpl(properties);
    }
}
```

- [ ] **Step 7: AutoConfiguration.imports**

`pay-wechat-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.yt.pay.wechat.config.WechatPayAutoConfiguration
```

- [ ] **Step 8: 编译**

```bash
mvn compile -pl third-pay-starter/pay-wechat-starter
```

Expected: BUILD SUCCESS

---

### Task 4: 创建 pay-alipay-starter

**Files:**
- Create: `third-pay-starter/pay-alipay-starter/pom.xml`
- Create: `pay-alipay-starter/src/main/java/com/yt/pay/alipay/AlipayService.java`
- Create: `pay-alipay-starter/src/main/java/com/yt/pay/alipay/AlipayServiceImpl.java`
- Create: `pay-alipay-starter/src/main/java/com/yt/pay/alipay/config/AlipayProperties.java`
- Create: `pay-alipay-starter/src/main/java/com/yt/pay/alipay/config/AlipayAutoConfiguration.java`
- Create: `pay-alipay-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p third-pay-starter/pay-alipay-starter/src/main/java/com/yt/pay/alipay/config
mkdir -p third-pay-starter/pay-alipay-starter/src/main/resources/META-INF/spring
```

- [ ] **Step 2: pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.yt.third</groupId>
        <artifactId>third-pay-starter</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>pay-alipay-starter</artifactId>
    <packaging>jar</packaging>
    <name>pay-alipay-starter</name>
    <description>支付宝 Spring Boot Starter</description>
    <dependencies>
        <dependency>
            <groupId>com.yt.third</groupId>
            <artifactId>pay-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.alipay.sdk</groupId>
            <artifactId>alipay-sdk-java</artifactId>
        </dependency>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: AlipayProperties**

`pay-alipay-starter/src/main/java/com/yt/pay/alipay/config/AlipayProperties.java`:

```java
package com.yt.pay.alipay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝配置
 */
@Data
@ConfigurationProperties("pay.alipay")
public class AlipayProperties {
    /** 应用 ID */
    private String appId;
    /** 应用私钥路径 */
    private String privateKeyPath;
    /** 支付宝公钥路径 */
    private String alipayPublicKeyPath;
    /** 回调地址 */
    private String notifyUrl;
    /** 网关地址 */
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
}
```

- [ ] **Step 4: AlipayService**

`pay-alipay-starter/src/main/java/com/yt/pay/alipay/AlipayService.java`:

```java
package com.yt.pay.alipay;

import com.yt.pay.PayService;

/**
 * 支付宝服务接口
 */
public interface AlipayService extends PayService {
}
```

- [ ] **Step 5: AlipayServiceImpl**

`pay-alipay-starter/src/main/java/com/yt/pay/alipay/AlipayServiceImpl.java`:

```java
package com.yt.pay.alipay;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.yt.pay.model.*;
import com.yt.pay.alipay.config.AlipayProperties;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
public class AlipayServiceImpl implements AlipayService {

    private final AlipayProperties properties;
    private final AlipayClient alipayClient;

    public AlipayServiceImpl(AlipayProperties properties) {
        this.properties = properties;
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.getGatewayUrl());
        config.setAppId(properties.getAppId());
        config.setPrivateKey(properties.getPrivateKeyPath());
        config.setAlipayPublicKey(properties.getAlipayPublicKeyPath());
        config.setFormat("json");
        config.setCharset("UTF-8");
        config.setSignType("RSA2");
        this.alipayClient = new DefaultAlipayClient(config);
    }

    @Override
    public String generateQrCode(com.yt.pay.model.PayRequest request) {
        AlipayTradePrecreateRequest req = new AlipayTradePrecreateRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", request.getOutTradeNo());
        bizContent.put("total_amount", request.getTotalAmount().toString());
        bizContent.put("subject", request.getDescription());
        req.setBizContent(bizContent.toString());
        req.setNotifyUrl(request.getNotifyUrl() != null
                ? request.getNotifyUrl() : properties.getNotifyUrl());

        try {
            AlipayTradePrecreateResponse resp = alipayClient.execute(req);
            if (!resp.isSuccess()) {
                throw new RuntimeException("支付宝预下单失败: " + resp.getMsg() + " " + resp.getSubMsg());
            }
            log.info("支付宝预下单成功: outTradeNo={}, qrCode={}",
                    request.getOutTradeNo(), resp.getQrCode());
            return resp.getQrCode();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝预下单异常: " + e.getMessage(), e);
        }
    }

    @Override
    public PayResult microPay(MicroPayRequest request) {
        AlipayTradePayRequest req = new AlipayTradePayRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", request.getOutTradeNo());
        bizContent.put("total_amount", request.getTotalAmount().toString());
        bizContent.put("subject", request.getDescription());
        bizContent.put("auth_code", request.getAuthCode());
        bizContent.put("scene", "bar_code");
        req.setBizContent(bizContent.toString());

        try {
            AlipayTradePayResponse resp = alipayClient.execute(req);
            String raw = JSONUtil.toJsonStr(resp);
            return PayResult.builder()
                    .outTradeNo(resp.getOutTradeNo())
                    .transactionId(resp.getTradeNo())
                    .status(convertStatus(resp.getTradeStatus()))
                    .totalAmount(request.getTotalAmount())
                    .payerOpenid(resp.getBuyerUserId())
                    .rawResponse(raw)
                    .success(resp.isSuccess() && "10000".equals(resp.getCode()))
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝条码支付异常: " + e.getMessage(), e);
        }
    }

    @Override
    public PayResult queryOrder(String outTradeNo) {
        AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        req.setBizContent(bizContent.toString());

        try {
            AlipayTradeQueryResponse resp = alipayClient.execute(req);
            String raw = JSONUtil.toJsonStr(resp);
            return PayResult.builder()
                    .outTradeNo(resp.getOutTradeNo())
                    .transactionId(resp.getTradeNo())
                    .status(convertStatus(resp.getTradeStatus()))
                    .totalAmount(resp.getTotalAmount() != null
                            ? new BigDecimal(resp.getTotalAmount()) : null)
                    .payerOpenid(resp.getBuyerUserId())
                    .rawResponse(raw)
                    .success(resp.isSuccess())
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝订单查询异常: " + e.getMessage(), e);
        }
    }

    @Override
    public void closeOrder(String outTradeNo) {
        AlipayTradeCloseRequest req = new AlipayTradeCloseRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        req.setBizContent(bizContent.toString());

        try {
            AlipayTradeCloseResponse resp = alipayClient.execute(req);
            if (!resp.isSuccess()) {
                throw new RuntimeException("支付宝关单失败: " + resp.getMsg());
            }
            log.info("支付宝关单成功: outTradeNo={}", outTradeNo);
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝关单异常: " + e.getMessage(), e);
        }
    }

    @Override
    public RefundResult refund(com.yt.pay.model.RefundRequest request) {
        AlipayTradeRefundRequest req = new AlipayTradeRefundRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", request.getOutTradeNo());
        bizContent.put("refund_amount", request.getRefundAmount().toString());
        bizContent.put("out_request_no", request.getOutRefundNo());
        if (request.getReason() != null) {
            bizContent.put("refund_reason", request.getReason());
        }
        req.setBizContent(bizContent.toString());

        try {
            AlipayTradeRefundResponse resp = alipayClient.execute(req);
            String raw = JSONUtil.toJsonStr(resp);
            return RefundResult.builder()
                    .outRefundNo(request.getOutRefundNo())
                    .refundId(resp.getTradeNo())
                    .status(resp.isSuccess() ? "SUCCESS" : "FAILED")
                    .refundAmount(request.getRefundAmount())
                    .success(resp.isSuccess())
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝退款异常: " + e.getMessage(), e);
        }
    }

    @Override
    public RefundResult refundQuery(String outRefundNo) {
        AlipayTradeFastpayRefundQueryRequest req = new AlipayTradeFastpayRefundQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_request_no", outRefundNo);
        req.setBizContent(bizContent.toString());

        try {
            AlipayTradeFastpayRefundQueryResponse resp = alipayClient.execute(req);
            String raw = JSONUtil.toJsonStr(resp);
            return RefundResult.builder()
                    .outRefundNo(resp.getOutRequestNo())
                    .refundId(resp.getTradeNo())
                    .status("SUCCESS")
                    .refundAmount(new BigDecimal(resp.getRefundAmount()))
                    .success(resp.isSuccess())
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝退款查询异常: " + e.getMessage(), e);
        }
    }

    @Override
    public PayNotifyResult handleNotify(String body, Map<String, String> params) {
        try {
            boolean verified = AlipaySignature.rsaCheckV1(
                    params, properties.getAlipayPublicKeyPath(), "UTF-8", "RSA2");
            if (!verified) {
                throw new RuntimeException("支付宝回调验签失败");
            }

            return PayNotifyResult.builder()
                    .outTradeNo(params.get("out_trade_no"))
                    .transactionId(params.get("trade_no"))
                    .status(convertStatus(params.get("trade_status")))
                    .totalAmount(new BigDecimal(params.get("total_amount")))
                    .eventType(params.get("event_type") != null
                            ? params.get("event_type") : "payment")
                    .rawBody(body)
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝回调处理异常: " + e.getMessage(), e);
        }
    }

    // ==================== helper ====================

    private PayStatus convertStatus(String tradeStatus) {
        if (tradeStatus == null) return PayStatus.NOTPAY;
        switch (tradeStatus) {
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return PayStatus.SUCCESS;
            case "TRADE_CLOSED":
                return PayStatus.CLOSED;
            case "WAIT_BUYER_PAY":
                return PayStatus.NOTPAY;
            default:
                return PayStatus.NOTPAY;
        }
    }
}
```

- [ ] **Step 6: AlipayAutoConfiguration**

`pay-alipay-starter/src/main/java/com/yt/pay/alipay/config/AlipayAutoConfiguration.java`:

```java
package com.yt.pay.alipay.config;

import com.yt.pay.alipay.AlipayService;
import com.yt.pay.alipay.AlipayServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AlipayProperties.class)
public class AlipayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AlipayService alipayService(AlipayProperties properties) {
        return new AlipayServiceImpl(properties);
    }
}
```

- [ ] **Step 7: AutoConfiguration.imports**

```
com.yt.pay.alipay.config.AlipayAutoConfiguration
```

- [ ] **Step 8: 编译**

```bash
mvn compile -pl third-pay-starter/pay-alipay-starter
```

Expected: BUILD SUCCESS

---

### Task 5: 创建 pay-all-starter

**Files:**
- Create: `third-pay-starter/pay-all-starter/pom.xml`

- [ ] **Step 1: 创建**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.yt.third</groupId>
        <artifactId>third-pay-starter</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>pay-all-starter</artifactId>
    <packaging>jar</packaging>
    <name>pay-all-starter</name>
    <description>支付全家桶 - 微信支付 + 支付宝</description>
    <dependencies>
        <dependency>
            <groupId>com.yt.third</groupId>
            <artifactId>pay-wechat-starter</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.yt.third</groupId>
            <artifactId>pay-alipay-starter</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 编译**

```bash
mvn compile -pl third-pay-starter/pay-all-starter
```

Expected: BUILD SUCCESS

---

### Task 6: 创建 pay-test 测试应用

**Files:**
- Create: `pay-test/pom.xml`
- Create: `pay-test/src/main/java/com/yt/pay/test/PayTestApplication.java`
- Create: `pay-test/src/main/java/com/yt/pay/test/controller/WechatPayTestController.java`
- Create: `pay-test/src/main/java/com/yt/pay/test/controller/AlipayTestController.java`
- Create: `pay-test/src/main/resources/application.yml`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p pay-test/src/main/java/com/yt/pay/test/controller
mkdir -p pay-test/src/main/resources
```

- [ ] **Step 2: pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.yt.third</groupId>
        <artifactId>yt-parent</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>pay-test</artifactId>
    <packaging>jar</packaging>
    <name>pay-test</name>
    <description>支付 HTTP 测试模块</description>
    <dependencies>
        <dependency>
            <groupId>com.yt.third</groupId>
            <artifactId>pay-all-starter</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: PayTestApplication**

`pay-test/src/main/java/com/yt/pay/test/PayTestApplication.java`:

```java
package com.yt.pay.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PayTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(PayTestApplication.class, args);
    }
}
```

- [ ] **Step 4: WechatPayTestController**

`pay-test/src/main/java/com/yt/pay/test/controller/WechatPayTestController.java`:

```java
package com.yt.pay.test.controller;

import com.yt.pay.model.*;
import com.yt.pay.wechat.WechatPayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/test/pay/wechat")
public class WechatPayTestController {

    @Autowired
    private WechatPayService wechatPayService;

    @PostMapping("/qrcode")
    public Map<String, Object> qrcode(@RequestBody PayRequest request) {
        try {
            String url = wechatPayService.generateQrCode(request);
            return Map.of("success", true, "codeUrl", url);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @PostMapping("/micropay")
    public Map<String, Object> micropay(@RequestBody MicroPayRequest request) {
        try {
            PayResult result = wechatPayService.microPay(request);
            return Map.of("success", true, "data", result);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @GetMapping("/order/{outTradeNo}")
    public Map<String, Object> queryOrder(@PathVariable String outTradeNo) {
        try {
            PayResult result = wechatPayService.queryOrder(outTradeNo);
            return Map.of("success", true, "data", result);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @PostMapping("/order/{outTradeNo}/close")
    public Map<String, Object> closeOrder(@PathVariable String outTradeNo) {
        try {
            wechatPayService.closeOrder(outTradeNo);
            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @PostMapping("/refund")
    public Map<String, Object> refund(@RequestBody RefundRequest request) {
        try {
            RefundResult result = wechatPayService.refund(request);
            return Map.of("success", true, "data", result);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @GetMapping("/refund/{outRefundNo}")
    public Map<String, Object> refundQuery(@PathVariable String outRefundNo) {
        try {
            RefundResult result = wechatPayService.refundQuery(outRefundNo);
            return Map.of("success", true, "data", result);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
```

- [ ] **Step 5: AlipayTestController** — 同理，把 `WechatPayService` 换成 `AlipayService`，路径 `/test/pay/alipay/*`

代码结构和 WechatPayTestController 一致，替换 Service 类型为 `AlipayService`，路径前缀改为 `/test/pay/alipay`。

- [ ] **Step 6: application.yml**

```yaml
server:
  port: 8090

pay:
  wechat:
    merchant-id: ""
    merchant-serial-number: ""
    private-key-path: /path/to/apiclient_key.pem
    api-v3-key: ""
    notify-url: http://localhost:8090/test/pay/wechat/notify
  alipay:
    app-id: ""
    private-key-path: /path/to/private_key.pem
    alipay-public-key-path: /path/to/alipay_public_key.pem
    notify-url: http://localhost:8090/test/pay/alipay/notify
```

- [ ] **Step 7: 编译**

```bash
mvn compile -pl pay-test
```

Expected: BUILD SUCCESS

---

### Task 7: 全量编译验证

- [ ] **Step 1: 全量 clean compile**

```bash
cd /Users/sunan/java_project/demo/yt-parent && mvn clean compile
```

Expected: 所有模块 BUILD SUCCESS

Reactor order: yt-parent → ocr-spring-boot-starter → third-sso-starter → sso-common → sso-dingtalk-starter → sso-wechat-starter → sso-wechat-open-starter → sso-feishu-starter → sso-all-starter → third-pay-starter → pay-common → pay-wechat-starter → pay-alipay-starter → pay-all-starter → sso-test → pay-test
