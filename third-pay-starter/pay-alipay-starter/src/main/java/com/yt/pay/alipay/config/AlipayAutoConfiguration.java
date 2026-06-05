package com.yt.pay.alipay.config;

import com.yt.pay.alipay.AlipayService;
import com.yt.pay.alipay.AlipayServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 支付宝自动装配
 *
 * @author sunan
 */
@AutoConfiguration
@EnableConfigurationProperties(AlipayProperties.class)
public class AlipayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AlipayService alipayService(AlipayProperties properties) {
        return new AlipayServiceImpl(properties);
    }
}
