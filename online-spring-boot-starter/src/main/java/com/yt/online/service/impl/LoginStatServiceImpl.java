package com.yt.online.service.impl;


import com.yt.online.build.OnlineKeyBuilder;
import com.yt.online.config.OnlineStatProperties;
import com.yt.online.service.ILoginStatService;
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
public class LoginStatServiceImpl implements ILoginStatService {
    private final RedisTemplate<String, Object> redis;

    private final OnlineStatProperties properties;

    public LoginStatServiceImpl(RedisTemplate<String, Object> redis, OnlineStatProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }


    /** 登录成功时调用 */
    public void recordLogin(String userId, String tenantId) {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now();
        DateTimeFormatter datePattern= OnlineKeyBuilder.getDatePattern();
        String dayStr = today.format(datePattern);
        String countKey = OnlineKeyBuilder.loginCount(dayStr);
        String userLoginKey = OnlineKeyBuilder.loginUser(userId, tenantId, dayStr);
        try {
            // 一次 pipeline 批量执行两次 incr
            redis.executePipelined((RedisCallback<Object>) connection -> {
                byte[] countRawKey = ((RedisSerializer<String>) redis.getKeySerializer()).serialize(countKey);
                connection.incr(countRawKey);
                connection.expire(countRawKey, properties.getExpireSeconds());

                byte[] userRawKey = ((RedisSerializer<String>) redis.getKeySerializer()).serialize(userLoginKey);
                connection.incr(userRawKey);
                connection.expire(userRawKey, properties.getExpireSeconds());
                return null;
            });
        } catch (Exception e) {
            log.warn("recordLogin failed for userId={}, tenantId={}", userId, tenantId, e);
        }
    }
}
