package com.yt.pay.model;

/**
 * 支付状态
 */
public enum PayStatus {
    /** 支付成功 */
    SUCCESS,
    /** 已退款 */
    REFUND,
    /** 已关闭 */
    CLOSED,
    /** 未支付 */
    NOTPAY,
    /** 用户支付中 */
    USERPAYING
}
