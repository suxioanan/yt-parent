package com.yt.sso.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SsoUser 模型单元测试
 */
class SsoUserTest {

    @Test
    void testBuilderAllFields() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("department", new int[]{1, 2});
        extra.put("position", "工程师");

        SsoUser user = SsoUser.builder()
                .userId("zhangsan")
                .name("张三")
                .mobile("13800138000")
                .email("zhangsan@example.com")
                .avatar("https://example.com/avatar.jpg")
                .unionId("union-xxx")
                .jobNumber("001")
                .source("dingtalk")
                .extra(extra)
                .build();

        assertEquals("zhangsan", user.getUserId());
        assertEquals("张三", user.getName());
        assertEquals("13800138000", user.getMobile());
        assertEquals("zhangsan@example.com", user.getEmail());
        assertEquals("https://example.com/avatar.jpg", user.getAvatar());
        assertEquals("union-xxx", user.getUnionId());
        assertEquals("001", user.getJobNumber());
        assertEquals("dingtalk", user.getSource());
        assertEquals(extra, user.getExtra());
    }

    @Test
    void testBuilderDefaultExtra() {
        SsoUser user = SsoUser.builder()
                .userId("lisi")
                .build();

        assertNotNull(user.getExtra());
        assertTrue(user.getExtra().isEmpty());
    }

    @Test
    void testNoArgsConstructor() {
        SsoUser user = new SsoUser();
        user.setUserId("wangwu");
        user.setSource("wechat");

        assertEquals("wangwu", user.getUserId());
        assertEquals("wechat", user.getSource());
    }

    @Test
    void testUnionIdNullByDefault() {
        SsoUser user = SsoUser.builder()
                .userId("zhaoliu")
                .source("wechat")
                .build();

        assertNull(user.getUnionId(), "微信用户 unionId 应为 null");
        assertNull(user.getJobNumber(), "微信用户 jobNumber 应为 null");
    }

    @Test
    void testSourcePlatform() {
        SsoUser dingtalk = SsoUser.builder().source("dingtalk").build();
        SsoUser wechat = SsoUser.builder().source("wechat").build();

        assertEquals("dingtalk", dingtalk.getSource());
        assertEquals("wechat", wechat.getSource());
    }
}
