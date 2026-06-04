package com.yt.sso.wechat.open.config;

import com.yt.sso.model.BaseProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信开放平台配置属性
 *
 * @author sunan
 */
@Data
@ConfigurationProperties("sso.wechat-open")
public class WechatOpenProperties extends BaseProperties {

    /** 微信开放平台扫码登录地址 */
    private String loginApi = "https://open.weixin.qq.com/connect/qrconnect";

    /** API 基础地址 */
    private String baseUri = "https://api.weixin.qq.com";

    /** code 换 access_token 接口 */
    private String accessTokenUri = "/sns/oauth2/access_token";

    /** 获取用户信息接口 */
    private String userInfoUri = "/sns/userinfo";

}
