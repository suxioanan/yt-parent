package com.yt.sso.feishu.config;

import com.yt.sso.feishu.FeishuSSOService;
import com.yt.sso.feishu.FeishuSSOServiceImpl;
import com.yt.sso.feishu.FeishuUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 飞书 SSO 自动装配
 *
 * @author sunan
 */
@AutoConfiguration
@EnableConfigurationProperties(FeishuProperties.class)
public class FeishuAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeishuUtils feishuUtils(FeishuProperties properties) {
        return new FeishuUtils(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuSSOService feishuSSOService(FeishuProperties properties, FeishuUtils utils) {
        return new FeishuSSOServiceImpl(properties, utils);
    }
}
