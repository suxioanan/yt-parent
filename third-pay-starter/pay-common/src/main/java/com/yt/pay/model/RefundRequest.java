package com.yt.pay.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotBlank(message = "原商户订单号不能为空")
    private String outTradeNo;
    /** 退款单号（每次退款唯一，用于幂等） */
    @NotBlank(message = "退款单号不能为空")
    private String outRefundNo;
    /** 原订单金额 */
    @NotNull(message = "原订单金额不能为空")
    @DecimalMin(value = "0.01", message = "原订单金额必须大于等于0.01元")
    private BigDecimal totalAmount;
    /** 本次退款金额（部分退款时小于 totalAmount） */
    @NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0.01", message = "退款金额必须大于等于0.01元")
    private BigDecimal refundAmount;
    /** 退款原因 */
    private String reason;
}
