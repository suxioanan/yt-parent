package com.yt.pay.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 扫码下单请求
 */
@Data
public class PayRequest {
    /** 商户订单号 */
    private String outTradeNo;
    /** 金额（元） */
    private BigDecimal totalAmount;
    /** 商品描述 */
    private String description;
    /** 回调地址（覆盖配置默认值） */
    private String notifyUrl;
    /** 过期时间（分钟），默认 5 */
    private Integer expireMinutes = 5;
}
