package com.yt.sso.dingtalk;

import com.yt.sso.SsoService;

/**
 * 钉钉 SSO 服务接口
 *
 * @author sunan
 */
public interface DingtalkSSOService extends SsoService {

    /**
     * 根据 unionId 获取 userId
     *
     * @param unionId 钉钉 unionId
     * @return userId
     * @throws RuntimeException 获取失败时抛出
     */
    String getUserIdByUnionId(String unionId);
}
