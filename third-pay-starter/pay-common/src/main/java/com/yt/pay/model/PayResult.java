package com.yt.pay.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 支付结果
 */
@Data
@Builder
public class PayResult {
    /** 商户订单号 */
    private String outTradeNo;
    /** 平台交易号 */
    private String transactionId;
    /** 支付状态 */
    private PayStatus status;
    /** 交易金额 */
    private BigDecimal totalAmount;
    /** 付款人 openid */
    private String payerOpenid;
    /** 原始响应 JSON */
    private String rawResponse;
    /** 是否成功 */
    private boolean success;
}
