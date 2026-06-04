# SSO Starter 模块拆分实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `code/` 目录下的钉钉/企业微信 SSO 代码迁移到 `third-sso-starter/`，拆分为 4 个独立模块（sso-common、sso-dingtalk-starter、sso-wechat-starter、sso-all-starter），支持按需引用。

**Architecture:** 4 个子模块 — sso-common 定义统一接口，sso-dingtalk-starter/sso-wechat-starter 各自实现并自动装配，sso-all-starter 空壳聚合。遵循 Spring Boot 3.2 `@AutoConfiguration` + `AutoConfiguration.imports` 机制（参考 ocr-spring-boot-starter）。

**Tech Stack:** Spring Boot 3.2, hutool 5.8.34, Alibaba DingTalk SDK 2.1.98, Lombok, Java 17

---

## 最终模块结构

```
third-sso-starter/
├── pom.xml                                  (packaging=pom, 管理4个子模块)
├── sso-common/
│   ├── pom.xml
│   └── src/main/java/com/yt/sso/
│       └── SsoService.java
├── sso-dingtalk-starter/
│   ├── pom.xml
│   └── src/main/java/com/yt/sso/dingtalk/
│       ├── DingtalkSSOService.java
│       ├── DingtalkSSOServiceImpl.java
│       ├── DingTalkUtils.java
│       └── config/
│           ├── DingTalkProperties.java
│           └── DingTalkAutoConfiguration.java
│   └── src/main/resources/META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
├── sso-wechat-starter/
│   ├── pom.xml
│   └── src/main/java/com/yt/sso/wechat/
│       ├── WechatSSOService.java
│       ├── WechatSSOServiceImpl.java
│       ├── WechatUtils.java
│       └── config/
│           ├── WechatProperties.java
│           └── WechatAutoConfiguration.java
│   └── src/main/resources/META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── sso-all-starter/
    └── pom.xml
```

---

### Task 0: 前置准备 — 修改父 POM 添加 hutool 版本管理

**Files:**
- Modify: `pom.xml` (项目根)
- Modify: `third-sso-starter/pom.xml`

- [ ] **Step 1: 在父 POM dependencyManagement 中添加 hutool**

在 `pom.xml` 的 `<dependencyManagement>` 中，`spring-boot-dependencies` 之后添加：

```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
    <version>5.8.34</version>
</dependency>
```

- [ ] **Step 2: 将 third-sso-starter/pom.xml 改为聚合 POM**

当前 `third-sso-starter/pom.xml` 是 jar 模块。替换为：

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

    <artifactId>third-sso-starter</artifactId>
    <packaging>pom</packaging>
    <name>third-sso-starter</name>
    <description>第三方SSO认证集成 - 聚合模块 (钉钉/企业微信)</description>

    <modules>
        <module>sso-common</module>
        <module>sso-dingtalk-starter</module>
        <module>sso-wechat-starter</module>
        <module>sso-all-starter</module>
    </modules>

</project>
```

- [ ] **Step 3: 验证 Maven 能识别聚合 POM**

```bash
cd /Users/sunan/java_project/demo/yt-parent && mvn validate -pl third-sso-starter
```

Expected: BUILD SUCCESS（子模块目录还没创建，预期会有警告但不会失败，因为 Maven 还没扫描子模块目录）

---

### Task 1: 创建 sso-common 模块

**Files:**
- Create: `third-sso-starter/sso-common/pom.xml`
- Create: `third-sso-starter/sso-common/src/main/java/com/yt/sso/SsoService.java`

- [ ] **Step 1: 创建 sso-common/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.yt.third</groupId>
        <artifactId>third-sso-starter</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>sso-common</artifactId>
    <packaging>jar</packaging>
    <name>sso-common</name>
    <description>SSO 公共接口定义</description>

    <dependencies>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>
    </dependencies>

</project>
```

- [ ] **Step 2: 创建 SsoService 统一接口**

`third-sso-starter/sso-common/src/main/java/com/yt/sso/SsoService.java`:

```java
package com.yt.sso;

import cn.hutool.json.JSONObject;

/**
 * SSO 统一服务接口
 *
 * @author sunan
 */
public interface SsoService {

    /**
     * 通过授权码获取用户信息
     *
     * @param authCode 授权码
     * @return 用户信息
     * @throws RuntimeException 获取失败时抛出
     */
    JSONObject getUserInfoByCode(String authCode);

    /**
     * 根据用户ID获取用户详情
     *
     * @param userId 用户ID
     * @return 用户详情
     * @throws RuntimeException 获取失败时抛出
     */
    JSONObject getUserByUserId(String userId);
}
```

- [ ] **Step 3: 编译 sso-common**

```bash
cd /Users/sunan/java_project/demo/yt-parent && mvn compile -pl third-sso-starter/sso-common
```

Expected: BUILD SUCCESS

---

### Task 2: 创建 sso-dingtalk-starter 模块

**Files:**
- Create: `third-sso-starter/sso-dingtalk-starter/pom.xml`
- Create: `third-sso-starter/sso-dingtalk-starter/src/main/java/com/yt/sso/dingtalk/config/DingTalkProperties.java`
- Create: `third-sso-starter/sso-dingtalk-starter/src/main/java/com/yt/sso/dingtalk/DingTalkUtils.java`
- Create: `third-sso-starter/sso-dingtalk-starter/src/main/java/com/yt/sso/dingtalk/DingtalkSSOService.java`
- Create: `third-sso-starter/sso-dingtalk-starter/src/main/java/com/yt/sso/dingtalk/DingtalkSSOServiceImpl.java`
- Create: `third-sso-starter/sso-dingtalk-starter/src/main/java/com/yt/sso/dingtalk/config/DingTalkAutoConfiguration.java`
- Create: `third-sso-starter/sso-dingtalk-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: 创建 sso-dingtalk-starter/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.yt.third</groupId>
        <artifactId>third-sso-starter</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>sso-dingtalk-starter</artifactId>
    <packaging>jar</packaging>
    <name>sso-dingtalk-starter</name>
    <description>钉钉 SSO Spring Boot Starter</description>

    <dependencies>
        <!-- 公共接口 -->
        <dependency>
            <groupId>com.yt.third</groupId>
            <artifactId>sso-common</artifactId>
        </dependency>

        <!-- 钉钉 SDK -->
        <dependency>
            <groupId>com.aliyun</groupId>
            <artifactId>dingtalk</artifactId>
            <version>2.1.98</version>
        </dependency>

        <!-- hutool -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>

        <!-- Spring Boot -->
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
    </dependencies>

</project>
```

- [ ] **Step 2: 创建 DingTalkProperties.java**

`third-sso-starter/sso-dingtalk-starter/src/main/java/com/yt/sso/dingtalk/config/DingTalkProperties.java`:

```java
package com.yt.sso.dingtalk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 钉钉配置属性
 *
 * @author sunan
 */
@Data
@ConfigurationProperties("sso.dingtalk")
public class DingTalkProperties {

    /** 钉钉 API 基础地址 */
    private String baseApi = "https://oapi.dingtalk.com";

    /** 获取 token 接口 */
    private String tokenApi = "/gettoken";

    /** 获取部门用户接口 */
    private String deptUserApi = "/topapi/v2/user/list";

    /** 根据 unionId 获取 userId 接口 */
    private String userIdApi = "/topapi/user/getbyunionid";

    /** 获取用户信息接口 */
    private String userApi = "/topapi/v2/user/get";

    /** 应用名称 */
    private String appName = "钉钉通知";

    /** 所属组织 CorpId */
    private String corpId;

    /** 钉钉应用的 AppKey */
    private String clientId;

    /** 钉钉应用的 AppSecret */
    private String clientSecret;

    /** 钉钉应用的 agentId */
    private Long agentId;
}
```

- [ ] **Step 3: 创建 DingTalkUtils.java**

`third-sso-starter/sso-dingtalk-starter/src/main/java/com/yt/sso/dingtalk/DingTalkUtils.java`:

```java
package com.yt.sso.dingtalk;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yt.sso.dingtalk.config.DingTalkProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 钉钉 accessToken 管理工具
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class DingTalkUtils {

    /** token 有效期（30 分钟） */
    private static final long EXPIRE_MILLIS = 30 * 60 * 1000L;

    /** 内存缓存 */
    private static volatile String accessToken;

    /** token 过期时间点 */
    private static volatile long expireAt = 0L;

    /** 锁对象 */
    private static final Object LOCK = new Object();

    private final DingTalkProperties dingTalkProperties;

    /**
     * 获取 accessToken（带缓存）
     */
    public String getAccessToken() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < expireAt) {
            return accessToken;
        }
        synchronized (LOCK) {
            now = System.currentTimeMillis();
            if (accessToken != null && now < expireAt) {
                return accessToken;
            }
            String tokenResult = httpAccessToken();
            accessToken = tokenResult;
            expireAt = now + EXPIRE_MILLIS;
            return accessToken;
        }
    }

    private String httpAccessToken() {
        HttpResponse response = HttpUtil.createGet(dingTalkProperties.getBaseApi() + dingTalkProperties.getTokenApi())
                .form("appkey", dingTalkProperties.getClientId())
                .form("appsecret", dingTalkProperties.getClientSecret())
                .execute();

        JSONObject result = JSONUtil.parseObj(response.body());
        if (!"0".equals(result.getStr("errcode"))) {
            throw new RuntimeException("获取钉钉 access_token 失败：" + result);
        }
        return result.getStr("access_token");
    }
}
```

- [ ] **Step 4: 创建 DingtalkSSOService 接口**

`third-sso-starter/sso-dingtalk-starter/src/main/java/com/yt/sso/dingtalk/DingtalkSSOService.java`:

```java
package com.yt.sso.dingtalk;

import com.yt.sso.SsoService;

/**
 * 钉钉 SSO 服务接口
 *
 * @author sunan
 */
public interface DingtalkSSOService extends SsoService {

    /**
     * 根据 unionId 获取 userId
     *
     * @param unionId 钉钉 unionId
     * @return userId
     * @throws RuntimeException 获取失败时抛出
     */
    String getUserIdByUnionId(String unionId);
}
```

- [ ] **Step 5: 创建 DingtalkSSOServiceImpl.java**

`third-sso-starter/sso-dingtalk-starter/src/main/java/com/yt/sso/dingtalk/DingtalkSSOServiceImpl.java`:

```java
package com.yt.sso.dingtalk;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aliyun.dingtalkcontact_1_0.Client;
import com.aliyun.dingtalkcontact_1_0.models.GetUserHeaders;
import com.aliyun.dingtalkcontact_1_0.models.GetUserResponse;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenRequest;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.yt.sso.dingtalk.config.DingTalkProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉 SSO 服务实现
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class DingtalkSSOServiceImpl implements DingtalkSSOService {

    private final DingTalkProperties dingTalkProperties;
    private final DingTalkUtils dingTalkUtils;

    @Override
    public JSONObject getUserInfoByCode(String authCode) {
        String accessToken = getAccessTokenByCode(authCode);
        return getUserInfoByAccessToken(accessToken);
    }

    @Override
    public String getUserIdByUnionId(String unionId) {
        String accessToken = dingTalkUtils.getAccessToken();
        Map<String, Object> body = new HashMap<>();
        body.put("unionid", unionId);
        String url = dingTalkProperties.getBaseApi() + dingTalkProperties.getUserIdApi()
                + "?access_token=" + accessToken;
        log.info("请求钉钉 getUserIdByUnionId，参数：{}", JSONUtil.toJsonStr(body));
        HttpResponse response = HttpUtil.createPost(url)
                .body(JSONUtil.toJsonStr(body), ContentType.JSON.getValue())
                .execute();
        JSONObject returnBody = JSONUtil.parseObj(response.body());
        if (!"0".equals(returnBody.getStr("errcode"))) {
            throw new RuntimeException("获取钉钉用户ID失败：" + returnBody);
        }
        return returnBody.getJSONObject("result").getStr("userid");
    }

    @Override
    public JSONObject getUserByUserId(String userId) {
        String accessToken = dingTalkUtils.getAccessToken();
        Map<String, Object> body = new HashMap<>();
        body.put("userid", userId);
        String url = dingTalkProperties.getBaseApi() + dingTalkProperties.getUserApi()
                + "?access_token=" + accessToken;
        log.info("请求钉钉 getUserByUserId，参数：{}", JSONUtil.toJsonStr(body));
        HttpResponse response = HttpUtil.createPost(url)
                .body(JSONUtil.toJsonStr(body), ContentType.JSON.getValue())
                .execute();
        JSONObject returnBody = JSONUtil.parseObj(response.body());
        if (!"0".equals(returnBody.getStr("errcode"))) {
            throw new RuntimeException("获取钉钉用户信息失败：" + returnBody);
        }
        return returnBody.getJSONObject("result");
    }

    /**
     * 通过 OAuth2 授权码获取 accessToken（使用钉钉 SDK）
     */
    private String getAccessTokenByCode(String authCode) {
        Config config = new Config();
        config.protocol = "https";
        config.regionId = "central";
        try {
            com.aliyun.dingtalkoauth2_1_0.Client client =
                    new com.aliyun.dingtalkoauth2_1_0.Client(config);
            GetUserTokenRequest request = new GetUserTokenRequest()
                    .setClientId(dingTalkProperties.getClientId())
                    .setClientSecret(dingTalkProperties.getClientSecret())
                    .setCode(authCode)
                    .setGrantType("authorization_code");
            GetUserTokenResponse response = client.getUserToken(request);
            return response.getBody().getAccessToken();
        } catch (Exception e) {
            throw new RuntimeException("获取钉钉 accessToken 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过 accessToken 获取钉钉用户信息（使用钉钉 SDK）
     */
    private JSONObject getUserInfoByAccessToken(String accessToken) {
        Config config = new Config();
        config.protocol = "https";
        config.regionId = "central";
        Client client;
        try {
            client = new Client(config);
        } catch (Exception e) {
            throw new RuntimeException("初始化钉钉 Client 失败: " + e.getMessage(), e);
        }
        GetUserHeaders headers = new GetUserHeaders();
        headers.xAcsDingtalkAccessToken = accessToken;
        JSONObject user = new JSONObject();
        try {
            GetUserResponse resp = client.getUserWithOptions("me", headers, new RuntimeOptions());
            user.put("unionid", resp.getBody().getUnionId());
            user.put("phone", resp.getBody().getMobile());
            user.put("nick", resp.getBody().getNick());
            user.put("email", resp.getBody().getEmail());
            user.put("avatarUrl", resp.getBody().getAvatarUrl());
            user.put("stateCode", resp.getBody().getStateCode());
        } catch (TeaException e) {
            throw new RuntimeException("钉钉接口异常: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("获取钉钉用户信息失败: " + e.getMessage(), e);
        }
        return user;
    }
}
```

- [ ] **Step 6: 创建 DingTalkAutoConfiguration.java**

`third-sso-starter/sso-dingtalk-starter/src/main/java/com/yt/sso/dingtalk/config/DingTalkAutoConfiguration.java`:

```java
package com.yt.sso.dingtalk.config;

import com.yt.sso.dingtalk.DingTalkUtils;
import com.yt.sso.dingtalk.DingtalkSSOService;
import com.yt.sso.dingtalk.DingtalkSSOServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 钉钉 SSO 自动装配
 *
 * @author sunan
 */
@AutoConfiguration
@EnableConfigurationProperties(DingTalkProperties.class)
public class DingTalkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DingTalkUtils dingTalkUtils(DingTalkProperties properties) {
        return new DingTalkUtils(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DingtalkSSOService dingtalkSSOService(DingTalkProperties properties, DingTalkUtils utils) {
        return new DingtalkSSOServiceImpl(properties, utils);
    }
}
```

- [ ] **Step 7: 创建 AutoConfiguration.imports**

`third-sso-starter/sso-dingtalk-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.yt.sso.dingtalk.config.DingTalkAutoConfiguration
```

- [ ] **Step 8: 编译 sso-dingtalk-starter**

```bash
cd /Users/sunan/java_project/demo/yt-parent && mvn compile -pl third-sso-starter/sso-dingtalk-starter
```

Expected: BUILD SUCCESS

---

### Task 3: 创建 sso-wechat-starter 模块

**Files:**
- Create: `third-sso-starter/sso-wechat-starter/pom.xml`
- Create: `third-sso-starter/sso-wechat-starter/src/main/java/com/yt/sso/wechat/config/WechatProperties.java`
- Create: `third-sso-starter/sso-wechat-starter/src/main/java/com/yt/sso/wechat/WechatUtils.java`
- Create: `third-sso-starter/sso-wechat-starter/src/main/java/com/yt/sso/wechat/WechatSSOService.java`
- Create: `third-sso-starter/sso-wechat-starter/src/main/java/com/yt/sso/wechat/WechatSSOServiceImpl.java`
- Create: `third-sso-starter/sso-wechat-starter/src/main/java/com/yt/sso/wechat/config/WechatAutoConfiguration.java`
- Create: `third-sso-starter/sso-wechat-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: 创建 sso-wechat-starter/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.yt.third</groupId>
        <artifactId>third-sso-starter</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>sso-wechat-starter</artifactId>
    <packaging>jar</packaging>
    <name>sso-wechat-starter</name>
    <description>企业微信 SSO Spring Boot Starter</description>

    <dependencies>
        <!-- 公共接口 -->
        <dependency>
            <groupId>com.yt.third</groupId>
            <artifactId>sso-common</artifactId>
        </dependency>

        <!-- hutool -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
        </dependency>

        <!-- Spring Boot -->
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
    </dependencies>

</project>
```

- [ ] **Step 2: 创建 WechatProperties.java**

`third-sso-starter/sso-wechat-starter/src/main/java/com/yt/sso/wechat/config/WechatProperties.java`:

```java
package com.yt.sso.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业微信配置属性
 *
 * @author sunan
 */
@Data
@ConfigurationProperties("sso.wechat")
public class WechatProperties {

    /** 企业微信登录地址 */
    private String loginApi = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";

    /** API 基础地址 */
    private String baseUri = "https://qyapi.weixin.qq.com";

    /** 获取 token 接口 */
    private String tokenUri = "/cgi-bin/gettoken";

    /** 获取用户ID接口 */
    private String userIdUri = "/cgi-bin/auth/getuserinfo";

    /** 获取用户详情接口 */
    private String userInfoUri = "/cgi-bin/user/get";

    /** 回调地址 */
    private String redirectUri = "https://auth.com";

    /** 应用 AgentId */
    private String agentId;

    /** 企业 CorpId */
    private String clientId;

    /** 应用 Secret */
    private String secret;
}
```

- [ ] **Step 3: 创建 WechatUtils.java**

`third-sso-starter/sso-wechat-starter/src/main/java/com/yt/sso/wechat/WechatUtils.java`:

```java
package com.yt.sso.wechat;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yt.sso.wechat.config.WechatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 企业微信 accessToken 管理与 API 调用工具
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class WechatUtils {

    /** token 有效期（30 分钟） */
    private static final long EXPIRE_MILLIS = 30 * 60 * 1000L;

    /** 内存缓存 */
    private static volatile String accessToken;

    /** token 过期时间点 */
    private static volatile long expireAt = 0L;

    /** 锁对象 */
    private static final Object LOCK = new Object();

    private final WechatProperties wechatProperties;

    /**
     * 获取 accessToken（带缓存）
     */
    public String getAccessToken() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < expireAt) {
            return accessToken;
        }
        synchronized (LOCK) {
            now = System.currentTimeMillis();
            if (accessToken != null && now < expireAt) {
                return accessToken;
            }
            String tokenResult = httpAccessToken();
            accessToken = tokenResult;
            expireAt = now + EXPIRE_MILLIS;
            return accessToken;
        }
    }

    /**
     * 通过授权码获取 userId
     */
    public String getUserId(String code) {
        String token = getAccessToken();
        String url = wechatProperties.getBaseUri() + wechatProperties.getUserIdUri()
                + "?access_token=" + token + "&code=" + code;
        HttpResponse response = HttpUtil.createGet(url).execute();
        JSONObject result = JSONUtil.parseObj(response.body());
        if (!"0".equals(result.getStr("errcode"))) {
            throw new RuntimeException("获取企业微信用户ID失败：" + result);
        }
        return result.getStr("userid");
    }

    /**
     * 根据 wechatUserId 获取用户详情
     */
    public JSONObject getUserDetail(String wechatUserId) {
        String token = getAccessToken();
        String url = wechatProperties.getBaseUri() + wechatProperties.getUserInfoUri()
                + "?access_token=" + token + "&userid=" + wechatUserId;
        HttpResponse response = HttpUtil.createGet(url).execute();
        JSONObject result = JSONUtil.parseObj(response.body());
        if (!"0".equals(result.getStr("errcode"))) {
            throw new RuntimeException("获取企业微信用户详情失败：" + result);
        }
        return result.getJSONObject("user");
    }

    private String httpAccessToken() {
        HttpResponse response = HttpUtil.createGet(wechatProperties.getBaseUri() + wechatProperties.getTokenUri())
                .form("corpid", wechatProperties.getClientId())
                .form("corpsecret", wechatProperties.getSecret())
                .execute();
        JSONObject result = JSONUtil.parseObj(response.body());
        if (!"0".equals(result.getStr("errcode"))) {
            throw new RuntimeException("获取企业微信 access_token 失败：" + result);
        }
        return result.getStr("access_token");
    }
}
```

- [ ] **Step 4: 创建 WechatSSOService 接口**

`third-sso-starter/sso-wechat-starter/src/main/java/com/yt/sso/wechat/WechatSSOService.java`:

```java
package com.yt.sso.wechat;

import com.yt.sso.SsoService;

/**
 * 企业微信 SSO 服务接口
 *
 * @author sunan
 */
public interface WechatSSOService extends SsoService {
}
```

- [ ] **Step 5: 创建 WechatSSOServiceImpl.java**

`third-sso-starter/sso-wechat-starter/src/main/java/com/yt/sso/wechat/WechatSSOServiceImpl.java`:

```java
package com.yt.sso.wechat;

import cn.hutool.json.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 企业微信 SSO 服务实现
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class WechatSSOServiceImpl implements WechatSSOService {

    private final WechatUtils wechatUtils;

    @Override
    public JSONObject getUserInfoByCode(String authCode) {
        String userId = wechatUtils.getUserId(authCode);
        return wechatUtils.getUserDetail(userId);
    }

    @Override
    public JSONObject getUserByUserId(String userId) {
        return wechatUtils.getUserDetail(userId);
    }
}
```

- [ ] **Step 6: 创建 WechatAutoConfiguration.java**

`third-sso-starter/sso-wechat-starter/src/main/java/com/yt/sso/wechat/config/WechatAutoConfiguration.java`:

```java
package com.yt.sso.wechat.config;

import com.yt.sso.wechat.WechatSSOService;
import com.yt.sso.wechat.WechatSSOServiceImpl;
import com.yt.sso.wechat.WechatUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 企业微信 SSO 自动装配
 *
 * @author sunan
 */
@AutoConfiguration
@EnableConfigurationProperties(WechatProperties.class)
public class WechatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WechatUtils wechatUtils(WechatProperties properties) {
        return new WechatUtils(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WechatSSOService wechatSSOService(WechatUtils wechatUtils) {
        return new WechatSSOServiceImpl(wechatUtils);
    }
}
```

- [ ] **Step 7: 创建 AutoConfiguration.imports**

`third-sso-starter/sso-wechat-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.yt.sso.wechat.config.WechatAutoConfiguration
```

- [ ] **Step 8: 编译 sso-wechat-starter**

```bash
cd /Users/sunan/java_project/demo/yt-parent && mvn compile -pl third-sso-starter/sso-wechat-starter
```

Expected: BUILD SUCCESS

---

### Task 4: 创建 sso-all-starter 聚合模块

**Files:**
- Create: `third-sso-starter/sso-all-starter/pom.xml`

- [ ] **Step 1: 创建 sso-all-starter/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.yt.third</groupId>
        <artifactId>third-sso-starter</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>sso-all-starter</artifactId>
    <packaging>jar</packaging>
    <name>sso-all-starter</name>
    <description>SSO 全家桶 - 同时包含钉钉和企业微信</description>

    <dependencies>
        <dependency>
            <groupId>com.yt.third</groupId>
            <artifactId>sso-dingtalk-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.yt.third</groupId>
            <artifactId>sso-wechat-starter</artifactId>
        </dependency>
    </dependencies>

</project>
```

- [ ] **Step 2: 编译 sso-all-starter**

```bash
cd /Users/sunan/java_project/demo/yt-parent && mvn compile -pl third-sso-starter/sso-all-starter
```

Expected: BUILD SUCCESS（依赖传递自动引入 dingtalk + wechat）

---

### Task 5: 全量编译验证

- [ ] **Step 1: 从父 POM 全量编译所有模块**

```bash
cd /Users/sunan/java_project/demo/yt-parent && mvn clean compile
```

Expected: 所有模块 BUILD SUCCESS

- [ ] **Step 2: 验证模块列表**

```bash
cd /Users/sunan/java_project/demo/yt-parent && mvn validate
```

Expected: 显示 Reactor Build Order 包含 sso-common → sso-dingtalk-starter → sso-wechat-starter → sso-all-starter → ocr-spring-boot-starter

---

## 使用方配置参考

```yaml
# 钉钉
sso:
  dingtalk:
    client-id: your-app-key
    client-secret: your-app-secret
    agent-id: 123456
    corp-id: your-corp-id

# 企业微信
sso:
  wechat:
    client-id: your-corp-id
    secret: your-corp-secret
```

## 使用方注入示例

```java
// 钉钉
@Autowired
private DingtalkSSOService dingtalkSSOService;

// 企业微信
@Autowired
private WechatSSOService wechatSSOService;
```
