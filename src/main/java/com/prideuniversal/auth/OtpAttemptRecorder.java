package com.prideuniversal.auth;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records a failed OTP attempt in its own transaction.
 *
 * <p>This class exists for one specific reason, and it is worth understanding
 * properly because it is a bug class that bites people in production.
 *
 * <p>When {@code OtpService.verify()} finds a wrong code it increments the
 * attempt counter and then throws. If that increment ran in the caller's
 * transaction, the throw would roll it back - and the counter would never go
 * up, giving an attacker unlimited guesses against a 6-digit code. The
 * lockout would silently not exist.
 *
 * <p>{@code REQUIRES_NEW} suspends the caller's transaction and runs this in a
 * fresh one that commits independently, so the increment survives the rollback.
 *
 * <p>It also has to be a <em>separate bean</em>. Spring's {@code @Transactional}
 * works through a proxy that wraps the bean; calling a method on {@code this}
 * goes straight to the underlying object and never touches the proxy, so the
 * annotation is silently ignored. Self-invocation is the single most common
 * reason {@code @Transactional} "doesn't work".
 */
@Component
public class OtpAttemptRecorder {

    private final OtpRequestRepository repository;

    public OtpAttemptRecorder(OtpRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(UUID otpRequestId) {
        repository.findById(otpRequestId).ifPresent(otp -> {
            otp.setAttempts(otp.getAttempts() + 1);
            repository.save(otp);
        });
    }
}
