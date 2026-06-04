package com.yt.sso.dingtalk.config;

import com.yt.sso.dingtalk.DingTalkUtils;
import com.yt.sso.dingtalk.DingtalkSSOService;
import com.yt.sso.dingtalk.DingtalkSSOServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 钉钉 SSO 自动装配
 *
 * @author sunan
 */
@AutoConfiguration
@EnableConfigurationProperties(DingTalkProperties.class)
public class DingTalkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DingTalkUtils dingTalkUtils(DingTalkProperties properties) {
        return new DingTalkUtils(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DingtalkSSOService dingtalkSSOService(DingTalkProperties properties, DingTalkUtils utils) {
        return new DingtalkSSOServiceImpl(properties, utils);
    }
}
