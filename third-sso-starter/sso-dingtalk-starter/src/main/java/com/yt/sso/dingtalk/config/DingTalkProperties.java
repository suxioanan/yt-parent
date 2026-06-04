package com.yt.sso.dingtalk.config;

import com.yt.sso.model.BaseProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 钉钉配置属性
 * @author sunan
 */
@Data
@ConfigurationProperties("sso.dingtalk")
public class DingTalkProperties extends BaseProperties {

    /**
     * 钉钉登录路径地址
     **/
    private String loginApi = "https://login.dingtalk.com/oauth2/auth";

    /**
     * 钉钉新版 API 基础地址（OAuth2 / 用户信息）
     */
    private String newBaseApi = "https://api.dingtalk.com";

    /**
     * OAuth2 code 换 userAccessToken 接口
     */
    private String userTokenApi = "/v1.0/oauth2/userAccessToken";

    /**
     * 通过 accessToken 获取当前用户信息接口
     */
    private String contactUserApi = "/v1.0/contact/users/me";

    /**
     * 钉钉旧版 API 基础地址
     */
    private String baseApi = "https://oapi.dingtalk.com";

    /**
     * 获取 token 接口
     */
    private String tokenApi = "/gettoken";

    /**
     * 获取部门用户接口
     */
    private String deptUserApi = "/topapi/v2/user/list";

    /**
     * 根据 unionId 获取 userId 接口
     */
    private String userIdApi = "/topapi/user/getbyunionid";

    /**
     * 获取用户信息接口
     */
    private String userApi = "/topapi/v2/user/get";

    /**
     * 应用名称
     */
    private String appName = "钉钉通知";

    /**
     * 所属组织 CorpId
     */
    private String corpId;

    /**
     * 钉钉应用的 agentId
     */
    private Long agentId;
}
