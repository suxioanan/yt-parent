package com.yt.sso.test.controller;

import com.yt.sso.model.SsoUser;
import com.yt.sso.wechat.WechatSSOService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 企业微信 SSO 测试接口
 */
@RestController
@RequestMapping("/test/wechat")
public class WechatTestController {

    @Autowired
    private WechatSSOService wechatSSOService;

    /**
     * 获取授权码登录的 URL
     * <p>GET /test/wechat/login?authCode=xxx
     */
    @GetMapping("/url")
    public String login() {
        return wechatSSOService.getSSOScanUri();
    }
    /**
     * 授权码登录获取用户信息
     * <p>GET /test/wechat/login?authCode=xxx
     */
    @GetMapping("/login")
    public Map<String, Object> login(@RequestParam("authCode") String authCode) {
        try {
            return Map.of("success", true, "data", wechatSSOService.getUserInfoByCode(authCode));
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 根据 userId 获取用户详情
     * <p>GET /test/wechat/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public Map<String, Object> getUserByUserId(@PathVariable String userId) {
        try {
            SsoUser user = wechatSSOService.getUserByUserId(userId);
            return Map.of("success", true, "data", user);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
