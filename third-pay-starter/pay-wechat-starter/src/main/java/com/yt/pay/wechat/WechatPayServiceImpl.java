package com.yt.pay.wechat;

import cn.hutool.json.JSONUtil;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.http.ApacheHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.http.HttpHeaders;
import com.wechat.pay.java.core.http.JsonRequestBody;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.yt.pay.model.*;
import com.yt.pay.wechat.config.WechatPayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 微信支付服务实现
 *
 * @author sunan
 */
@Slf4j
public class WechatPayServiceImpl implements WechatPayService {

    private final WechatPayProperties properties;
    private final Config config;
    private final NativePayService nativePayService;
    private final RefundService refundService;
    private final NotificationParser notificationParser;
    private final HttpClient httpClient;

    public WechatPayServiceImpl(WechatPayProperties properties) {
        this.properties = properties;
        this.config = new RSAAutoCertificateConfig.Builder()
                .merchantId(properties.getMerchantId())
                .privateKeyFromPath(properties.getPrivateKeyPath())
                .merchantSerialNumber(properties.getMerchantSerialNumber())
                .apiV3Key(properties.getApiV3Key())
                .build();
        this.nativePayService = new NativePayService.Builder().config(config).build();
        this.refundService = new RefundService.Builder().config(config).build();
        this.notificationParser = new NotificationParser((NotificationConfig) config);
        this.httpClient = new ApacheHttpClientBuilder().config(config).build();
    }

    @Override
    public String generateQrCode(PayRequest request) {
        Amount amount = new Amount();
        amount.setTotal(toFen(request.getTotalAmount()));
        amount.setCurrency("CNY");

        PrepayRequest prepayRequest = new PrepayRequest();
        prepayRequest.setOutTradeNo(request.getOutTradeNo());
        prepayRequest.setDescription(request.getDescription());
        prepayRequest.setAmount(amount);
        prepayRequest.setNotifyUrl(request.getNotifyUrl() != null
                ? request.getNotifyUrl() : properties.getNotifyUrl());

        PrepayResponse response = nativePayService.prepay(prepayRequest);
        log.info("微信 Native 下单成功: outTradeNo={}, codeUrl={}",
                request.getOutTradeNo(), response.getCodeUrl());
        return response.getCodeUrl();
    }

    @Override
    public PayResult microPay(MicroPayRequest request) {
        cn.hutool.json.JSONObject body = new cn.hutool.json.JSONObject();
        body.put("out_trade_no", request.getOutTradeNo());
        body.put("auth_code", request.getAuthCode());
        body.put("description", request.getDescription());
        cn.hutool.json.JSONObject amt = new cn.hutool.json.JSONObject();
        amt.put("total", toFen(request.getTotalAmount()));
        amt.put("currency", "CNY");
        body.put("amount", amt);

        String jsonBody = body.toString();
        log.info("微信付款码支付: outTradeNo={}", request.getOutTradeNo());

        HttpHeaders headers = new HttpHeaders();
        headers.addHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
        com.wechat.pay.java.core.http.HttpResponse<String> httpResp =
                httpClient.post(headers,
                        properties.getPayCodeUrl(),
                        new JsonRequestBody.Builder().body(jsonBody).build(),
                        String.class);

        String respBody = httpResp.getServiceResponse();
        cn.hutool.json.JSONObject result = cn.hutool.json.JSONUtil.parseObj(respBody);
        log.info("微信付款码支付返回: {}", result);

        if (result.containsKey("code")) {
            throw new RuntimeException("微信付款码支付失败: " + result);
        }
        return PayResult.builder()
                .outTradeNo(result.getStr("out_trade_no"))
                .transactionId(result.getStr("transaction_id"))
                .status(convertTradeState(result.getStr("trade_state")))
                .totalAmount(request.getTotalAmount())
                .payerOpenid(result.getJSONObject("payer") != null
                        ? result.getJSONObject("payer").getStr("openid") : null)
                .rawResponse(respBody)
                .success("SUCCESS".equals(result.getStr("trade_state")))
                .build();
    }

    @Override
    public PayResult queryOrder(String outTradeNo) {
        QueryOrderByOutTradeNoRequest req = new QueryOrderByOutTradeNoRequest();
        req.setOutTradeNo(outTradeNo);

        Transaction transaction = nativePayService.queryOrderByOutTradeNo(req);
        String raw = JSONUtil.toJsonStr(transaction);
        return PayResult.builder()
                .outTradeNo(transaction.getOutTradeNo())
                .transactionId(transaction.getTransactionId())
                .status(convertStatus(transaction.getTradeState()))
                .totalAmount(transaction.getAmount() != null
                        ? toYuan(transaction.getAmount().getTotal()) : null)
                .payerOpenid(transaction.getPayer() != null
                        ? transaction.getPayer().getOpenid() : null)
                .rawResponse(raw)
                .success(transaction.getTradeState() != null
                        && Transaction.TradeStateEnum.SUCCESS.equals(transaction.getTradeState()))
                .build();
    }

    @Override
    public void closeOrder(String outTradeNo) {
        CloseOrderRequest req = new CloseOrderRequest();
        req.setOutTradeNo(outTradeNo);
        nativePayService.closeOrder(req);
        log.info("微信关单成功: outTradeNo={}", outTradeNo);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        AmountReq amountReq = new AmountReq();
        amountReq.setRefund((long) toFen(request.getRefundAmount()));
        amountReq.setTotal((long) toFen(request.getTotalAmount()));
        amountReq.setCurrency("CNY");

        CreateRequest createRequest = new CreateRequest();
        createRequest.setOutTradeNo(request.getOutTradeNo());
        createRequest.setOutRefundNo(request.getOutRefundNo());
        createRequest.setAmount(amountReq);
        createRequest.setReason(request.getReason());

        Refund refund = refundService.create(createRequest);
        return RefundResult.builder()
                .outRefundNo(refund.getOutRefundNo())
                .refundId(refund.getRefundId())
                .status(refund.getStatus() != null ? refund.getStatus().name() : null)
                .refundAmount(request.getRefundAmount())
                .success(refund.getStatus() != null
                        && "SUCCESS".equals(refund.getStatus().name()))
                .build();
    }

    @Override
    public RefundResult refundQuery(String outRefundNo) {
        QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
        request.setOutRefundNo(outRefundNo);

        Refund refund = refundService.queryByOutRefundNo(request);
        return RefundResult.builder()
                .outRefundNo(refund.getOutRefundNo())
                .refundId(refund.getRefundId())
                .status(refund.getStatus() != null ? refund.getStatus().name() : null)
                .refundAmount(refund.getAmount() != null
                        ? toYuan(refund.getAmount().getRefund()) : null)
                .success(refund.getStatus() != null
                        && "SUCCESS".equals(refund.getStatus().name()))
                .build();
    }

    @Override
    public PayNotifyResult handleNotify(String body, Map<String, String> headers) {
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(headers.get("wechatpay-serial"))
                .nonce(headers.get("wechatpay-nonce"))
                .signature(headers.get("wechatpay-signature"))
                .timestamp(headers.get("wechatpay-timestamp"))
                .body(body)
                .build();

        Transaction transaction = notificationParser.parse(requestParam, Transaction.class);
        return PayNotifyResult.builder()
                .outTradeNo(transaction.getOutTradeNo())
                .transactionId(transaction.getTransactionId())
                .status(convertStatus(transaction.getTradeState()))
                .totalAmount(transaction.getAmount() != null
                        ? toYuan(transaction.getAmount().getTotal()) : null)
                .eventType("payment")
                .rawBody(body)
                .build();
    }

    // ============ helpers ============

    private int toFen(BigDecimal yuan) {
        return yuan.multiply(new BigDecimal("100")).intValue();
    }

    private BigDecimal toYuan(Integer fen) {
        return fen == null ? null
                : new BigDecimal(fen).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal toYuan(Long fen) {
        return fen == null ? null
                : new BigDecimal(fen).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private PayStatus convertTradeState(String tradeState) {
        if (tradeState == null) return PayStatus.NOTPAY;
        switch (tradeState) {
            case "SUCCESS": return PayStatus.SUCCESS;
            case "CLOSED": return PayStatus.CLOSED;
            case "NOTPAY": return PayStatus.NOTPAY;
            case "USERPAYING": return PayStatus.USERPAYING;
            default: return PayStatus.NOTPAY;
        }
    }

    private PayStatus convertStatus(Transaction.TradeStateEnum state) {
        if (state == null) return PayStatus.NOTPAY;
        switch (state) {
            case SUCCESS: return PayStatus.SUCCESS;
            case CLOSED: return PayStatus.CLOSED;
            case NOTPAY: return PayStatus.NOTPAY;
            case USERPAYING: return PayStatus.USERPAYING;
            default: return PayStatus.NOTPAY;
        }
    }
}
