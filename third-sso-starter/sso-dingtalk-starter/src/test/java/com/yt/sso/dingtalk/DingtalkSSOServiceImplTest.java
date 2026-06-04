package com.yt.sso.dingtalk;

import com.yt.sso.dingtalk.config.DingTalkProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DingtalkSSOServiceImpl 单元测试
 *
 * getUserInfoByCode / getUserByUserId / getUserIdByUnionId 均依赖
 * 钉钉 SDK 或 hutool 发起真实 HTTP 请求，需集成测试覆盖。
 * 此处仅验证 Bean 构造和注入无异常。
 */
@ExtendWith(MockitoExtension.class)
class DingtalkSSOServiceImplTest {

    @Mock
    private DingTalkProperties dingTalkProperties;

    @Mock
    private DingTalkUtils dingTalkUtils;

    @Test
    void testConstructorInjection() {
        DingtalkSSOServiceImpl impl = new DingtalkSSOServiceImpl(dingTalkProperties, dingTalkUtils);
        assertNotNull(impl);
    }
}
