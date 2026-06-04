package com.yt.sso.wechat.config;

import com.yt.sso.wechat.WechatSSOService;
import com.yt.sso.wechat.WechatSSOServiceImpl;
import com.yt.sso.wechat.WechatUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 企业微信 SSO 自动装配
 *
 * @author sunan
 */
@AutoConfiguration
@EnableConfigurationProperties(WechatProperties.class)
public class WechatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WechatUtils wechatUtils(WechatProperties properties) {
        return new WechatUtils(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WechatSSOService wechatSSOService(WechatUtils wechatUtils,WechatProperties properties) {
        return new WechatSSOServiceImpl(wechatUtils,properties);
    }
}
