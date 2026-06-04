package com.yt.sso.wechat;

import cn.hutool.json.JSONObject;
import com.yt.sso.build.LoginState;
import com.yt.sso.model.SsoUser;
import com.yt.sso.wechat.config.WechatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 企业微信 SSO 服务实现
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class WechatSSOServiceImpl implements WechatSSOService {

    private final WechatUtils wechatUtils;

    private final WechatProperties wechatProperties;

    @Override
    public String getSSOScanUri() {
        String baseUrl = wechatProperties.getLoginApi();
        String encodedUrl = wechatProperties.getRedirectUri();
        LoginState system = LoginState.system();
        String encodedState = encodeState(system);
        try {
            encodedUrl = URLEncoder.encode(encodedUrl, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            log.error("转码失败:{}", e.getMessage(), e);
        }
        String url = baseUrl
                + "?appid=" + wechatProperties.getClientId()  // ww******
                + "&agentid=" + wechatProperties.getAgentId()
                + "&redirect_uri=" + encodedUrl
                + "&state=" + encodedState;
        return url;
    }

    @Override
    public SsoUser getUserInfoByCode(String authCode) {
        String userId = wechatUtils.getUserId(authCode);
        return wechatUtils.getUserDetail(userId);
    }

    @Override
    public SsoUser getUserByUserId(String userId) {
        SsoUser user = wechatUtils.getUserDetail(userId);
        return user;
    }
}
