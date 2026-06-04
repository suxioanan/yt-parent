package com.yt.sso.dingtalk;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aliyun.dingtalkcontact_1_0.Client;
import com.aliyun.dingtalkcontact_1_0.models.GetUserHeaders;
import com.aliyun.dingtalkcontact_1_0.models.GetUserResponse;
import com.aliyun.dingtalkcontact_1_0.models.GetUserResponseBody;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenRequest;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.yt.sso.dingtalk.config.DingTalkProperties;
import com.yt.sso.model.SsoUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    public String getSSOScanUri() {
        String baseUrl = dingTalkProperties.getLoginApi();
        String encodedUrl = dingTalkProperties.getRedirectUri();
        try {
            encodedUrl = URLEncoder.encode(encodedUrl, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            log.error("转码失败:{}", e.getMessage(), e);
        }
        String dingTalkParamers = "&response_type=code&client_id=" + dingTalkProperties.getClientId() + "&scope=openid&state=dddd&prompt=consent";
        String url = baseUrl + "?redirect_uri=" + encodedUrl + dingTalkParamers;
        return url;
    }

    @Override
    public SsoUser getUserInfoByCode(String authCode) {
        String accessToken = getAccessTokenByCode(authCode);
        return getUserInfoByAccessToken(accessToken);
    }

    @Override
    public String getUserIdByUnionId(String unionId) {
        String userId = dingTalkUtils.getUserIdByUnionId(unionId);
        return userId;
    }

    @Override
    public SsoUser getUserByUserId(String userId) {
        SsoUser user = dingTalkUtils.getUserByUserId(userId);
        return user;

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
    private SsoUser getUserInfoByAccessToken(String accessToken) {
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
        SsoUser ssoUser =new SsoUser();
        try {
            GetUserResponse resp = client.getUserWithOptions("me", headers, new RuntimeOptions());
            GetUserResponseBody body = resp.getBody();
            log.info("通过accessToken获取钉钉用户信息: {}", body);
            ssoUser.setAvatar(body.getAvatarUrl());
            ssoUser.setEmail(body.getEmail());
            ssoUser.setMobile(body.getMobile());
            ssoUser.setName(body.getNick());
            ssoUser.setOpenId(body.getOpenId());
            ssoUser.setUnionId(body.getUnionId());
            ssoUser .setSource("dingtalk");
        } catch (TeaException e) {
            throw new RuntimeException("钉钉接口异常: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("获取钉钉用户信息失败: " + e.getMessage(), e);
        }
        return ssoUser;
    }
}
