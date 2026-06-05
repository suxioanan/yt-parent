package com.yt.sso.dingtalk;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yt.sso.dingtalk.config.DingTalkProperties;
import com.yt.sso.model.SsoUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 钉钉 SSO 服务实现
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class DingtalkSSOServiceImpl implements DingtalkSSOService {

    private final DingTalkProperties dingTalkProperties;

    private final DingTalkUtils dingTalkUtils;

    @Override
    public String getSSOScanUri() {
        String baseUrl = dingTalkProperties.getLoginApi();
        String encodedUrl = dingTalkProperties.getRedirectUri();
        try {
            encodedUrl = URLEncoder.encode(encodedUrl, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            log.error("转码失败:{}", e.getMessage(), e);
        }
        String dingTalkParams = "&response_type=code&client_id="
                + dingTalkProperties.getClientId()
                + "&scope=openid&state=dddd&prompt=consent";
        return baseUrl + "?redirect_uri=" + encodedUrl + dingTalkParams;
    }

    @Override
    public SsoUser getUserInfoByCode(String authCode) {
        String accessToken = getAccessTokenByCode(authCode);
        return getUserInfoByAccessToken(accessToken);
    }

    @Override
    public String getUserIdByUnionId(String unionId) {
        return dingTalkUtils.getUserIdByUnionId(unionId);
    }

    @Override
    public SsoUser getUserByUserId(String userId) {
        return dingTalkUtils.getUserByUserId(userId);
    }

    // ==================== OAuth2 纯 HTTP 实现 ====================

    /**
     * 通过 OAuth2 授权码获取 userAccessToken
     * <p>POST https://api.dingtalk.com/v1.0/oauth2/userAccessToken
     */
    private String getAccessTokenByCode(String authCode) {
        JSONObject body = new JSONObject();
        body.put("clientId", dingTalkProperties.getClientId());
        body.put("clientSecret", dingTalkProperties.getClientSecret());
        body.put("code", authCode);
        body.put("grantType", "authorization_code");

        String url = dingTalkProperties.getNewBaseApi() + dingTalkProperties.getUserTokenApi();
        log.info("请求钉钉 getAccessTokenByCode, url={},body={}", url, JSONUtil.toJsonStr(body));
        try (HttpResponse response = HttpUtil.createPost(url)
                .body(JSONUtil.toJsonStr(body))
                .execute()) {
            JSONObject result = JSONUtil.parseObj(response.body());
            log.info("钉钉 getAccessTokenByCode 返回：{}", result);

            if (result.getStr("accessToken") == null) {
                throw new RuntimeException("获取钉钉 accessToken 失败：" + result);
            }
            return result.getStr("accessToken");
        }

    }

    /**
     * 通过 userAccessToken 获取钉钉用户信息
     * <p>GET https://api.dingtalk.com/v1.0/contact/users/me
     * <p>Header: x-acs-dingtalk-access-token: {accessToken}
     */
    private SsoUser getUserInfoByAccessToken(String accessToken) {
        String url = dingTalkProperties.getNewBaseApi() + dingTalkProperties.getContactUserApi();
        log.info("请求钉钉 getUserInfo, url={}", url);
        JSONObject result;
        try (HttpResponse response = HttpUtil.createGet(url)
                .header("x-acs-dingtalk-access-token", accessToken)
                .execute()) {
            result = JSONUtil.parseObj(response.body());
            log.info("钉钉 getUserInfo 返回：{}", result);
            if (result.containsKey("code") && !"0".equals(result.getStr("code"))) {
                throw new RuntimeException("获取钉钉用户信息失败：" + result);
            }
        }
        SsoUser ssoUser = new SsoUser();
        ssoUser.setName(result.getStr("nick"));
        ssoUser.setAvatar(result.getStr("avatarUrl"));
        ssoUser.setMobile(result.getStr("mobile"));
        ssoUser.setEmail(result.getStr("email"));
        ssoUser.setOpenId(result.getStr("openId"));
        ssoUser.setUnionId(result.getStr("unionId"));
        ssoUser.setJobNumber(null);
        ssoUser.setSource("dingtalk");
        return ssoUser;
    }
}
