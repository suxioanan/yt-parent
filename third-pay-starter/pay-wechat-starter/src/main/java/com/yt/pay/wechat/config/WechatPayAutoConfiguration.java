package com.yt.pay.wechat.config;

import com.yt.pay.wechat.WechatPayService;
import com.yt.pay.wechat.WechatPayServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 微信支付自动装配
 *
 * @author sunan
 */
@AutoConfiguration
@EnableConfigurationProperties(WechatPayProperties.class)
public class WechatPayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WechatPayService wechatPayService(WechatPayProperties properties) {
        return new WechatPayServiceImpl(properties);
    }
}
