package com.prideuniversal.sms;

import com.prideuniversal.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Production sender using MSG91's OTP API.
 *
 * <p>Requires TRAI DLT registration: {@code templateId} must be an approved
 * template and {@code senderId} an approved 6-character header, both registered
 * against the society's entity. Without that, MSG91 accepts the API call and
 * the message is dropped downstream - which is why delivery failures here are
 * logged with the provider's own response body rather than swallowed.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "msg91")
public class Msg91SmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(Msg91SmsSender.class);
    private static final String BASE_URL = "https://control.msg91.com/api/v5";

    private final RestClient client;
    private final AppProperties.Sms.Msg91 config;

    public Msg91SmsSender(AppProperties props, RestClient.Builder builder) {
        this.config = props.sms().msg91();
        if (config.authKey() == null || config.authKey().isBlank()) {
            throw new IllegalStateException(
                    "app.sms.msg91.auth-key is required when app.sms.provider=msg91");
        }
        this.client = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public void sendOtp(String mobile, String otp) {
        try {
            // MSG91 expects the number with the country code and no '+'.
            Map<String, Object> body = Map.of(
                    "template_id", config.templateId(),
                    "mobile", "91" + mobile,
                    "authkey", config.authKey(),
                    "otp", otp);

            Map<?, ?> response = client.post()
                    .uri("/otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            // MSG91 returns HTTP 200 with {"type":"error"} for business errors,
            // so a 2xx status alone does not mean the message went out.
            if (response == null || !"success".equals(response.get("type"))) {
                throw new SmsDeliveryException("MSG91 rejected the request: " + response);
            }

            log.info("OTP dispatched to +91{} via MSG91", maskMobile(mobile));

        } catch (RestClientException ex) {
            throw new SmsDeliveryException("Could not reach MSG91", ex);
        }
    }

    @Override
    public String providerName() {
        return "msg91";
    }

    /** Never log a full mobile number. */
    private String maskMobile(String mobile) {
        return mobile.length() == 10 ? mobile.substring(0, 2) + "******" + mobile.substring(8) : "****";
    }
}
