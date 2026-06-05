package com.yt.pay.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 扫码下单请求
 */
@Data
public class PayRequest {
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
    /** 回调地址（覆盖配置默认值） */
    private String notifyUrl;
    /** 过期时间（分钟），默认 5 */
    private Integer expireMinutes = 5;
}
