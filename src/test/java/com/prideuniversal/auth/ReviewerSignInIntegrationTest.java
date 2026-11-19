package com.prideuniversal.auth;

import com.prideuniversal.AbstractIntegrationTest;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The store reviewer: 1234567890 signs in with the configured code, as an ordinary resident. */
class ReviewerSignInIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("the reviewer number signs in with the fixed code and sees a resident's app")
    void reviewerSignsIn() throws Exception {
        if (userRepository.findByMobile("1234567890").isEmpty()) {
            Flat flat = createFlat();
            User u = new User();
            u.setMobile("1234567890");
            u.setDisplayName("Play Store Reviewer");
            u.setRole(Role.RESIDENT);
            u.setFlat(flat);
            u.setActive(true);
            userRepository.save(u);
        }
        verifyOtp("1234567890", "000000"); // no code requested yet: refused, nothing to consume
        postAs(null, "/api/auth/otp/send", java.util.Map.of("mobile", "1234567890")).andExpect(status().isOk());
        verifyOtp("1234567890", "999999").andExpect(status().isUnauthorized());
        String token = objectMapper.readTree(verifyOtp("1234567890", "123456")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("access_token").asText();
        getAs(token, "/api/auth/me").andExpect(jsonPath("$.role").value("resident"));
    }
}
