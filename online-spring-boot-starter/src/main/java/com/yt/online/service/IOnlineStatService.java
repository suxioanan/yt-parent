package com.yt.online.service;

/**
 * 在线时长统计服务接口。
 *
 * @author sunan
 */
public interface IOnlineStatService {

    /**
     * 记录用户在线时长。
     */
    void recordOnlineDuration(String userId, String tenantId);

}
