package com.yt.online.service.impl;


import com.yt.online.build.OnlineKeyBuilder;
import com.yt.online.config.OnlineStatProperties;
import com.yt.online.service.IOnlineStatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @author sunan
 */
@Slf4j
public class OnlineStatServiceImpl implements IOnlineStatService {

    private final RedisTemplate<String, Object> redis;

    private final OnlineStatProperties properties;

    public OnlineStatServiceImpl(RedisTemplate<String, Object> redis, OnlineStatProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * 记录用户在线时长。
     * 通过 SETNX 原子抢占时间窗口，避免并发重复累加。
     */
    public void recordOnlineDuration(String userId, String tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        DateTimeFormatter datePattern = OnlineKeyBuilder.getDatePattern();
        String dayStr = today.format(datePattern);
        long now = System.currentTimeMillis();
        String lastIncrementKey = OnlineKeyBuilder.lastIncrement(userId, tenantId);
        String durationKey = OnlineKeyBuilder.onlineDuration(userId, tenantId, dayStr);
        String lastActiveKey = OnlineKeyBuilder.lastActive(userId, tenantId);

        try {
            long windowTtlMs = properties.getIntervalMs() * 2;
            long lastActiveTtlSeconds = (long) properties.getOnlineIntervalMinutes() * 2 * 60;

            redis.executePipelined((RedisCallback<Object>) connection -> {
                // SETNX 原子抢占窗口
                byte[] incrRawKey = ((RedisSerializer<String>) redis.getKeySerializer()).serialize(lastIncrementKey);
                byte[] incrRawValue = ((RedisSerializer<Object>) redis.getValueSerializer()).serialize(now);
                Boolean acquired = connection.setNX(incrRawKey, incrRawValue);
                if (Boolean.TRUE.equals(acquired)) {
                    connection.pExpire(incrRawKey, windowTtlMs);
                    // 累加在线时长
                    byte[] durRawKey = ((RedisSerializer<String>) redis.getKeySerializer()).serialize(durationKey);
                    connection.incrBy(durRawKey, properties.getOnlineIntervalMinutes());
                    connection.expire(durRawKey, properties.getExpireSeconds());
                }
                // 更新最后活跃时间
                byte[] lastRawKey = ((RedisSerializer<String>) redis.getKeySerializer()).serialize(lastActiveKey);
                byte[] lastRawValue = ((RedisSerializer<Object>) redis.getValueSerializer()).serialize(now);
                connection.setEx(lastRawKey, lastActiveTtlSeconds, lastRawValue);
                return null;
            });
        } catch (Exception e) {
            log.warn("recordOnlineDuration failed for userId={}, tenantId={}", userId, tenantId, e);
        }
    }

}
