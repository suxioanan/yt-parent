package com.yt.pay.alipay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝配置
 *
 * @author sunan
 */
@Data
@ConfigurationProperties("pay.alipay")
public class AlipayProperties {
    /** 应用 ID */
    private String appId;
    /** 应用私钥路径 */
    private String privateKeyPath;
    /** 支付宝公钥路径 */
    private String alipayPublicKeyPath;
    /** 回调地址 */
    private String notifyUrl;
    /** 网关地址 */
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
}
