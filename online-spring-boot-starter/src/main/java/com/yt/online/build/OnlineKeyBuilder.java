package com.yt.online.build;

import com.yt.online.constants.OnlineConstant;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * @author sunan
 */
public class OnlineKeyBuilder {

    private static final String FORMAT_PATTERN = "yyyyMMdd";

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(FORMAT_PATTERN, Locale.getDefault());

    public static DateTimeFormatter getDatePattern() {
        return DATE_FORMATTER;
    }

    public static String loginCount(String date) {
        return OnlineConstant.REDIS_SUM_COUNT_KEY + date;
    }

    public static String loginUser(String userId, String tenantId, String date) {
        return OnlineConstant.REDIS_COUNT_KEY + date + OnlineConstant.SPLIT_STR + tenantId + OnlineConstant.SPLIT_STR + userId;
    }

    public static String lastActive(String userId, String tenantId) {
        return OnlineConstant.REDIS_LAST_ACTIVE_KEY + tenantId + OnlineConstant.SPLIT_STR + userId;
    }

    public static String onlineDuration(String userId, String tenantId, String date) {
        return OnlineConstant.REDIS_ONLINE_KEY + date + OnlineConstant.SPLIT_STR + tenantId + OnlineConstant.SPLIT_STR + userId;
    }

    public static String lastIncrement(String userId, String tenantId) {
        return OnlineConstant.REDIS_LAST_INCREMENT_KEY + tenantId + OnlineConstant.SPLIT_STR + userId;
    }

    /** 用于 SCAN 扫描某租户某日所有用户在线时长 */
    public static String onlineDurationScanPattern(String date, String tenantId) {
        return OnlineConstant.REDIS_ONLINE_KEY + date + OnlineConstant.SPLIT_STR + tenantId + OnlineConstant.SPLIT_STR + "*";
    }

}
