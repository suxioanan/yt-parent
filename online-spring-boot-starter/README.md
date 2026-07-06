# online-spring-boot-starter

Spring Boot Starter for online user statistics — login tracking and online duration recording.

## 功能

- **登录统计**：用户登录时记录全站及个人的登录次数
- **在线时长统计**：通过拦截 HTTP 请求，按时间窗口记录用户每日在线时长
- **可扩展**：默认基于 Spring Security 获取用户身份，支持选择性覆写解析逻辑

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.yt.third</groupId>
    <artifactId>online-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 启用开关

```yaml
third:
  online:
    enabled: true
```

### 3. 可选配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `third.online.enabled` | `false` | 是否启用在线统计 |
| `third.online.online-interval-minutes` | `5` | 在线时长累计间隔（分钟），两次请求间隔小于此值不重复累计 |
| `third.online.key-expire-days` | `2` | Redis key 过期天数 |

## 自定义用户身份解析

如果你的项目中不使用 Spring Security，或者用户身份获取方式不同（比如从 JWT 解析），只需继承 `OnlineUserResolver` 并选择性覆写需要的方法即可。

### 场景 1：只自定义用户 ID 获取方式

```java
import com.yt.online.handle.OnlineUserResolver;
import jakarta.servlet.http.HttpServletRequest;

public class MyOnlineUserResolver extends OnlineUserResolver {

    @Override
    public Long resolveUserId(HttpServletRequest request) {
        // 从 JWT 或其他方式获取用户ID
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            return Long.valueOf(userId);
        }
        return null;  // 返回 null 表示本次请求不统计
    }
}
```

租户ID 的解析使用 `OnlineUserResolver` 的默认逻辑（从 Header `TENANT-ID` 获取），不需要额外覆写。

### 场景 2：同时自定义用户 ID 和租户 ID

```java
public class MyOnlineUserResolver extends OnlineUserResolver {

    @Override
    public String resolveTenantId(HttpServletRequest request) {
        return request.getAttribute("tenantId").toString();
    }

    @Override
    public Long resolveUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
```

### 场景 3：只自定义租户 ID

```java
public class MyOnlineUserResolver extends OnlineUserResolver {

    @Override
    public String resolveTenantId(HttpServletRequest request) {
        // 比如从 JWT Token 中解析
        return JwtUtil.getTenantId(request);
    }
}
```

> **只需继承 `OnlineUserResolver` 并覆写需要的方法即可，加 `@Component`。** Spring Boot 会自动扫描到你的实现并替换默认逻辑。

### 默认逻辑

不继承 `OnlineUserResolver` 时，框架自带的默认行为：
- 用户ID：从 `SecurityContextHolder` 获取 `Authentication` → `principal.getId()`
- 租户ID：从请求头 `TENANT-ID` 获取

## 模块结构

```
online-spring-boot-starter/
├── build/
│   └── OnlineKeyBuilder.java            # Redis Key 构建工具
├── config/
│   ├── OnlineStatAutoConfiguration.java    # 自动装配（启用入口）
│   ├── OnlineServiceAutoConfiguration.java # 自动装配（配置service入口）
│   └── OnlineStatProperties.java           # 配置属性
├── handle/
│   ├── OnlineStatInterceptor.java       # HTTP 拦截器
│   └── OnlineUserResolver.java          # 用户解析器（可继承覆写）
└── service/
    ├── LoginStatService.java            # 登录统计服务     需要在登陆成功时，进行调用
    └── OnlineStatService.java           # 在线时长统计服务
    └── OnlineService.java               # 获取时长数据
```

## 依赖要求

- Java 17+
- Spring Boot 3.2+
- Redis（通过 `spring-boot-starter-data-redis` 提供）
