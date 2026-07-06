package com.yt.online.config;


import com.yt.online.handle.OnlineStatInterceptor;
import com.yt.online.handle.OnlineUserResolver;
import com.yt.online.service.IOnlineStatService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author sunan
 */
@Configuration
@ConditionalOnProperty(prefix = "third.online", name = "enabled", havingValue = "true")
public class OnlineStatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OnlineUserResolver.class)
    public OnlineUserResolver onlineUserResolver() {
        return new OnlineUserResolver() {};
    }



    @Bean
    @ConditionalOnBean(OnlineUserResolver.class)
    public OnlineStatInterceptor onlineStatInterceptor(IOnlineStatService onlineStatService,
                                                       OnlineUserResolver onlineUserResolver) {
        return new OnlineStatInterceptor(onlineStatService, onlineUserResolver);
    }

    @Bean
    @ConditionalOnBean(OnlineStatInterceptor.class)
    public WebMvcConfigurer onlineWebMvcConfigurer(OnlineStatInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**");
            }
        };
    }
}
