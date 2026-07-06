package com.yt.online.config;

import com.yt.online.service.ILoginStatService;
import com.yt.online.service.IOnlineService;
import com.yt.online.service.IOnlineStatService;
import com.yt.online.service.impl.LoginStatServiceImpl;
import com.yt.online.service.impl.OnlineServiceImpl;
import com.yt.online.service.impl.OnlineStatServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 无条件注册三个核心 service Bean，确保 jar 包被引用后始终可用。
 *
 * @author sunan
 */
@Configuration
@EnableConfigurationProperties(OnlineStatProperties.class)
public class OnlineServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ILoginStatService loginStatService(RedisTemplate<String, Object> redisTemplate,
                                              OnlineStatProperties properties) {
        return new LoginStatServiceImpl(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public IOnlineStatService onlineStatService(RedisTemplate<String, Object> redisTemplate,
                                                OnlineStatProperties properties) {
        return new OnlineStatServiceImpl(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public IOnlineService onlineService(RedisTemplate<String, Object> redisTemplate) {
        return new OnlineServiceImpl(redisTemplate);
    }

}
