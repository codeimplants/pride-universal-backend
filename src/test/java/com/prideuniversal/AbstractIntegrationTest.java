package com.prideuniversal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.user.User;
import com.prideuniversal.domain.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that need the real application wired to a real database.
 *
 * <h2>Why a container and not H2</h2>
 * The schema depends on things H2 does not have: {@code gen_random_uuid()},
 * the {@code ~} regex operator inside CHECK constraints, and {@code TIMESTAMPTZ}.
 * An in-memory substitute would not even accept
 * {@code V1__initial_schema.sql}, and — more to the point — the enum-mapping
 * bug recorded in LEARNING-LOG entry 005 was caught *by* a Postgres CHECK
 * constraint. A fake database would have hidden it. Tests run against the same
 * engine and version as production, or they are not evidence.
 *
 * <h2>Why the container is started in a static block</h2>
 * This is the Testcontainers "singleton container" pattern, used deliberately
 * instead of the {@code @Testcontainers} / {@code @Container} JUnit extension.
 * That extension ties the container's lifetime to a single test class, so every
 * class pays a fresh Postgres startup. Started here, one container serves the
 * whole JVM run and is reclaimed by Testcontainers' Ryuk sidecar when the JVM
 * exits — so there is deliberately no {@code stop()} call and no shutdown hook.
 *
 * <p>Spring caches the application context across test classes on the same
 * configuration, so the pairing matters: one container, one context, many
 * classes.
 *
 * <h2>Note on isolation</h2>
 * These tests are not {@code @Transactional}, because the rollback-after-test
 * trick fights a MockMvc request that manages its own transactions. Isolation
 * comes from each test allocating its own fixtures instead, which also keeps
 * the OTP rate limiters (per mobile number) out of each other's way.
 */
@SpringBootTest(properties = {"app.otp.dev-mode=true", "app.admin.service-token=" + AbstractIntegrationTest.CONSOLE_TOKEN})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    /** Nexus's service token in tests. */
    protected static final String CONSOLE_TOKEN = "test-console-token-0123456789";

    // Non-generic in Testcontainers 2.x - the old self-referential
    // PostgreSQLContainer<SELF> generic was dropped, so no <?> here.
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /**
     * Points Spring at the container's randomised port. Flyway then runs the
     * real migration against it, exactly as it does at startup.
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected FlatRepository flatRepository;
    @Autowired
    protected UserRepository userRepository;

    /**
     * Every fixture claims its own flat number and mobile. Sharing one would
     * make tests interfere through the OTP resend cooldown and the per-number
     * burst limiter, both keyed on the mobile.
     */
    private static final AtomicInteger SEQ = new AtomicInteger(900);

    protected static int nextSeq() {
        return SEQ.incrementAndGet();
    }

    protected static String mobileFor(int n) {
        return "98765" + String.format("%05d", n);
    }

    // =====================================================================
    // Fixtures
    // =====================================================================

    /** A flat in wing A with a unique number, owned by a unique mobile. */
    protected Flat createFlat() {
        int n = nextSeq();
        return createFlat("A", String.valueOf(n), mobileFor(n));
    }

    protected Flat createFlat(String wing, String number, String ownerContact) {
        Flat flat = new Flat();
        flat.setWing(wing);
        flat.setFlatNumber(number);
        flat.setOwnerName("Test Owner " + number);
        flat.setOwnerContact(ownerContact);
        return flatRepository.save(flat);
    }

    /** Creates a flat and returns the owner's mobile, ready to log in with. */
    protected String newResidentMobile() {
        return createFlat().getOwnerContact();
    }

    /**
     * Creates an account at a given role directly.
     *
     * <p>Committee roles are never reachable by logging in — that is the whole
     * design — so a test must not be able to shortcut it either.
     */
    protected String newUserMobile(Role role) {
        int n = nextSeq();
        String mobile = mobileFor(n);

        User user = new User();
        user.setMobile(mobile);
        user.setDisplayName("Test " + role.getCode() + " " + n);
        user.setRole(role);
        user.setActive(true);
        userRepository.save(user);

        return mobile;
    }

    // =====================================================================
    // Login helpers
    // =====================================================================

    protected String loginAsNewResident() throws Exception {
        return loginAs(newResidentMobile());
    }

    protected String loginAs(Role role) throws Exception {
        return loginAs(newUserMobile(role));
    }

    /** Full OTP round trip, returning the access token. */
    protected String loginAs(String mobile) throws Exception {
        String otp = requestOtp(mobile);
        String body = verifyOtp(mobile, otp)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("access_token").asText();
    }

    /** Sends an OTP and reads the code back out of the dev-mode response. */
    protected String requestOtp(String mobile) throws Exception {
        String body = mockMvc.perform(post("/api/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"" + mobile + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String devOtp = objectMapper.readTree(body).path("dev_otp").asText(null);
        assertThat(devOtp)
                .as("dev_otp is only returned while app.otp.dev-mode is true")
                .isNotNull();
        return devOtp;
    }

    protected ResultActions verifyOtp(String mobile, String otp) throws Exception {
        return mockMvc.perform(post("/api/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobile\":\"" + mobile + "\",\"otp\":\"" + otp + "\"}"));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // =====================================================================
    // Request helpers for the domain suites
    // =====================================================================

    protected ResultActions getAs(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", bearer(token)));
    }

    protected ResultActions postAs(String token, String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : objectMapper.writeValueAsString(body)));
    }

    protected ResultActions putAs(String token, String url, Object body) throws Exception {
        return mockMvc.perform(put(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions deleteAs(String token, String url) throws Exception {
        return mockMvc.perform(delete(url).header("Authorization", bearer(token)));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** A new flat in the given wing; returns its owner's mobile, ready to log in with. */
    protected String residentOfWing(String wing) {
        int n = nextSeq();
        return createFlat(wing, String.valueOf(n), mobileFor(n)).getOwnerContact();
    }
}
