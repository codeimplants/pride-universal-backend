package com.prideuniversal.sms;

import com.prideuniversal.common.ApiException;
import com.prideuniversal.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Delivers OTPs through Code Implants Messaging ({@code msg.codeimplants.com}).
 *
 * <p>Pride Universal still generates, hashes and verifies its own codes; messaging is
 * transport only. It holds the shared 2Factor account and its approved OTP template,
 * applies per-number limits, and reports usage per app. This is the same "app owns
 * the code" integration RestoBill uses ({@code OtpSenderConfig} there).
 *
 * <p>{@code POST {base}/v1/otp/send} with {@code {"phone","purpose":"login","code"}},
 * {@code Bearer sk_…} and an {@code Idempotency-Key}. The key is derived from the
 * phone and code, so a retried send is answered from the first and never sent twice.
 *
 * <p>Environments: on dev, messaging dev sends nothing and {@code MESSAGING_FIXED_CODE=123456}
 * gives the fixed code every Code Implants app uses outside prod. A fixed code with a
 * live ({@code sk_live_}) key is refused at startup — it would let anyone sign in as anyone.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "messaging")
public class MessagingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MessagingSmsSender.class);

    private final RestClient client;
    private final String apiKey;
    private final Optional<String> fixedCode;

    public MessagingSmsSender(AppProperties props, RestClient.Builder builder) {
        AppProperties.Sms.Messaging config = props.sms().messaging();
        if (config == null || config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("app.sms.provider=messaging needs MESSAGING_API_KEY");
        }
        this.apiKey = config.apiKey().strip();
        boolean liveKey = apiKey.startsWith("sk_live_");

        String fixed = config.fixedCode() == null ? "" : config.fixedCode().strip();
        if (!fixed.isEmpty() && liveKey) {
            throw new IllegalStateException(
                    "MESSAGING_FIXED_CODE must not be set with a live (sk_live_) key");
        }
        if (props.otp().devMode() && liveKey) {
            // dev-mode returns the code in the HTTP response. With real SMS behind it,
            // that is a login bypass for every account.
            throw new IllegalStateException(
                    "OTP_DEV_MODE must be false with a live (sk_live_) messaging key");
        }
        this.fixedCode = fixed.isEmpty() ? Optional.empty() : Optional.of(fixed);
        fixedCode.ifPresent(c -> log.warn("SMS provider 'messaging' with a fixed code. Test environments only."));

        String baseUrl = config.baseUrl() == null || config.baseUrl().isBlank()
                ? "https://msg.codeimplants.com"
                : config.baseUrl().strip().replaceAll("/+$", "");

        // Timeouts come from spring.http.client.* in application.yml, applied to the
        // auto-configured builder (and replaceable by a mock server in tests).
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Optional<String> fixedCode() {
        return fixedCode;
    }

    @Override
    public void sendOtp(String mobile, String otp) {
        String phone = "+91" + mobile;
        try {
            client.post()
                    .uri("/v1/otp/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Idempotency-Key", "pride-" + sha256(phone + "|" + otp).substring(0, 40))
                    .body(Map.of("phone", phone, "purpose", "login", "code", otp))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        int status = response.getStatusCode().value();
                        String body = abbreviate(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
                        if (status == 429) {
                            log.warn("Messaging refused an OTP for {} on a per-number limit: {}", mask(mobile), body);
                            throw ApiException.tooManyRequests(
                                    "Too many codes requested for this number. Please try again later.");
                        }
                        log.error("Messaging refused the OTP for {}: HTTP {} {}", mask(mobile), status, body);
                        throw new SmsDeliveryException("Messaging returned HTTP " + status);
                    })
                    .toBodilessEntity();
            log.info("OTP handed to messaging for {}", mask(mobile));
        } catch (RestClientException ex) {
            log.error("Messaging unreachable for {}: {}", mask(mobile), ex.getMessage());
            throw new SmsDeliveryException("Could not reach messaging", ex);
        }
    }

    @Override
    public String providerName() {
        return "messaging";
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String mask(String mobile) {
        return mobile.length() < 4 ? "****" : "******" + mobile.substring(mobile.length() - 4);
    }

    private static String abbreviate(String body) {
        return body == null ? "" : body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }
}
