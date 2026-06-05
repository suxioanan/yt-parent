package com.yt.pay.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 退款结果
 */
@Data
@Builder
public class RefundResult {
    /** 退款单号 */
    private String outRefundNo;
    /** 平台退款单号 */
    private String refundId;
    /** 退款状态 */
    private RefundStatus status;
    /** 退款金额 */
    private BigDecimal refundAmount;
    /** 是否成功 */
    private boolean success;
}
