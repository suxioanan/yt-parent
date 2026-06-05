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



    /** 内存缓存 */
    private  volatile String accessToken;

    /** token 过期时间点 */
    private  volatile long expireAt = 0L;

    /** 锁对象 */
    private  final Object lock = new Object();

    private final WechatProperties wechatProperties;

    /**
     * 获取 accessToken（带缓存）
     * Double-Checked Locking（双重检查锁）
     * 场景：accessToken 已过期
     * A,B 同时调用getAccessToken
     * 线程A执行 if (accessToken != null && now < expireAt) 结果为 false，进入 synchronized 块 占有锁
     * 线程B执行 if (accessToken != null && now < expireAt) 结果为 false 等待A锁释放
     * 线程A执行完毕，释放锁，此时已经生成新的token，线程B拿到锁，所以需要二次判断
     */
    public String getAccessToken() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < expireAt) {
            return accessToken;
        }
        synchronized (lock) {
            now = System.currentTimeMillis();
            if (accessToken != null && now < expireAt) {
                return accessToken;
            }
            JSONObject tokenResult = httpAccessToken();
            accessToken = tokenResult.getStr("access_token");
            Long expiresIn =tokenResult.getLong("expires_in");
            if (expiresIn == null) {
                expiresIn = 7200L;
            }
            //默认有个300毫秒的缓冲
            expireAt = System.currentTimeMillis() + (expiresIn - 300) * 1000;
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
        log.info("getUserId 请求路径：{}", url);
        try (HttpResponse response = HttpUtil.createGet(url).execute()){
            JSONObject result = JSONUtil.parseObj(response.body());
            log.info("根据微信CODE获取用户ID返回结果为：{}", result);
            if (!"0".equals(result.getStr("errcode"))) {
                throw new RuntimeException("获取企业微信用户ID失败：" + result);
            }
            return result.getStr("userid");
        }
    }

    /**
     * 根据 wechatUserId 获取用户详情
     */
    public SsoUser getUserDetail(String wechatUserId) {
        String token = getAccessToken();
        String url = wechatProperties.getBaseUri() + wechatProperties.getUserInfoUri()
                + "?access_token=" + token + "&userid=" + wechatUserId;
        try (HttpResponse response = HttpUtil.createGet(url).execute()){
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
    }

    private JSONObject httpAccessToken() {
        try (HttpResponse response = HttpUtil.createGet(wechatProperties.getBaseUri() + wechatProperties.getTokenUri())
                .form("corpid", wechatProperties.getClientId())
                .form("corpsecret", wechatProperties.getClientSecret())
                .execute()){
            JSONObject result = JSONUtil.parseObj(response.body());
            log.info("httpAccessToken获取：{}" , result);
            if (!"0".equals(result.getStr("errcode"))) {
                throw new RuntimeException("获取企业微信 access_token 失败：" + result);
            }
            return result;
        }











    }
}
