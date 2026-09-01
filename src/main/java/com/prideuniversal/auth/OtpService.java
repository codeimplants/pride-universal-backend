package com.prideuniversal.auth;

import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Role;
import com.prideuniversal.config.AppProperties;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.user.User;
import com.prideuniversal.domain.user.UserRepository;
import com.prideuniversal.sms.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issues and verifies login OTPs.
 *
 * <p>Replaces the base44 implementation, which compared every submitted code
 * against a hardcoded {@code '000000'}, printed that code on the login screen,
 * and returned a role in plain JSON that the browser then stored in
 * sessionStorage. Here: codes are random, hashed at rest, single-use,
 * time-limited, attempt-limited and rate-limited, and the role comes from the
 * database into a signed token.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpRequestRepository otpRepository;
    private final OtpAttemptRecorder attemptRecorder;
    private final UserRepository userRepository;
    private final FlatRepository flatRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsSender smsSender;
    private final AppProperties.Otp config;

    private final SecureRandom random = new SecureRandom();

    /**
     * Burst protection, in memory and per mobile number.
     *
     * <p>The database cooldown below is the durable limit; this is the cheap
     * one that stops a flood before it reaches Postgres. ConcurrentHashMap
     * rather than a synchronized HashMap because the whole map should not lock
     * for an operation touching one key.
     */
    private final ConcurrentHashMap<String, Window> burstWindows = new ConcurrentHashMap<>();
    private static final int BURST_LIMIT = 3;
    private static final long BURST_WINDOW_SECONDS = 300;

    private record Window(Instant startedAt, AtomicInteger count) {}

    public OtpService(OtpRequestRepository otpRepository,
                      OtpAttemptRecorder attemptRecorder,
                      UserRepository userRepository,
                      FlatRepository flatRepository,
                      PasswordEncoder passwordEncoder,
                      SmsSender smsSender,
                      AppProperties props) {
        this.otpRepository = otpRepository;
        this.attemptRecorder = attemptRecorder;
        this.userRepository = userRepository;
        this.flatRepository = flatRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsSender = smsSender;
        this.config = props.otp();
    }

    // ---------------------------------------------------------------------
    // Sending
    // ---------------------------------------------------------------------

    /**
     * Issues an OTP to a mobile number, provided that number belongs to a flat
     * or an existing account.
     *
     * @return the plaintext code when running in dev mode, otherwise null.
     *         Nothing else in the system ever sees the code again.
     */
    @Transactional
    public String send(String mobile, String requestIp) {
        requireValidMobile(mobile);
        checkBurstLimit(mobile);

        // The number must be known to the society. An unknown number gets no
        // OTP at all - we never create accounts from an unsolicited login.
        if (!isKnownMobile(mobile)) {
            log.info("OTP requested for unregistered mobile {}", mask(mobile));
            throw new ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "not_registered",
                    "This number is not registered with the society. "
                            + "Please contact the society office to be added.");
        }

        // Durable resend cooldown - survives a restart, unlike the in-memory one.
        Instant cooldownStart = Instant.now().minus(config.resendCooldown());
        if (otpRepository.countRecent(mobile, cooldownStart) > 0) {
            throw ApiException.tooManyRequests(
                    "An OTP was just sent. Please wait before requesting another.");
        }

        String code = generateCode();

        OtpRequest request = new OtpRequest();
        request.setMobile(mobile);
        // Hashed, never stored in plaintext.
        request.setCodeHash(passwordEncoder.encode(code));
        request.setExpiresAt(Instant.now().plus(config.ttl()));
        request.setRequestIp(requestIp);
        otpRepository.save(request);

        smsSender.sendOtp(mobile, code);

        // Returned only so the dev UI can show it without an SMS account.
        return config.devMode() ? code : null;
    }

    // ---------------------------------------------------------------------
    // Verifying
    // ---------------------------------------------------------------------

    /**
     * Checks a submitted code and, on success, returns the account it belongs
     * to - creating a resident account on first login if the number matches a
     * flat but has no account yet.
     */
    @Transactional
    public User verify(String mobile, String code) {
        requireValidMobile(mobile);

        OtpRequest otp = otpRepository.findUsable(mobile, Instant.now())
                .orElseThrow(() -> ApiException.unauthorized(
                        "That code has expired or was already used. Please request a new one."));

        if (otp.getAttempts() >= config.maxAttempts()) {
            throw ApiException.tooManyRequests(
                    "Too many incorrect attempts. Please request a new code.");
        }

        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            // Committed in its own transaction so the throw below cannot undo
            // it. See OtpAttemptRecorder for why this is a separate bean.
            attemptRecorder.recordFailedAttempt(otp.getId());
            throw ApiException.unauthorized("Incorrect code. Please try again.");
        }

        // Single use: consume it before issuing any tokens.
        otp.setConsumedAt(Instant.now());
        otpRepository.save(otp);
        burstWindows.remove(mobile);

        User user = findOrProvisionUser(mobile);
        user.setLastLoginAt(Instant.now());

        log.info("Successful OTP login for {} as {}", mask(mobile), user.getRole().getCode());
        return user;
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /**
     * An account exists, or the number belongs to a flat and we create a
     * resident account for it.
     *
     * <p>Note what is NOT possible here: self-provisioning at any role above
     * resident. Committee roles are assigned by an admin, never inferred from
     * the login itself. The old function hardcoded an admin mobile number in
     * source, which meant the repository was the access-control list.
     */
    private User findOrProvisionUser(String mobile) {
        Optional<User> existing = userRepository.findWithFlatByMobile(mobile);
        if (existing.isPresent()) {
            User user = existing.get();
            if (!user.isActive()) {
                throw ApiException.forbidden(
                        "This account has been deactivated. Please contact the society office.");
            }
            return user;
        }

        List<Flat> flats = flatRepository.findByAnyContact(mobile);
        if (flats.isEmpty()) {
            // Should be unreachable - send() already checked - but never
            // fall through into creating an unattached account.
            throw ApiException.forbidden("This number is not linked to any flat.");
        }

        Flat flat = flats.get(0);
        User user = new User();
        user.setMobile(mobile);
        user.setDisplayName(nameFor(flat, mobile));
        user.setRole(Role.RESIDENT);
        user.setFlat(flat);
        user.setActive(true);

        log.info("Provisioned resident account for {} in flat {}-{}",
                mask(mobile), flat.getWing(), flat.getFlatNumber());

        return userRepository.save(user);
    }

    private String nameFor(Flat flat, String mobile) {
        if (mobile.equals(flat.getTenantContact()) && flat.getTenantName() != null) {
            return flat.getTenantName();
        }
        return flat.getOwnerName();
    }

    private boolean isKnownMobile(String mobile) {
        return userRepository.existsByMobile(mobile)
                || !flatRepository.findByAnyContact(mobile).isEmpty();
    }

    /**
     * Cryptographically random code, zero-padded to the configured length.
     * {@link SecureRandom}, not {@code Math.random()} - a predictable OTP is
     * not an OTP.
     */
    private String generateCode() {
        int bound = (int) Math.pow(10, config.length());
        return String.format("%0" + config.length() + "d", random.nextInt(bound));
    }

    /**
     * Fixed-window burst check.
     *
     * <p>{@code compute} is the point of interest: it applies the whole
     * read-modify-write atomically for that key. A {@code get} followed by a
     * {@code put} would let two concurrent requests both read count=2 and both
     * store 3, and the limit would leak under exactly the load it exists to stop.
     */
    private void checkBurstLimit(String mobile) {
        Instant now = Instant.now();
        Window window = burstWindows.compute(mobile, (key, existing) -> {
            if (existing == null
                    || existing.startedAt().plusSeconds(BURST_WINDOW_SECONDS).isBefore(now)) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (window.count().get() > BURST_LIMIT) {
            log.warn("Burst limit hit for {}", mask(mobile));
            throw ApiException.tooManyRequests(
                    "Too many OTP requests. Please try again in a few minutes.");
        }
    }

    private void requireValidMobile(String mobile) {
        if (mobile == null || !mobile.matches("^[6-9][0-9]{9}$")) {
            throw ApiException.badRequest("Enter a valid 10-digit Indian mobile number.");
        }
    }

    /** Mobile numbers are personal data; logs get a masked form. */
    private static String mask(String mobile) {
        return mobile == null || mobile.length() != 10
                ? "****"
                : mobile.substring(0, 2) + "******" + mobile.substring(8);
    }

    /** Lets the burst map be cleared between tests. */
    void clearBurstWindows() {
        burstWindows.clear();
    }
}
