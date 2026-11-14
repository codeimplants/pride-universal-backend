package com.prideuniversal.sms;

import com.prideuniversal.common.ApiException;
import com.prideuniversal.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Unit tests: no Spring context, no Docker. The contract is RestoBill's, which is live in prod. */
class MessagingSmsSenderTest {

    private static final String TEST_KEY = "sk_test_abc123";
    private static final String LIVE_KEY = "sk_live_abc123";

    @Test
    @DisplayName("posts phone in E.164, purpose login and our own code, with bearer key and idempotency key")
    void sendsTheCodeToMessaging() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dev.msg.example/v1/otp/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TEST_KEY))
                .andExpect(header("Idempotency-Key", org.hamcrest.Matchers.startsWith("pride-")))
                .andExpect(content().json("{\"phone\":\"+919876543210\",\"purpose\":\"login\",\"code\":\"482910\"}", true))
                .andRespond(withSuccess("{\"otpId\":\"otp_1\",\"status\":\"accepted\"}", MediaType.APPLICATION_JSON));

        sender(builder, TEST_KEY, null, false).sendOtp("9876543210", "482910");

        server.verify();
    }

    @Test
    @DisplayName("a 429 from messaging is a 429 to the caller, not a server error")
    void rateLimitIsTooManyRequests() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dev.msg.example/v1/otp/send"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"code\":\"user_daily_cap\"}}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender(builder, TEST_KEY, null, false).sendOtp("9876543210", "482910"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("any other failure is an SMS delivery failure")
    void providerFailureIsDeliveryException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dev.msg.example/v1/otp/send"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> sender(builder, TEST_KEY, null, false).sendOtp("9876543210", "482910"))
                .isInstanceOf(SmsSender.SmsDeliveryException.class);
    }

    @Test
    @DisplayName("the fixed test code is offered on a test key")
    void fixedCodeOnTestKey() {
        assertThat(sender(RestClient.builder(), TEST_KEY, "123456", false).fixedCode()).contains("123456");
        assertThat(sender(RestClient.builder(), TEST_KEY, "", false).fixedCode()).isEmpty();
    }

    @Test
    @DisplayName("a fixed code with a live key refuses to start")
    void fixedCodeWithLiveKeyIsRefused() {
        assertThatThrownBy(() -> sender(RestClient.builder(), LIVE_KEY, "123456", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MESSAGING_FIXED_CODE");
    }

    @Test
    @DisplayName("OTP dev-mode with a live key refuses to start")
    void devModeWithLiveKeyIsRefused() {
        assertThatThrownBy(() -> sender(RestClient.builder(), LIVE_KEY, null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OTP_DEV_MODE");
    }

    @Test
    @DisplayName("no API key refuses to start")
    void missingKeyIsRefused() {
        assertThatThrownBy(() -> sender(RestClient.builder(), " ", null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MESSAGING_API_KEY");
    }

    private static MessagingSmsSender sender(RestClient.Builder builder, String key, String fixed, boolean devMode) {
        AppProperties props = new AppProperties(
                null,
                new AppProperties.Otp(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60), devMode),
                new AppProperties.Sms("messaging",
                        new AppProperties.Sms.Messaging("https://dev.msg.example/", key, fixed)),
                null,
                new AppProperties.Cors(List.of()),
                new AppProperties.Admin(null),
                null);
        return new MessagingSmsSender(props, builder);
    }
}
