package com.yt.sso.test.controller;

import com.yt.sso.dingtalk.DingtalkSSOService;
import com.yt.sso.model.SsoUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 钉钉 SSO 测试接口
 */
@RestController
@RequestMapping("/test/dingtalk")
public class DingtalkTestController {

    @Autowired
    private DingtalkSSOService dingtalkSSOService;

    /**
     * 获取授权码登录的 URL
     * <p>GET /test/wechat/login?authCode=xxx
     */
    @GetMapping("/url")
    public String login() {
        return dingtalkSSOService.getSSOScanUri();
    }

    /**
     * 授权码登录获取用户信息
     * <p>GET /test/dingtalk/login?authCode=xxx
     */
    @GetMapping("/login")
    public Map<String, Object> login(@RequestParam("authCode") String authCode) {
        try {
            return Map.of("success", true, "data", dingtalkSSOService.getUserInfoByCode(authCode));
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 根据 userId 获取用户详情
     * <p>GET /test/dingtalk/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public Map<String, Object> getUserByUserId(@PathVariable("userId") String userId) {
        try {
            SsoUser user = dingtalkSSOService.getUserByUserId(userId);
            return Map.of("success", true, "data", user);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 根据 unionId 获取 userId
     * <p>GET /test/dingtalk/unionid/{unionId}
     */
    @GetMapping("/unionid/{unionId}")
    public Map<String, Object> getUserIdByUnionId(@PathVariable("unionId") String unionId) {
        try {
            String userId = dingtalkSSOService.getUserIdByUnionId(unionId);
            return Map.of("success", true, "data", Map.of("userId", userId));
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
