package com.yt.pay.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 条码支付请求（商户扫用户付款码）
 */
@Data
public class MicroPayRequest {
    /** 商户订单号 */
    @NotBlank(message = "商户订单号不能为空")
    private String outTradeNo;
    /** 金额（元） */
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于等于0.01元")
    private BigDecimal totalAmount;
    /** 商品描述 */
    @NotBlank(message = "商品描述不能为空")
    private String description;
    /** 用户付款码（扫码枪扫到的条码） */
    @NotBlank(message = "付款码不能为空")
    private String authCode;
}
