package com.yt.online.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 时长统计表
 * </p>
 * @author honeycom
 * @since 2017-11-20
 */
@Data
public class SysUserOnline implements Serializable {

    private static final long serialVersionUID = 1L;


    private Long id;

    private String userId;

    private String tenantId;

    private String statDate;

    /**
     * 登录次数
     */
    private Integer loginCount;

    /**
     * 在线/登录时长（分钟）
     */
    private Integer onlineMinutes;

    /**
     * 天 20251222
     */
    private Integer dayTime;

    /**
     * 月 202512
     */
    private Integer monthTime;

    /**
     * 年 2025
     */
    private Integer yearTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
