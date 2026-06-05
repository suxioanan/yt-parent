package com.yt.pay.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 支付回调通知结果
 */
@Data
@Builder
public class PayNotifyResult {
    /** 商户订单号 */
    private String outTradeNo;
    /** 平台交易号 */
    private String transactionId;
    /** 支付状态 */
    private PayStatus status;
    /** 金额 */
    private BigDecimal totalAmount;
    /** 事件类型：支付/退款 */
    private String eventType;
    /** 原始回调体 */
    private String rawBody;
}
