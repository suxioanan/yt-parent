package com.yt.online.service;

import com.yt.online.entity.SysUserOnline;

import java.util.List;

/**
 * 在线数据同步服务接口。
 *
 * @author sunan
 */
public interface IOnlineService {

    /**
     * 同步指定租户前一天的在线统计数据。
     */
    List<SysUserOnline> execute(String tenantId);

}
