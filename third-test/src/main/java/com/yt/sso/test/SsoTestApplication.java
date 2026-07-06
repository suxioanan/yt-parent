package com.yt.sso.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SSO 测试应用
 *
 * <p>启动后通过 HTTP 接口测试钉钉/企业微信 SSO 功能。
 * <p>配置 application.yml 中的 sso.dingtalk 和 sso.wechat 后启动。
 *
 * @author sunan
 */
@SpringBootApplication
public class SsoTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsoTestApplication.class, args);
    }
}
