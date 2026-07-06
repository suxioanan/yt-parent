package com.yt.sso.test.controller;

import com.yt.sso.feishu.FeishuSSOService;
import com.yt.sso.model.SsoUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 飞书 SSO 测试接口
 */
@RestController
@RequestMapping("/test/feishu")
public class FeishuTestController {

    @Autowired
    private FeishuSSOService feishuSSOService;

    /**
     * 获取授权码登录的 URL
     * <p>GET /test/feishu/url
     */
    @GetMapping("/url")
    public String getUrl() {
        return feishuSSOService.getSSOScanUri();
    }

    /**
     * 授权码登录获取用户信息
     * <p>GET /test/feishu/login?authCode=xxx
     */
    @GetMapping("/login")
    public Map<String, Object> login(@RequestParam("authCode") String authCode) {
        try {
            SsoUser user = feishuSSOService.getUserInfoByCode(authCode);
            return Map.of("success", true, "data", user);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 根据 userId 获取用户详情
     * <p>GET /test/feishu/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public Map<String, Object> getUserByUserId(@PathVariable("userId") String userId) {
        try {
            SsoUser user = feishuSSOService.getUserByUserId(userId);
            return Map.of("success", true, "data", user);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
