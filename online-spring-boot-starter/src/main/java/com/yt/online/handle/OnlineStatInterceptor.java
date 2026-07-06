package com.yt.online.handle;

import com.yt.online.service.IOnlineStatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 在线统计拦截器。
 * <p>
 * 通过拦截请求记录用户在线时长。用户身份解析委托给 {@link OnlineUserResolver}，
 * 引入方可实现该接口并注册为 Spring Bean 来自定义解析逻辑。
 *
 * @author sunan
 */
@Slf4j
public class OnlineStatInterceptor implements HandlerInterceptor {

    private final IOnlineStatService onlineStatService;

    private final OnlineUserResolver onlineUserResolver;

    public OnlineStatInterceptor(IOnlineStatService onlineStatService,
                                 OnlineUserResolver onlineUserResolver) {
        this.onlineStatService = onlineStatService;
        this.onlineUserResolver = onlineUserResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        try {
            String userId = onlineUserResolver.resolveUserId(request);
            String tenantId = onlineUserResolver.resolveTenantId(request);
            if (userId != null && tenantId != null) {
                onlineStatService.recordOnlineDuration(userId, tenantId);
            }
        } catch (Exception e) {
            // 绝对不能影响主流程
            log.debug("OnlineStatInterceptor error: {}", e.getMessage());
        }
        return true;
    }
}
