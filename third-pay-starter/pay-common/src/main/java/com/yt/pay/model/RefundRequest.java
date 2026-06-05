package com.yt.pay.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 退款请求（支持全额退款和部分退款）
 *
 * 部分退款：refundAmount 小于 totalAmount，剩余金额可多次退款
 * 每次退款 outRefundNo 必须不同，累计退款金额不能超过 totalAmount
 */
@Data
@Builder
public class RefundRequest {
    /** 原商户订单号 */
    private String outTradeNo;
    /** 退款单号（每次退款唯一，用于幂等） */
    private String outRefundNo;
    /** 原订单金额 */
    private BigDecimal totalAmount;
    /** 本次退款金额（部分退款时小于 totalAmount） */
    private BigDecimal refundAmount;
    /** 退款原因 */
    private String reason;
}
