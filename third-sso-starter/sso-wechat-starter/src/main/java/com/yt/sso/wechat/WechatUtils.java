package com.yt.sso.wechat;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yt.sso.model.SsoUser;
import com.yt.sso.wechat.config.WechatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.temporal.ValueRange;
import java.util.Map;

/**
 * 企业微信 accessToken 管理与 API 调用工具
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class WechatUtils {

    /** token 有效期（30 分钟） */
    private static final long EXPIRE_MILLIS = 30 * 60 * 1000L;

    /** 内存缓存 */
    private static volatile String accessToken;

    /** token 过期时间点 */
    private static volatile long expireAt = 0L;

    /** 锁对象 */
    private static final Object LOCK = new Object();

    private final WechatProperties wechatProperties;

    /**
     * 获取 accessToken（带缓存）
     */
    public String getAccessToken() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < expireAt) {
            return accessToken;
        }
        synchronized (LOCK) {
            now = System.currentTimeMillis();
            if (accessToken != null && now < expireAt) {
                return accessToken;
            }
            String tokenResult = httpAccessToken();
            accessToken = tokenResult;
            expireAt = now + EXPIRE_MILLIS;
            return accessToken;
        }
    }

    /**
     * 通过授权码获取 userId
     */
    public String getUserId(String code) {
        String token = getAccessToken();
        String url = wechatProperties.getBaseUri() + wechatProperties.getUserIdUri()
                + "?access_token=" + token + "&code=" + code;
        HttpResponse response = HttpUtil.createGet(url).execute();
        JSONObject result = JSONUtil.parseObj(response.body());
        log.info("根据微信CODE获取用户ID返回结果为：{}", result);
        if (!"0".equals(result.getStr("errcode"))) {
            throw new RuntimeException("获取企业微信用户ID失败：" + result);
        }
        return result.getStr("userid");
    }

    /**
     * 根据 wechatUserId 获取用户详情
     */
    public SsoUser getUserDetail(String wechatUserId) {
        String token = getAccessToken();
        String url = wechatProperties.getBaseUri() + wechatProperties.getUserInfoUri()
                + "?access_token=" + token + "&userid=" + wechatUserId;
        HttpResponse response = HttpUtil.createGet(url).execute();
        JSONObject result = JSONUtil.parseObj(response.body());
        log.info("根据企业用户ID获取企业微信用户返回数据为：{}", result);
        if (!"0".equals(result.getStr("errcode"))) {
            throw new RuntimeException("获取企业微信用户详情失败：" + result);
        }
        return SsoUser.builder()
                .userId(result.getStr("userid"))
                .name(result.getStr("name"))
                .mobile(result.getStr("mobile"))
                .email(result.getStr("email"))
                .avatar(result.getStr("avatar"))
                .unionId(null)
                .jobNumber(null)
                .openId(result.getStr("open_userid"))
                .address(result.getStr("address"))
                .source("wechat")
                .extra(result.toBean(Map.class))
                .build();
    }

    private String httpAccessToken() {
        HttpResponse response = HttpUtil.createGet(wechatProperties.getBaseUri() + wechatProperties.getTokenUri())
                .form("corpid", wechatProperties.getClientId())
                .form("corpsecret", wechatProperties.getSecret())
                .execute();
        JSONObject result = JSONUtil.parseObj(response.body());
        log.info("httpAccessToken获取：{}" , result);
        if (!"0".equals(result.getStr("errcode"))) {
            throw new RuntimeException("获取企业微信 access_token 失败：" + result);
        }
        return result.getStr("access_token");
    }
}
