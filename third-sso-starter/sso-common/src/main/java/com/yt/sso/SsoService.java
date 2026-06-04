package com.yt.sso;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yt.sso.build.LoginState;
import com.yt.sso.model.SsoUser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SSO 统一服务接口
 *
 * @author sunan
 */
public interface SsoService {

    /**
     * 生成扫码地址
     *
     */
    String getSSOScanUri();

    /**
     * 通过授权码获取用户信息
     *
     * @param authCode 授权码
     * @return 用户信息
     * @throws RuntimeException 获取失败时抛出
     */
    SsoUser getUserInfoByCode(String authCode);

    /**
     * 根据用户ID获取用户详情
     *
     * @param userId 用户ID
     * @return 统一用户信息
     * @throws RuntimeException 获取失败时抛出
     */
    SsoUser getUserByUserId(String userId);


    default String encodeState(LoginState state) {
        String json = JSONUtil.toJsonStr(state);
        return Base64.getUrlEncoder()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
