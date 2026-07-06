package com.yt.online.service;

/**
 * 登录统计服务接口。
 *
 * @author sunan
 */
public interface ILoginStatService {

    /**
     * 记录用户登录。
     */
    void recordLogin(String userId, String tenantId);

}
