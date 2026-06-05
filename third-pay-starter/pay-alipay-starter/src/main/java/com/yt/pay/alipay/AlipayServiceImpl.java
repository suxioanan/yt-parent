package com.yt.pay.alipay;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.yt.pay.model.*;
import com.yt.pay.alipay.config.AlipayProperties;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝服务实现
 *
 * @author sunan
 */
@Slf4j
public class AlipayServiceImpl implements AlipayService {

    private final AlipayProperties properties;
    private final AlipayClient alipayClient;

    public AlipayServiceImpl(AlipayProperties properties) {
        this.properties = properties;
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.getGatewayUrl());
        config.setAppId(properties.getAppId());
        config.setPrivateKey(properties.getPrivateKeyPath());
        config.setAlipayPublicKey(properties.getAlipayPublicKeyPath());
        config.setFormat("json");
        config.setCharset("UTF-8");
        config.setSignType("RSA2");
        try {
            this.alipayClient = new DefaultAlipayClient(config);
        } catch (AlipayApiException e) {
            throw new RuntimeException("初始化支付宝客户端失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateQrCode(PayRequest request) {
        AlipayTradePrecreateRequest req = new AlipayTradePrecreateRequest();
        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", request.getOutTradeNo());
        biz.put("total_amount", request.getTotalAmount().toString());
        biz.put("subject", request.getDescription());
        req.setBizContent(biz.toString());
        req.setNotifyUrl(request.getNotifyUrl() != null
                ? request.getNotifyUrl() : properties.getNotifyUrl());
        try {
            AlipayTradePrecreateResponse resp = alipayClient.execute(req);
            if (!resp.isSuccess()) {
                throw new RuntimeException("支付宝预下单失败: " + resp.getMsg() + " " + resp.getSubMsg());
            }
            log.info("支付宝预下单成功: outTradeNo={}, qrCode={}", request.getOutTradeNo(), resp.getQrCode());
            return resp.getQrCode();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝预下单异常: " + e.getMessage(), e);
        }
    }

    @Override
    public PayResult microPay(MicroPayRequest request) {
        AlipayTradePayRequest req = new AlipayTradePayRequest();
        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", request.getOutTradeNo());
        biz.put("total_amount", request.getTotalAmount().toString());
        biz.put("subject", request.getDescription());
        biz.put("auth_code", request.getAuthCode());
        biz.put("scene", "bar_code");
        req.setBizContent(biz.toString());
        try {
            AlipayTradePayResponse resp = alipayClient.execute(req);
            String raw = JSONUtil.toJsonStr(resp);
            return PayResult.builder()
                    .outTradeNo(resp.getOutTradeNo())
                    .transactionId(resp.getTradeNo())
                    .status(resp.isSuccess() ? PayStatus.SUCCESS : PayStatus.NOTPAY)
                    .totalAmount(request.getTotalAmount())
                    .payerOpenid(resp.getBuyerUserId())
                    .rawResponse(raw)
                    .success(resp.isSuccess() && "10000".equals(resp.getCode()))
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝条码支付异常: " + e.getMessage(), e);
        }
    }

    @Override
    public PayResult queryOrder(String outTradeNo) {
        AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", outTradeNo);
        req.setBizContent(biz.toString());
        try {
            AlipayTradeQueryResponse resp = alipayClient.execute(req);
            String raw = JSONUtil.toJsonStr(resp);
            return PayResult.builder()
                    .outTradeNo(resp.getOutTradeNo())
                    .transactionId(resp.getTradeNo())
                    .status(convertStatus(resp.getTradeStatus()))
                    .totalAmount(resp.getTotalAmount() != null
                            ? new BigDecimal(resp.getTotalAmount()) : null)
                    .payerOpenid(resp.getBuyerUserId())
                    .rawResponse(raw)
                    .success(resp.isSuccess())
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝订单查询异常: " + e.getMessage(), e);
        }
    }

    @Override
    public void closeOrder(String outTradeNo) {
        AlipayTradeCloseRequest req = new AlipayTradeCloseRequest();
        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", outTradeNo);
        req.setBizContent(biz.toString());
        try {
            AlipayTradeCloseResponse resp = alipayClient.execute(req);
            if (!resp.isSuccess()) {
                throw new RuntimeException("支付宝关单失败: " + resp.getMsg());
            }
            log.info("支付宝关单成功: outTradeNo={}", outTradeNo);
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝关单异常: " + e.getMessage(), e);
        }
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        AlipayTradeRefundRequest req = new AlipayTradeRefundRequest();
        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", request.getOutTradeNo());
        biz.put("refund_amount", request.getRefundAmount().toString());
        biz.put("out_request_no", request.getOutRefundNo());
        if (request.getReason() != null) {
            biz.put("refund_reason", request.getReason());
        }
        req.setBizContent(biz.toString());
        try {
            AlipayTradeRefundResponse resp = alipayClient.execute(req);
            return RefundResult.builder()
                    .outRefundNo(request.getOutRefundNo())
                    .refundId(resp.getTradeNo())
                    .status(resp.isSuccess() ? "SUCCESS" : "FAILED")
                    .refundAmount(request.getRefundAmount())
                    .success(resp.isSuccess())
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝退款异常: " + e.getMessage(), e);
        }
    }

    @Override
    public RefundResult refundQuery(String outRefundNo) {
        AlipayTradeFastpayRefundQueryRequest req = new AlipayTradeFastpayRefundQueryRequest();
        JSONObject biz = new JSONObject();
        biz.put("out_request_no", outRefundNo);
        req.setBizContent(biz.toString());
        try {
            AlipayTradeFastpayRefundQueryResponse resp = alipayClient.execute(req);
            return RefundResult.builder()
                    .outRefundNo(resp.getOutRequestNo())
                    .refundId(resp.getTradeNo())
                    .status("SUCCESS")
                    .refundAmount(new BigDecimal(resp.getRefundAmount()))
                    .success(resp.isSuccess())
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝退款查询异常: " + e.getMessage(), e);
        }
    }

    @Override
    public PayNotifyResult handleNotify(String body, Map<String, String> params) {
        try {
            boolean verified = AlipaySignature.rsaCheckV1(
                    params, properties.getAlipayPublicKeyPath(), "UTF-8", "RSA2");
            if (!verified) {
                throw new RuntimeException("支付宝回调验签失败");
            }
            return PayNotifyResult.builder()
                    .outTradeNo(params.get("out_trade_no"))
                    .transactionId(params.get("trade_no"))
                    .status(convertStatus(params.get("trade_status")))
                    .totalAmount(new BigDecimal(params.get("total_amount")))
                    .eventType(params.get("event_type") != null
                            ? params.get("event_type") : "payment")
                    .rawBody(body)
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝回调处理异常: " + e.getMessage(), e);
        }
    }

    private PayStatus convertStatus(String tradeStatus) {
        if (tradeStatus == null) return PayStatus.NOTPAY;
        switch (tradeStatus) {
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return PayStatus.SUCCESS;
            case "TRADE_CLOSED":
                return PayStatus.CLOSED;
            case "WAIT_BUYER_PAY":
                return PayStatus.NOTPAY;
            default:
                return PayStatus.NOTPAY;
        }
    }
}
