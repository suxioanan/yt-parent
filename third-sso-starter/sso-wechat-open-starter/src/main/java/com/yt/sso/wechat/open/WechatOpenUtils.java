package com.yt.sso.wechat.open;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yt.sso.model.SsoUser;
import com.yt.sso.wechat.open.config.WechatOpenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 微信开放平台 API 调用工具
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class WechatOpenUtils {

    private final WechatOpenProperties wechatOpenProperties;

    /**
     * 通过 OAuth 授权码获取用户信息
     * <p>第一步：code → access_token + openid
     * <p>第二步：access_token + openid → 用户信息
     */
    public SsoUser getUserByCode(String code) {
        // 1. code 换 access_token 和 openid
        JSONObject tokenResult = getAccessTokenByCode(code);
        String accessToken = tokenResult.getStr("access_token");
        String openid = tokenResult.getStr("openid");

        // 2. 获取用户信息
        return getUserInfo(accessToken, openid);
    }

    /**
     * 用 code 换取 access_token
     * <p>GET /sns/oauth2/access_token?appid=APPID&secret=SECRET&code=CODE&grant_type=authorization_code
     * <p>返回：{"access_token":"xxx","expires_in":7200,"refresh_token":"xxx","openid":"xxx","scope":"xxx","unionid":"xxx"}
     */
    public JSONObject getAccessTokenByCode(String code) {
        String url = wechatOpenProperties.getBaseUri()
                + wechatOpenProperties.getAccessTokenUri()
                + "?appid=" + wechatOpenProperties.getClientId()
                + "&secret=" + wechatOpenProperties.getClientSecret()
                + "&code=" + code
                + "&grant_type=authorization_code";

        log.info("请求微信开放平台 getAccessTokenByCode, code={}", code);
        HttpResponse response = HttpUtil.createGet(url).execute();
        JSONObject result = JSONUtil.parseObj(response.body());
        log.info("微信开放平台 getAccessTokenByCode 返回：{}", result);

        if (result.containsKey("errcode") && result.getInt("errcode") != 0) {
            throw new RuntimeException("微信开放平台获取 access_token 失败：" + result);
        }
        return result;
    }

    /**
     * 用 access_token + openid 获取用户信息
     * <p>GET /sns/userinfo?access_token=ACCESS_TOKEN&openid=OPENID
     * <p>返回：{"openid":"xxx","nickname":"xxx","sex":1,"province":"xxx","city":"xxx","country":"xxx","headimgurl":"xxx","privilege":[],"unionid":"xxx"}
     */
    public SsoUser getUserInfo(String accessToken, String openid) {
        String url = wechatOpenProperties.getBaseUri()
                + wechatOpenProperties.getUserInfoUri()
                + "?access_token=" + accessToken
                + "&openid=" + openid;

        log.info("请求微信开放平台 getUserInfo, openid={}", openid);
        HttpResponse response = HttpUtil.createGet(url).execute();
        JSONObject result = JSONUtil.parseObj(response.body());
        log.info("微信开放平台 getUserInfo 返回：{}", result);

        if (result.containsKey("errcode") && result.getInt("errcode") != 0) {
            throw new RuntimeException("微信开放平台获取用户信息失败：" + result);
        }

        return mapToSsoUser(result);
    }

    /**
     * 微信开放平台 JSON → SsoUser 映射
     */
    private SsoUser mapToSsoUser(JSONObject data) {
        return SsoUser.builder()
                .userId(data.getStr("openid"))
                .name(data.getStr("nickname"))
                .gender(mapGender(data.getInt("sex")))
                .avatar(data.getStr("headimgurl"))
                .unionId(data.getStr("unionid"))
                .openId(data.getStr("openid"))
                .source("wechat-open")
                .extra(data.toBean(Map.class))
                .build();
    }

    /**
     * 微信性别映射：0-未知 1-男 2-女
     */
    private String mapGender(Integer sex) {
        if (sex == null) return null;
        switch (sex) {
            case 1: return "男";
            case 2: return "女";
            default: return "未知";
        }
    }
}
