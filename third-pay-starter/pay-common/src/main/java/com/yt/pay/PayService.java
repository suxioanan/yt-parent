package com.yt.pay;

import com.yt.pay.model.*;
import java.util.Map;

/**
 * 统一支付服务接口
 *
 * @author sunan
 */
public interface PayService {

    /** 手机扫码支付 — 生成二维码链接（用户扫商户） */
    String generateQrCode(PayRequest request);

    /** 扫描器扫码支付 — 商户扫用户付款码 */
    PayResult microPay(MicroPayRequest request);

    /** 查询订单状态 */
    PayResult queryOrder(String outTradeNo);

    /** 关单/取消订单 */
    void closeOrder(String outTradeNo);

    /** 退款（支持全额/部分退款） */
    RefundResult refund(RefundRequest request);

    /** 退款查询 */
    RefundResult refundQuery(String outRefundNo);

    /** 处理异步回调 — 验签 + 解密 + 转为统一结果 */
    PayNotifyResult handleNotify(String body, Map<String, String> headers);
}
