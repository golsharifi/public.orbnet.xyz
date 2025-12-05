package com.orbvpn.api.service.payment.coinpayment;

import static com.orbvpn.api.service.payment.coinpayment.Constants.COINS_API_URL;
import static com.orbvpn.api.service.payment.coinpayment.Constants.SUCCESS_MESSAGE;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import com.orbvpn.api.domain.entity.CoinPayment;
import com.orbvpn.api.domain.entity.CoinPaymentCallback;
import com.orbvpn.api.domain.entity.Payment;
import com.orbvpn.api.domain.enums.PaymentStatus;
import com.orbvpn.api.domain.payload.CoinPayment.AddressResponse;
import com.orbvpn.api.domain.payload.CoinPayment.CoinPaymentResponse;
import com.orbvpn.api.domain.payload.CoinPayment.CoinPaymentsCreateTransactionRequest;
import com.orbvpn.api.domain.payload.CoinPayment.CoinPaymentsGetCallbackRequest;
import com.orbvpn.api.domain.payload.CoinPayment.CreateTransactionResponse;
import com.orbvpn.api.domain.payload.CoinPayment.TransactionResult;
import com.orbvpn.api.exception.PaymentException;
import com.orbvpn.api.repository.CoinPaymentCallbackRepository;
import com.orbvpn.api.repository.CoinPaymentRepository;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CoinPaymentService extends CoinPaymentBaseService {

    private static final Logger log = LoggerFactory.getLogger(CoinPaymentService.class);

    private final CoinPaymentRepository coinPaymentRepository;
    private final CoinPaymentCallbackRepository coinCallbackRepo;

    public CoinPaymentService(
            CoinPaymentRepository coinPaymentRepository,
            CoinPaymentCallbackRepository callbackRepo) {
        super();
        this.coinPaymentRepository = coinPaymentRepository;
        this.coinCallbackRepo = callbackRepo;
    }

    @Transactional
    public CoinPaymentResponse createPayment(CoinPayment payment) throws IOException {
        try {
            coinPaymentRepository.save(payment);

            HttpPost post = prepareDepositRequest(payment);
            CloseableHttpResponse response = client.execute(post);
            String content = EntityUtils.toString(response.getEntity());
            CreateTransactionResponse transactionResponse;

            try {
                transactionResponse = gson.fromJson(content, CreateTransactionResponse.class);
            } catch (Exception e) {
                payment.getPayment().setStatus(PaymentStatus.FAILED);
                payment.getPayment().setErrorMessage("Failed to parse response: " + e.getMessage());
                coinPaymentRepository.save(payment);
                return CoinPaymentResponse.builder()
                        .id(payment.getId())
                        .error(content)
                        .build();
            }

            // Add webhook URL to the payment
            payment.setIpnUrl(IPN_URL + "/" + payment.getId());

            // Add better error handling
            if (transactionResponse.getError() != null && !transactionResponse.getError().equals(SUCCESS_MESSAGE)) {
                payment.getPayment().setStatus(PaymentStatus.FAILED);
                payment.getPayment().setErrorMessage(transactionResponse.getError());
                coinPaymentRepository.save(payment);
                return CoinPaymentResponse.builder()
                        .id(payment.getId())
                        .error(transactionResponse.getError())
                        .build();
            }

            TransactionResult result = transactionResponse.getResult();
            payment.setAddress(result.getAddress());
            payment.setTimeout(result.getTimeout());
            payment.setTxnId(result.getTxn_id());
            payment.setCheckout_url(result.getCheckout_url());
            payment.setQrcode_url(result.getQrcode_url());
            payment.setStatus_url(result.getStatus_url());
            payment.setConfirms_needed(result.getConfirms_needed());
            // Store the coin amount as string (CoinPayment entity uses String type)
            payment.setCoinAmount(result.getAmount());

            // Add logging
            log.info("CoinPayment transaction created: {}", payment.getTxnId());

            coinPaymentRepository.save(payment);

            return CoinPaymentResponse.builder()
                    .id(payment.getId())
                    .txn_id(result.getTxn_id())
                    .address(result.getAddress())
                    .amount(result.getAmount())
                    .checkout_url(result.getCheckout_url())
                    .qrcode_url(result.getQrcode_url())
                    .status_url(result.getStatus_url())
                    .confirms_needed(result.getConfirms_needed())
                    .timeout(result.getTimeout())
                    .build();
        } catch (Exception e) {
            log.error("Error creating CoinPayment transaction", e);
            payment.getPayment().setStatus(PaymentStatus.FAILED);
            payment.getPayment().setErrorMessage(e.getMessage());
            coinPaymentRepository.save(payment);
            throw new PaymentException("Failed to create CoinPayment transaction", e);
        }
    }

    public AddressResponse createPayment(CoinPaymentCallback payment) throws IOException {
        coinCallbackRepo.save(payment);
        var post = prepareCallbackRequest(payment);
        var response = client.execute(post);
        var content = EntityUtils.toString(response.getEntity());
        AddressResponse callbackResponse;
        try {
            callbackResponse = gson.fromJson(content, AddressResponse.class);
        } catch (Exception e) {
            return AddressResponse.builder()
                    .error(content).build();
        }
        if (!callbackResponse.getError().equals(SUCCESS_MESSAGE))
            return null;

        var result = callbackResponse.getResult();
        payment.setAddress(result.getAddress());
        payment.setStatus(0);
        coinCallbackRepo.save(payment);
        return callbackResponse;
    }

    public CoinPaymentCallback getCallbackPayment(Long id) {
        return coinCallbackRepo.findById(id).orElse(null);
    }

    public void save(CoinPaymentCallback payment) {
        coinCallbackRepo.save(payment);
    }

    public CoinPayment getPayment(Long id) {
        return coinPaymentRepository.findById(id).orElse(null);
    }

    public void save(CoinPayment payment) {
        coinPaymentRepository.save(payment);
    }

    private HttpPost prepareDepositRequest(CoinPayment coinPayment) throws UnsupportedEncodingException {

        HttpPost post = new HttpPost(COINS_API_URL);
        post.addHeader("Connection", "close");
        post.addHeader("Accept", "*/*");
        post.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        post.addHeader("Cookie2", "$Version=1");
        post.addHeader("Accept-Language", "en-US");

        Payment payment = coinPayment.getPayment();
        CoinPaymentsCreateTransactionRequest request = CoinPaymentsCreateTransactionRequest.builder()
                .amount(payment.getPrice())
                .currency(coinPayment.getCoin())
                .buyerEmail(payment.getUser().getEmail())
                .build();

        String payload = request.toString();
        payload += "&version=1&key=" + publicKey + "&format-json";
        String hmac = buildHmacSignature(payload, privateKey);

        post.addHeader("HMAC", hmac);
        post.setEntity(new StringEntity(payload));

        return post;
    }

    private HttpPost prepareCallbackRequest(CoinPaymentCallback coinpayment) throws UnsupportedEncodingException {
        HttpPost post = new HttpPost(COINS_API_URL);
        post.addHeader("Connection", "close");
        post.addHeader("Accept", "*/*");
        post.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        post.addHeader("Cookie2", "$Version=1");
        post.addHeader("Accept-Language", "en-US");

        var ipnUrl = IPN_URL + "/" + coinpayment.getId();
        var request = CoinPaymentsGetCallbackRequest.builder()
                .currency(coinpayment.getCoin())
                .IPNUrl(ipnUrl)
                .build();
        var payload = request.toString();
        payload += "&version=1&key=" + publicKey + "&format-json";
        String hmac = buildHmacSignature(payload, privateKey);

        post.addHeader("HMAC", hmac);
        post.setEntity(new StringEntity(payload));

        return post;
    }

}
