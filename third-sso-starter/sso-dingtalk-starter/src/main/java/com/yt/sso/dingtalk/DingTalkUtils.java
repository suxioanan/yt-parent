package com.yt.sso.dingtalk;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yt.sso.dingtalk.config.DingTalkProperties;
import com.yt.sso.model.SsoUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉 accessToken 管理工具
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class DingTalkUtils {


    /** 内存缓存 */
    private  volatile String accessToken;

    /** token 过期时间点 */
    private  volatile long expireAt = 0L;

    /** 锁对象 */
    private  final Object lock = new Object();

    private final DingTalkProperties dingTalkProperties;

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
            //默认有个300秒的缓冲
            expireAt = System.currentTimeMillis() + (expiresIn - 300) * 1000;
            return accessToken;
        }
    }

    public SsoUser getUserByUserId(String userId) {
        String accessToken = getAccessToken();
        Map<String, Object> body = new HashMap<>();
        body.put("userid", userId);
        String url = dingTalkProperties.getBaseApi() + dingTalkProperties.getUserApi()
                + "?access_token=" + accessToken;
        log.info("请求钉钉 getUserByUserId，参数：{}", JSONUtil.toJsonStr(body));
        try (HttpResponse response = HttpUtil.createPost(url)
                .body(JSONUtil.toJsonStr(body), ContentType.JSON.getValue())
                .execute()){
            JSONObject returnBody = JSONUtil.parseObj(response.body());
            log.debug("请求钉钉 getUserByUserId返回：{}", JSONUtil.toJsonStr(returnBody));
            if (!"0".equals(returnBody.getStr("errcode"))) {
                throw new RuntimeException("获取钉钉用户信息失败：" + returnBody);
            }
            JSONObject result = returnBody.getJSONObject("result");
            log.info("根据用户ID获取钉钉用户信息：{}", JSONUtil.toJsonStr( result));
            return SsoUser.builder()
                    .userId(result.getStr("userid"))
                    .name(result.getStr("name"))
                    .mobile(result.getStr("mobile"))
                    .email(result.getStr("email"))
                    .avatar(result.getStr("avatar"))
                    .unionId(result.getStr("unionid"))
                    .jobNumber(result.getStr("job_number"))
                    .source("dingtalk")
                    .extra(result.toBean(Map.class))
                    .build();
        }
    }

    public String getUserIdByUnionId(String unionId) {
        String accessToken = getAccessToken();
        Map<String, Object> body = new HashMap<>();
        body.put("unionid", unionId);
        String url = dingTalkProperties.getBaseApi() + dingTalkProperties.getUserIdApi()
                + "?access_token=" + accessToken;
        log.info("请求钉钉 getUserIdByUnionId，参数：{}", JSONUtil.toJsonStr(body));
        try (HttpResponse response = HttpUtil.createPost(url)
                .body(JSONUtil.toJsonStr(body), ContentType.JSON.getValue())
                .execute()){
            JSONObject returnBody = JSONUtil.parseObj(response.body());
            log.info("请求钉钉 getUserIdByUnionId，返回体：{}", JSONUtil.toJsonStr(returnBody));
            if (!"0".equals(returnBody.getStr("errcode"))) {
                throw new RuntimeException("获取钉钉用户ID失败：" + returnBody);
            }
            return returnBody.getJSONObject("result").getStr("userid");
        }
    }

    private JSONObject httpAccessToken() {
        String url=dingTalkProperties.getBaseApi() + dingTalkProperties.getTokenApi();
        log.info("请求钉钉 getAccessToken，url：{}", url);
        try (HttpResponse response = HttpUtil.createGet(url)
                .form("appkey", dingTalkProperties.getClientId())
                .form("appsecret", dingTalkProperties.getClientSecret())
                .execute()){
            JSONObject result = JSONUtil.parseObj(response.body());
            log.info("httpAccessToken获取：{}" , result);
            if (!"0".equals(result.getStr("errcode"))) {
                throw new RuntimeException("获取钉钉 access_token 失败：" + result);
            }
            return result;
        }
    }
}
