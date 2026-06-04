package com.yt.sso.model;

import lombok.Data;

/**
 * @author sunan
 */
@Data
public class BaseProperties {

    /** 应用的 AppKey */
    private String clientId;

    /** 应用的 AppSecret */
    private String clientSecret;

    /**
     * 回掉地址
     **/
    private String redirectUri;


}
