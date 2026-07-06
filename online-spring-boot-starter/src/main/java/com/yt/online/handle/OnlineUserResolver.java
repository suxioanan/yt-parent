package com.yt.online.handle;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;

/**
 * 在线用户解析器。
 * <p>
 * 默认通过反射调用 Spring Security 的 {@code SecurityContextHolder} + 请求头获取用户身份。
 * 引入方可以继承此类并选择性覆写方法来定制解析逻辑。
 *
 * @author sunan
 */
@Slf4j
public abstract class OnlineUserResolver {

    private static final String TENANT_ID_HEADER = "TENANT-ID";

    private volatile MethodCache methodCache;

    private static final class MethodCache {
        final Class<?> principalClass;
        final Method getIdMethod;

        MethodCache(Class<?> principalClass, Method getIdMethod) {
            this.principalClass = principalClass;
            this.getIdMethod = getIdMethod;
        }
    }

    /**
     * 从请求头获取租户ID
     */
    public String resolveTenantId(HttpServletRequest request) {
        String header = request.getHeader(TENANT_ID_HEADER);
        if(ObjectUtils.isEmpty( header)){
            return "-1";
        }
        return header;
    }

    /**
     * 从 SecurityContextHolder 获取当前登录用户ID（反射调用，无编译期依赖）
     */
    public String resolveUserId(HttpServletRequest request) {
        try {
            Object authentication = getAuthentication();
            if (authentication == null) {
                return null;
            }
            Object principal = authentication.getClass().getMethod("getPrincipal").invoke(authentication);
            if (principal == null) {
                return null;
            }
            return invokeGetId(principal);
        } catch (Exception e) {
            log.debug("Failed to get current user id: {}", e.getMessage());
        }
        return null;
    }

    private Object getAuthentication() {
        try {
            Class<?> holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = holderClass.getMethod("getContext").invoke(null);
            return context.getClass().getMethod("getAuthentication").invoke(context);
        } catch (Exception e) {
            log.debug("Spring Security not available: {}", e.getMessage());
            return null;
        }
    }

    private String invokeGetId(Object principal) throws Exception {
        Class<?> clazz = principal.getClass();
        MethodCache cache = methodCache;

        if (cache == null || cache.principalClass != clazz) {
            synchronized (this) {
                cache = methodCache;
                if (cache == null || cache.principalClass != clazz) {
                    cache = new MethodCache(clazz, clazz.getMethod("getId"));
                    methodCache = cache;
                }
            }
        }

        Object result = cache.getIdMethod.invoke(principal);
        if(ObjectUtils.isEmpty(result)){
            return null;
        } else {
            return result.toString();
        }
    }

}
