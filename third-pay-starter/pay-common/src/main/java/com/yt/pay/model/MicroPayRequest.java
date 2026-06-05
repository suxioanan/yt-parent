package com.yt.pay.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 条码支付请求（商户扫用户付款码）
 */
@Data
public class MicroPayRequest {
    /** 商户订单号 */
    private String outTradeNo;
    /** 金额（元） */
    private BigDecimal totalAmount;
    /** 商品描述 */
    private String description;
    /** 用户付款码（扫码枪扫到的条码） */
    private String authCode;
}
