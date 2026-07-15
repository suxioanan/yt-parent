package com.yt.online.constants;

/**
 * @author sunan
 */
public final class OnlineConstant {

    private OnlineConstant() {}

    public static final String SPLIT_STR = ":";

    public static final String REDIS_ONLINE_KEY = "online:duration:";

    public static final String REDIS_COUNT_KEY = "online:login:user:";

    public static final String REDIS_SUM_COUNT_KEY = "online:login:count:";

    public static final String REDIS_LAST_ACTIVE_KEY = "online:last:";

    public static final String REDIS_ACTIVE_KEY = "online:active:";

    public static final String REDIS_LAST_INCREMENT_KEY = "online:lastIncrement:";
}
