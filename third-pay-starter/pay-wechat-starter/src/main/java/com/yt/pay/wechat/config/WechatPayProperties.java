package com.yt.pay.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信支付配置
 * @author sunan
 */
@Data
@ConfigurationProperties("pay.wechat")
public class WechatPayProperties {

    /** 公众号/小程序 AppID */
    private String appId;

    /** 商户号 */
    private String merchantId;

    /** 商户 API 证书序列号 */
    private String merchantSerialNumber;

    /** 商户私钥路径 */
    private String privateKeyPath;

    /** API V3 密钥 */
    private String apiV3Key;

    /** 回调地址 */
    private String notifyUrl;

    /** 商户扫用户付款码地址 */
    private String payCodeUrl = "https://api.mch.weixin.qq.com/v3/pay/transactions/codepay";

}
