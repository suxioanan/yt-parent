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

    /** token 有效期（30 分钟） */
    private static final long EXPIRE_MILLIS = 30 * 60 * 1000L;

    /** 内存缓存 */
    private static volatile String accessToken;

    /** token 过期时间点 */
    private static volatile long expireAt = 0L;

    /** 锁对象 */
    private static final Object LOCK = new Object();

    private final DingTalkProperties dingTalkProperties;

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

    public SsoUser getUserByUserId(String userId) {
        String accessToken = getAccessToken();
        Map<String, Object> body = new HashMap<>();
        body.put("userid", userId);
        String url = dingTalkProperties.getBaseApi() + dingTalkProperties.getUserApi()
                + "?access_token=" + accessToken;
        log.info("请求钉钉 getUserByUserId，参数：{}", JSONUtil.toJsonStr(body));
        HttpResponse response = HttpUtil.createPost(url)
                .body(JSONUtil.toJsonStr(body), ContentType.JSON.getValue())
                .execute();
        JSONObject returnBody = JSONUtil.parseObj(response.body());
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

    public String getUserIdByUnionId(String unionId) {
        String accessToken = getAccessToken();
        Map<String, Object> body = new HashMap<>();
        body.put("unionid", unionId);
        String url = dingTalkProperties.getBaseApi() + dingTalkProperties.getUserIdApi()
                + "?access_token=" + accessToken;
        log.info("请求钉钉 getUserIdByUnionId，参数：{}", JSONUtil.toJsonStr(body));
        HttpResponse response = HttpUtil.createPost(url)
                .body(JSONUtil.toJsonStr(body), ContentType.JSON.getValue())
                .execute();
        JSONObject returnBody = JSONUtil.parseObj(response.body());
        log.info("请求钉钉 getUserIdByUnionId，返回体：{}", JSONUtil.toJsonStr(returnBody));
        if (!"0".equals(returnBody.getStr("errcode"))) {
            throw new RuntimeException("获取钉钉用户ID失败：" + returnBody);
        }
        return returnBody.getJSONObject("result").getStr("userid");
    }

    private String httpAccessToken() {
        HttpResponse response = HttpUtil.createGet(dingTalkProperties.getBaseApi() + dingTalkProperties.getTokenApi())
                .form("appkey", dingTalkProperties.getClientId())
                .form("appsecret", dingTalkProperties.getClientSecret())
                .execute();

        JSONObject result = JSONUtil.parseObj(response.body());
        log.info("httpAccessToken获取：{}" , result);
        if (!"0".equals(result.getStr("errcode"))) {
            throw new RuntimeException("获取钉钉 access_token 失败：" + result);
        }
        return result.getStr("access_token");
    }
}
