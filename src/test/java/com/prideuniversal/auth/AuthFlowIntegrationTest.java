package com.prideuniversal.auth;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end regression tests for authentication and authorisation.
 *
 * <p>Every test here pins a bug that was real. The three from LEARNING-LOG
 * entry 005 were all found by running the app by hand after it compiled
 * cleanly — these exist so the next person cannot reintroduce them quietly.
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // =====================================================================
    // Bug 2 - anonymous requests returned a bare 403 instead of 401.
    // The client contract depends on the difference: 401 means "refresh the
    // token and retry", 403 means "stop and tell the user".
    // =====================================================================

    @Test
    @DisplayName("anonymous request to a protected route -> 401 with an ApiError body")
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("unparseable bearer token -> 401, not 500")
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    @DisplayName("authenticated resident on an admin-only route -> 403, not 401")
    void residentIsForbiddenFromAdminRoute() throws Exception {
        String token = loginAsNewResident();

        mockMvc.perform(get("/actuator/metrics").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("admin on an admin-only route -> 200 (proves 403 above is a real decision)")
    void adminReachesAdminRoute() throws Exception {
        String token = loginAs(Role.ADMIN);

        mockMvc.perform(get("/actuator/metrics").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    // =====================================================================
    // The headline security property. In the base44 app the role lived in
    // sessionStorage, so setItem('otp_user_role','admin') made you an admin.
    // Here it is a claim inside an HMAC-signed token.
    // =====================================================================

    @Test
    @DisplayName("editing the role claim to admin invalidates the signature -> 401")
    void tamperedRoleClaimIsRejected() throws Exception {
        String token = loginAsNewResident();
        String forged = tamperRoleClaim(token, "admin");

        // Sanity-check that the forgery actually says what we think it does -
        // otherwise a bug in the test helper could make this pass vacuously.
        assertThat(roleClaimOf(forged)).isEqualTo("admin");
        assertThat(roleClaimOf(token)).isEqualTo("resident");

        mockMvc.perform(get("/actuator/metrics").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // Bug 1 - @Enumerated(STRING) wrote "ADMIN"; ck_user_role only accepts
    // 'admin'. Asserted against the raw column, because reading it back
    // through the same converter that writes it would pass either way.
    // =====================================================================

    @Test
    @DisplayName("Role persists as its lowercase code, not the enum constant name")
    void rolePersistsAsLowercaseCode() throws Exception {
        String mobile = newResidentMobile();
        loginAs(mobile);

        String stored = jdbcTemplate.queryForObject(
                "select role from app_user where mobile = ?", String.class, mobile);

        assertThat(stored).isEqualTo("resident");
        assertThat(stored).isNotEqualTo(Role.RESIDENT.name());
    }

    @Test
    @DisplayName("a resident is auto-provisioned from the flat's owner contact")
    void residentIsProvisionedOnFirstLogin() throws Exception {
        String mobile = newResidentMobile();
        assertThat(userRepository.existsByMobile(mobile)).isFalse();

        loginAs(mobile);

        // findWithFlat... and not findByMobile: the flat is a LAZY association,
        // so with open-in-view disabled a plain lookup would hand back an
        // uninitialised proxy and assertNotNull would pass without proving
        // anything. This query fetch-joins it.
        User created = userRepository.findWithFlatByMobile(mobile).orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.RESIDENT);
        assertThat(created.getFlat()).isNotNull();
        assertThat(created.getFlat().getOwnerContact()).isEqualTo(mobile);
    }

    // =====================================================================
    // Bug 3 - @ExceptionHandler(Exception.class) turned framework exceptions
    // for ordinary client mistakes into 500s.
    // =====================================================================

    @Test
    @DisplayName("unmapped path -> 404, not 500")
    void unmappedPathIsNotFound() throws Exception {
        String token = loginAs(Role.ADMIN);

        mockMvc.perform(get("/api/does-not-exist").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    @DisplayName("malformed JSON body -> 400, not 500")
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"9876500001\" this is not json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    @Test
    @DisplayName("wrong HTTP method -> 405, handled via ErrorResponse with no named handler")
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/auth/otp/send"))
                .andExpect(status().isMethodNotAllowed());
    }

    // =====================================================================
    // OTP controls
    // =====================================================================

    @Test
    @DisplayName("an OTP cannot be replayed")
    void otpIsSingleUse() throws Exception {
        String mobile = newResidentMobile();
        String otp = requestOtp(mobile);

        verifyOtp(mobile, otp).andExpect(status().isOk());
        verifyOtp(mobile, otp).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unregistered number is refused an OTP entirely")
    void unregisteredNumberGetsNoOtp() throws Exception {
        mockMvc.perform(post("/api/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"9111100000\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_registered"));
    }

    @Test
    @DisplayName("a second OTP within the cooldown is rate limited")
    void resendWithinCooldownIsRateLimited() throws Exception {
        String mobile = newResidentMobile();
        requestOtp(mobile);

        mockMvc.perform(post("/api/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + mobile + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    // =====================================================================
    // Helpers specific to token forgery
    // =====================================================================

    /**
     * Rewrites the {@code role} claim while leaving the original signature
     * attached - i.e. exactly what an attacker with a valid token would try.
     */
    private String tamperRoleClaim(String jwt, String newRole) throws Exception {
        String[] parts = jwt.split("\\.");
        ObjectNode payload = (ObjectNode) objectMapper.readTree(
                Base64.getUrlDecoder().decode(parts[1]));
        payload.put("role", newRole);

        String rewritten = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(payload));

        return parts[0] + "." + rewritten + "." + parts[2];
    }

    private String roleClaimOf(String jwt) throws Exception {
        String payload = jwt.split("\\.")[1];
        return objectMapper.readTree(Base64.getUrlDecoder().decode(payload))
                .get("role").asText();
    }
}
