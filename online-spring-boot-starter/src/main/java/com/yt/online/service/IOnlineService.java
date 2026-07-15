package com.yt.online.service;

import com.yt.online.entity.SysUserOnline;

import java.util.List;
import java.util.Set;

/**
 * 在线数据同步服务接口。
 *
 * @author sunan
 */
public interface IOnlineService {

    List<String> getDayOnlineUsers(String tenantId);

    List<String> getDayOnlineUsers(String tenantId, String dayStr);

    List<String> countOnlineUsers(String tenantId);

    List<String> countOnlineUsers(String tenantId, String dayStr);

    /**
     * 同步指定租户前一天的在线统计数据。
     */
    List<SysUserOnline> execute(String tenantId);

    List<SysUserOnline> execute(String tenantId, String dayStr);
}
