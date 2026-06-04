package com.yt.sso.wechat.open;

import com.yt.sso.build.LoginState;
import com.yt.sso.model.SsoUser;
import com.yt.sso.wechat.open.config.WechatOpenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 微信开放平台 SSO 服务实现
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class WechatOpenSSOServiceImpl implements WechatOpenSSOService {

    private final WechatOpenProperties wechatOpenProperties;
    private final WechatOpenUtils wechatOpenUtils;

    @Override
    public String getSSOScanUri() {
        String baseUrl = wechatOpenProperties.getLoginApi();
        String encodedUrl = wechatOpenProperties.getRedirectUri();
        LoginState state = LoginState.system();
        String encodedState = encodeState(state);
        try {
            encodedUrl = URLEncoder.encode(encodedUrl, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            log.error("转码失败:{}", e.getMessage(), e);
        }

        String url = baseUrl
                + "?appid=" + wechatOpenProperties.getClientId()
                + "&redirect_uri=" + encodedUrl
                + "&response_type=code"
                + "&scope=snsapi_login"
                + "&state=" + encodedState
                + "#wechat_redirect";
        return url;
    }

    @Override
    public SsoUser getUserInfoByCode(String authCode) {
        return wechatOpenUtils.getUserByCode(authCode);
    }

    /**
     * 微信开放平台不支持根据 userId 查询任意用户详情。
     * 用户信息只能通过 OAuth 授权码（getUserInfoByCode）获取。
     */
    @Override
    public SsoUser getUserByUserId(String userId) {
        throw new UnsupportedOperationException(
                "微信开放平台不支持根据 userId 查询用户。" +
                "请使用 getUserInfoByCode(authCode) 获取授权用户信息，" +
                "或使用微信开放平台的 openid 自行管理用户标识。");
    }
}
