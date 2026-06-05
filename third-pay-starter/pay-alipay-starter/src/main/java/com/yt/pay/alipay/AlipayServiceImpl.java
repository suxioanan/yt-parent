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
import com.yt.pay.alipay.config.AlipayProperties;
import com.yt.pay.model.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 支付宝服务实现
 *
 * @author sunan
 */
@Slf4j
public class AlipayServiceImpl implements AlipayService {

    private static final String SIGN_TYPE = "RSA2";
    private static final String FORMAT = "json";
    private static final String CHARSET = "UTF-8";
    private static final String SCENE_BAR_CODE = "bar_code";

    private final AlipayProperties properties;
    private final AlipayClient alipayClient;
    private final String alipayPublicKeyContent;

    public AlipayServiceImpl(AlipayProperties properties) {
        this.properties = properties;
        String privateKeyContent = readKeyContent(properties.getPrivateKeyPath());
        this.alipayPublicKeyContent = readKeyContent(properties.getAlipayPublicKeyPath());

        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.getGatewayUrl());
        config.setAppId(properties.getAppId());
        config.setPrivateKey(privateKeyContent);
        config.setAlipayPublicKey(alipayPublicKeyContent);
        config.setFormat(FORMAT);
        config.setCharset(CHARSET);
        config.setSignType(SIGN_TYPE);
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
        if (request.getExpireMinutes() != null) {
            biz.put("timeout_express", request.getExpireMinutes() + "m");
        }
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
        biz.put("scene", SCENE_BAR_CODE);
        req.setBizContent(biz.toString());
        try {
            AlipayTradePayResponse resp = alipayClient.execute(req);
            String raw = JSONUtil.toJsonStr(resp);
            boolean success = resp.isSuccess();
            return PayResult.builder()
                    .outTradeNo(resp.getOutTradeNo())
                    .transactionId(resp.getTradeNo())
                    .status(success ? PayStatus.SUCCESS : PayStatus.NOTPAY)
                    .totalAmount(request.getTotalAmount())
                    .payerOpenid(resp.getBuyerUserId())
                    .rawResponse(raw)
                    .success(success)
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
            PayStatus status = convertStatus(resp.getTradeStatus());
            return PayResult.builder()
                    .outTradeNo(resp.getOutTradeNo())
                    .transactionId(resp.getTradeNo())
                    .status(status)
                    .totalAmount(resp.getTotalAmount() != null
                            ? new BigDecimal(resp.getTotalAmount()) : null)
                    .payerOpenid(resp.getBuyerUserId())
                    .rawResponse(raw)
                    .success(status == PayStatus.SUCCESS)
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
            boolean success = resp.isSuccess();
            return RefundResult.builder()
                    .outRefundNo(request.getOutRefundNo())
                    .refundId(request.getOutRefundNo())
                    .status(success ? RefundStatus.SUCCESS : RefundStatus.FAILED)
                    .refundAmount(request.getRefundAmount())
                    .success(success)
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝退款异常: " + e.getMessage(), e);
        }
    }


    /**
     *
     * @author sunan
     * @date 2026/6/5
     * @param outTradeNo  订单号
     * @param outRefundNo  outRefundNo退款返回的参数
     * @return
     **/
    @Override
    public RefundResult refundQuery(String outTradeNo, String outRefundNo) {
        AlipayTradeFastpayRefundQueryRequest req = new AlipayTradeFastpayRefundQueryRequest();
        JSONObject biz = new JSONObject();
        biz.put("out_trade_no", outTradeNo);
        biz.put("out_request_no", outRefundNo);
        req.setBizContent(biz.toString());
        try {
            AlipayTradeFastpayRefundQueryResponse resp = alipayClient.execute(req);
            boolean success = resp.isSuccess();
            return RefundResult.builder()
                    .outRefundNo(resp.getOutRequestNo())
                    .refundId(resp.getOutRequestNo() != null ? resp.getOutRequestNo() : outRefundNo)
                    .status(success ? RefundStatus.SUCCESS : RefundStatus.FAILED)
                    .refundAmount(resp.getRefundAmount() != null
                            ? new BigDecimal(resp.getRefundAmount()) : null)
                    .success(success)
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝退款查询异常: " + e.getMessage(), e);
        }
    }

    @Override
    public PayNotifyResult handleNotify(String body, Map<String, String> extra) {
        try {
            boolean verified = AlipaySignature.rsaCheckV1(
                    extra, alipayPublicKeyContent, CHARSET, SIGN_TYPE);
            if (!verified) {
                throw new RuntimeException("支付宝回调验签失败");
            }
            String totalAmountStr = extra.get("total_amount");
            return PayNotifyResult.builder()
                    .outTradeNo(extra.get("out_trade_no"))
                    .transactionId(extra.get("trade_no"))
                    .status(convertStatus(extra.get("trade_status")))
                    .totalAmount(totalAmountStr != null ? new BigDecimal(totalAmountStr) : null)
                    .eventType(extra.get("event_type") != null
                            ? extra.get("event_type") : "payment")
                    .rawBody(body)
                    .build();
        } catch (AlipayApiException e) {
            throw new RuntimeException("支付宝回调处理异常: " + e.getMessage(), e);
        }
    }

    // ============ helpers ============

    /**
     * 读取密钥内容，优先从文件系统路径读取，失败则尝试 classpath
     */
    private String readKeyContent(String path) {
        // 1. 尝试文件系统
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException ignored) {
            // fall through to classpath
            log.error("根据文件路径读取文件失败:{}",path);
        }
        // 2. 尝试 classpath
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
                path.startsWith("/") ? path.substring(1) : path)) {
            if (in == null) {
                throw new RuntimeException("密钥文件不存在（文件系统 + classpath 均未找到）: " + path);
            }
            return new String(in.readAllBytes());
        } catch (IOException e) {
            log.error("读取文件失败:{}",path);
            throw new RuntimeException("读取密钥文件失败: " + path, e);
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
