package com.yt.online.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author sunan
 */
@Slf4j
@ConfigurationProperties(prefix = "third.online")
@Data
public class OnlineStatProperties {

    /** 是否启用 */
    private boolean enabled = false;

    /** 在线统计间隔（分钟） */
    private int onlineIntervalMinutes = 5;

    /** Redis key 过期天数 */
    private int keyExpireDays = 2;

    /** 在线统计间隔（毫秒），由 onlineIntervalMinutes 计算得出 */
    private long intervalMs;

    /** Redis key 过期秒数，由 keyExpireDays 计算得出 */
    private long expireSeconds;

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("在线统计已启用, onlineIntervalMinutes={}, keyExpireDays={}", onlineIntervalMinutes, keyExpireDays);
        } else if (onlineIntervalMinutes != 5 || keyExpireDays != 2) {
            log.warn("检测到在线统计相关配置但未启用，请确认配置前缀为 third.online.enabled=true");
        }
    }

    public long getIntervalMs() {
        if (intervalMs == 0) {
            intervalMs = (long) onlineIntervalMinutes * 60_000L;
        }
        return intervalMs;
    }

    public long getExpireSeconds() {
        if (expireSeconds == 0) {
            expireSeconds = (long) keyExpireDays * 24 * 60 * 60L;
        }
        return expireSeconds;
    }

}
