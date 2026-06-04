package com.yt.sso.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业微信配置属性
 *
 * @author sunan
 */
@Data
@ConfigurationProperties("sso.wechat")
public class WechatProperties {

    /** 企业微信登录地址 */
    private String loginApi = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";

    /** API 基础地址 */
    private String baseUri = "https://qyapi.weixin.qq.com";

    /** 获取 token 接口 */
    private String tokenUri = "/cgi-bin/gettoken";

    /** 获取用户ID接口 */
    private String userIdUri = "/cgi-bin/auth/getuserinfo";

    /** 获取用户详情接口 */
    private String userInfoUri = "/cgi-bin/user/get";

    /** 回调地址 */
    private String redirectUri = "https://auth.com";

    /** 应用 AgentId */
    private String agentId;

    /** 企业 CorpId */
    private String clientId;

    /** 应用 Secret */
    private String secret;
}
