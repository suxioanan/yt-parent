package com.yt.sso.feishu;

import com.yt.sso.build.LoginState;
import com.yt.sso.feishu.config.FeishuProperties;
import com.yt.sso.model.SsoUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 飞书 SSO 服务实现
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class FeishuSSOServiceImpl implements FeishuSSOService {

    private final FeishuProperties feishuProperties;
    private final FeishuUtils feishuUtils;

    @Override
    public String getSSOScanUri() {
        String baseUrl = feishuProperties.getLoginApi();
        String encodedUrl = feishuProperties.getRedirectUri();
        LoginState state = LoginState.system();
        String encodedState = encodeState(state);
        try {
            encodedUrl = URLEncoder.encode(encodedUrl, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            log.error("转码失败:{}", e.getMessage(), e);
        }

        String url = baseUrl
                + "?app_id=" + feishuProperties.getClientId()
                + "&redirect_uri=" + encodedUrl
                + "&state=" + encodedState;
        return url;
    }

    @Override
    public SsoUser getUserInfoByCode(String authCode) {
        // 1. 用 code 换 user_access_token
        String userAccessToken = feishuUtils.getUserAccessToken(authCode);
        // 2. 用 user_access_token 获取登录用户信息
        return feishuUtils.getUserInfo(userAccessToken);
    }

    @Override
    public SsoUser getUserByUserId(String userId) {
        return feishuUtils.getUserByUserId(userId);
    }
}
