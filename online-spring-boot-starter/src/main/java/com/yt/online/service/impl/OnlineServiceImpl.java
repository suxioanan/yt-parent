package com.yt.online.service.impl;

import com.yt.online.build.OnlineKeyBuilder;
import com.yt.online.entity.SysUserOnline;
import com.yt.online.service.IOnlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author sunan
 */
@Slf4j
@RequiredArgsConstructor
public class OnlineServiceImpl implements IOnlineService {

    private final RedisTemplate<String, Object> redis;

    public List<SysUserOnline> execute(String tenantId) {
        DateTimeFormatter datePattern = OnlineKeyBuilder.getDatePattern();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String dayStr = yesterday.format(datePattern);
        Integer day = Integer.parseInt(dayStr);
        Integer month = Integer.parseInt(dayStr.substring(0, 6));
        Integer year = Integer.parseInt(dayStr.substring(0, 4));

        String pattern = OnlineKeyBuilder.onlineDurationScanPattern(dayStr, tenantId);
        log.info("租户{}:同步用户在线数据-------", tenantId);

        Set<String> keys;
        try {
            keys = scanKeys(pattern);
        } catch (Exception e) {
            log.error("SCAN keys failed for tenant={}, pattern={}", tenantId, pattern, e);
            return new ArrayList<>();
        }

        List<SysUserOnline> list = new ArrayList<>();
        if (keys.isEmpty()) {
            return list;
        }

        for (String key : keys) {
            String userId = extractUserId(key);
            if (userId == null) {
                continue;
            }

            Integer minutes = readInteger(key);
            String loginKey = OnlineKeyBuilder.loginUser(userId, tenantId, dayStr);
            Integer loginCount = readInteger(loginKey);

            SysUserOnline e = new SysUserOnline();
            e.setUserId(userId+"");
            e.setStatDate(dayStr);
            e.setTenantId(tenantId);
            e.setOnlineMinutes(minutes == null ? 0 : minutes);
            e.setLoginCount(loginCount);
            e.setDayTime(day);
            e.setMonthTime(month);
            e.setYearTime(year);
            list.add(e);
        }
        return list;
    }

    /** 使用 SCAN 替代 KEYS，避免阻塞 Redis */
    private Set<String> scanKeys(String pattern) {
        Set<String> keySet = new HashSet<>();
        redis.execute((RedisCallback<Object>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keySet.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        return keySet;
    }

    /** 读取 key 对应的 Integer 值 */
    private Integer readInteger(String key) {
        return redis.execute((RedisCallback<Integer>) connection -> {
            byte[] rawKey = ((RedisSerializer<String>) redis.getKeySerializer()).serialize(key);
            byte[] rawValue = connection.get(rawKey);
            if (rawValue == null) {
                return 0;
            }
            return Integer.valueOf(new String(rawValue, StandardCharsets.UTF_8));
        });
    }

    /** 从 key 中解析 userId，key 格式：xxx:date:tenantId:userId */
    private String extractUserId(String key) {
        try {
            String[] arr = key.split(":");
            return arr[arr.length - 1];
        } catch (Exception e) {
            log.warn("Failed to extract userId from key: {}", key);
            return null;
        }
    }

}
