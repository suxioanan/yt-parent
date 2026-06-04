package com.yt.sso.feishu.config;

import com.yt.sso.model.BaseProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 飞书配置属性
 *
 * @author sunan
 */
@Data
@ConfigurationProperties("sso.feishu")
public class FeishuProperties extends BaseProperties {

    /** 飞书授权登录地址 */
    private String loginApi = "https://open.feishu.cn/open-apis/authen/v1/authorize";

    /** API 基础地址 */
    private String baseUri = "https://open.feishu.cn/open-apis";

    /** 获取 app_access_token 接口 */
    private String appAccessTokenUri = "/auth/v3/app_access_token/internal";

    /** 获取 tenant_access_token 接口 */
    private String tenantAccessTokenUri = "/auth/v3/tenant_access_token/internal";

    /** OAuth2 code 换 user_access_token 接口 */
    private String userAccessTokenUri = "/authen/v1/oidc/access_token";

    /** 获取登录用户信息接口 */
    private String userInfoUri = "/authen/v1/user_info";

    /** 获取用户详情接口 */
    private String userDetailUri = "/contact/v3/users";


}
