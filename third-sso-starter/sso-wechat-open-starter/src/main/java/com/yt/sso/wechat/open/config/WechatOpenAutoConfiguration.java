package com.yt.sso.wechat.open.config;

import com.yt.sso.wechat.open.WechatOpenSSOService;
import com.yt.sso.wechat.open.WechatOpenSSOServiceImpl;
import com.yt.sso.wechat.open.WechatOpenUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 微信开放平台 SSO 自动装配
 *
 * @author sunan
 */
@AutoConfiguration
@EnableConfigurationProperties(WechatOpenProperties.class)
public class WechatOpenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WechatOpenUtils wechatOpenUtils(WechatOpenProperties properties) {
        return new WechatOpenUtils(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public WechatOpenSSOService wechatOpenSSOService(WechatOpenProperties properties, WechatOpenUtils utils) {
        return new WechatOpenSSOServiceImpl(properties, utils);
    }
}
