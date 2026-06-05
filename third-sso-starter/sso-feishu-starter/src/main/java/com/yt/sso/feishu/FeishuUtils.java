package com.yt.sso.feishu;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yt.sso.feishu.config.FeishuProperties;
import com.yt.sso.model.SsoUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 飞书 accessToken 管理与 API 调用工具
 *
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class FeishuUtils {

    /** token 有效期缓冲（提前 5 分钟过期） */
    private static final long EXPIRE_MILLIS = 110 * 60 * 1000L;

    private static volatile String appAccessToken;
    private static volatile long appAccessTokenExpireAt = 0L;

    private static volatile String tenantAccessToken;
    private static volatile long tenantAccessTokenExpireAt = 0L;

    private static final Object APP_LOCK = new Object();
    private static final Object TENANT_LOCK = new Object();

    private final FeishuProperties feishuProperties;

    // ==================== Token 获取 ====================

    /**
     * 获取 app_access_token（带缓存，有效期 2 小时）
     */
    public String getAppAccessToken() {
        long now = System.currentTimeMillis();
        if (appAccessToken != null && now < appAccessTokenExpireAt) {
            return appAccessToken;
        }
        synchronized (TENANT_LOCK) {
            now = System.currentTimeMillis();
            if (appAccessToken != null && now < appAccessTokenExpireAt) {
                return appAccessToken;
            }
            JSONObject tokenResult = httpAppAccessToken();
            appAccessToken = tokenResult.getStr("app_access_token");
            tenantAccessToken=tokenResult.getStr("tenant_access_token");
            Long expiresIn =tokenResult.getLong("expire");
            if (expiresIn == null) {
                expiresIn = 5155L;
            }
            //默认有个300毫秒的缓冲
            appAccessTokenExpireAt = System.currentTimeMillis() + (expiresIn - 300) * 1000;
            tenantAccessTokenExpireAt = System.currentTimeMillis() + (expiresIn - 300) * 1000;
            return appAccessToken;
        }
    }

    /**
     * 获取 tenant_access_token（带缓存，有效期 2 小时）
     */
    public String getTenantAccessToken() {
        long now = System.currentTimeMillis();
        if (tenantAccessToken != null && now < tenantAccessTokenExpireAt) {
            return tenantAccessToken;
        }
        synchronized (TENANT_LOCK) {
            now = System.currentTimeMillis();
            if (tenantAccessToken != null && now < tenantAccessTokenExpireAt) {
                return tenantAccessToken;
            }
            JSONObject tokenResult = httpTenantAccessToken();
            tenantAccessToken = tokenResult.getStr("tenant_access_token");
            Long expiresIn =tokenResult.getLong("expire");
            if (expiresIn == null) {
                expiresIn = 5155L;
            }
            //默认有个300毫秒的缓冲
            tenantAccessTokenExpireAt = System.currentTimeMillis() + (expiresIn - 300) * 1000;
            return tenantAccessToken;
        }
    }
    // ==================== 用户操作 ====================



    /**
     * 根据 user_id 获取用户详情
     * 只返回了 {"mobile_visible":true,"open_id":"ou_5d4f9a2ddc63ebb7ff7b4ad611fd80b5","union_id":"on_8db75c2fc8d1df097c5819ab4183439e","user_id":"3d8cdb4a"}
     */
    public SsoUser getUserByUserId(String userId) {
        String tenantToken = getTenantAccessToken();
        String url = feishuProperties.getBaseUri()
                + feishuProperties.getUserDetailUri()
                + "/" + userId
                + "?user_id_type=user_id";
        log.info("请求飞书 getUserByUserId,uri={}, userId={}", url,userId);
        try ( HttpResponse response = HttpUtil.createGet(url)
                .header("Authorization", "Bearer " + tenantToken)
                .execute()){
            JSONObject result = JSONUtil.parseObj(response.body());
            log.info("飞书 getUserByUserId 返回：{}", result);
            if (result.getInt("code") != 0) {
                throw new RuntimeException("获取飞书用户详情失败：" + result);
            }
            JSONObject user = result.getJSONObject("data").getJSONObject("user");
            return mapToSsoUser(user);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 用 code 换取 user_access_token
     */
    public String getUserAccessToken(String code) {
        String appToken = getAppAccessToken();
        JSONObject body = new JSONObject();
        body.put("grant_type", "authorization_code");
        body.put("code", code);

        String url = feishuProperties.getBaseUri() + feishuProperties.getUserAccessTokenUri();
        log.info("请求飞书 getUserAccessToken,url={} body ={}",url, JSONUtil.toJsonStr( body));
        try (HttpResponse response = HttpUtil.createPost(url)
                .header("Authorization", "Bearer " + appToken)
                .body(JSONUtil.toJsonStr(body))
                .execute()){
            JSONObject result = JSONUtil.parseObj(response.body());
            log.info("飞书 getUserAccessToken 返回：{}", result);
            if (result.getInt("code") != 0) {
                throw new RuntimeException("获取飞书 user_access_token 失败：" + result);
            }
            return result.getJSONObject("data").getStr("access_token");
        }
    }

    /**
     * 用 user_access_token 获取登录用户信息 返回比较全面的信息了
     */
    public SsoUser getUserInfo(String userAccessToken) {
        String url = feishuProperties.getBaseUri() + feishuProperties.getUserInfoUri();
        log.info("请求飞书 getUserInfo :{}", url);
        try (HttpResponse response = HttpUtil.createGet(url)
                .header("Authorization", "Bearer " + userAccessToken)
                .execute()){
            JSONObject result = JSONUtil.parseObj(response.body());
            log.info("飞书 getUserInfo 返回：{}", result);
            if (result.getInt("code") != 0) {
                throw new RuntimeException("获取飞书用户信息失败：" + result);
            }
            JSONObject data = result.getJSONObject("data");
            return mapToSsoUser(data);
        }
    }

    /**
     * 获取 app_access_token
     */
    private JSONObject httpAppAccessToken() {
        JSONObject body = new JSONObject();
        body.put("app_id", feishuProperties.getClientId());
        body.put("app_secret", feishuProperties.getClientSecret());
        String url = feishuProperties.getBaseUri() + feishuProperties.getAppAccessTokenUri();
        log.info("请求飞书 app_access_token url:{},body:{}",url,JSONUtil.toJsonStr( body));
        try (HttpResponse response = HttpUtil.createPost(url)
                .body(JSONUtil.toJsonStr(body))
                .execute()){
            JSONObject result = JSONUtil.parseObj(response.body());
            log.info("飞书 app_access_token 返回：{}", result);
            if (result.getInt("code") != 0) {
                throw new RuntimeException("获取飞书 app_access_token 失败：" + result);
            }
            return result;
        }
    }

    /**
     * 获取 tenant_access_token
     */
    private JSONObject httpTenantAccessToken() {
        JSONObject body = new JSONObject();
        body.put("app_id", feishuProperties.getClientId());
        body.put("app_secret", feishuProperties.getClientSecret());
        String url = feishuProperties.getBaseUri() + feishuProperties.getTenantAccessTokenUri();
        log.info("请求飞书 tenant_access_token 路径:{},参数:{}",url,body);
        try (HttpResponse response = HttpUtil.createPost(url)
                .body(JSONUtil.toJsonStr(body))
                .execute()){
            JSONObject result = JSONUtil.parseObj(response.body());
            log.info("飞书 tenant_access_token 返回：{}", result);
            if (result.getInt("code") != 0) {
                throw new RuntimeException("获取飞书 tenant_access_token 失败：" + result);
            }
            return result ;
        }
    }

    /**
     * 飞书 JSON → SsoUser 映射
     */
    private SsoUser mapToSsoUser(JSONObject data) {
        String mobile = data.getStr("mobile");
        if (mobile != null) {
            mobile = mobile.replace("+86 ", "").replace("+86", "");
        }
        return SsoUser.builder()
                .userId(data.getStr("user_id"))
                .name(data.containsKey("name") ? data.getStr("name") : data.getStr("nickname"))
                .mobile(mobile)
                .email(data.getStr("email"))
                .avatar(data.getStr("avatar_url") != null
                        ? data.getStr("avatar_url")
                        : data.getStr("avatar_big"))
                .unionId(data.getStr("union_id"))
                .openId(data.getStr("open_id"))
                .jobNumber(data.getStr("employee_no"))
                .gender(mapGender(data.getInt("gender")))
                .source("feishu")
                .extra(data.toBean(Map.class))
                .build();
    }

    /**
     * 飞书性别映射：0-保密 1-男 2-女
     */
    private String mapGender(Integer gender) {
        if (gender == null) return null;
        switch (gender) {
            case 1: return "男";
            case 2: return "女";
            default: return "保密";
        }
    }
}
