package com.prideuniversal.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpRequestRepository extends JpaRepository<OtpRequest, UUID> {

    /** The most recent OTP issued to a number, used or not. */
    Optional<OtpRequest> findFirstByMobileOrderByCreatedDateDesc(String mobile);

    /** The newest still-valid OTP for a number. */
    @Query("""
            select o from OtpRequest o
            where o.mobile = :mobile
              and o.consumedAt is null
              and o.expiresAt > :now
            order by o.createdDate desc
            limit 1
            """)
    Optional<OtpRequest> findUsable(@Param("mobile") String mobile, @Param("now") Instant now);

    /** How many OTPs a number has been sent recently - the resend throttle. */
    @Query("select count(o) from OtpRequest o where o.mobile = :mobile and o.createdDate > :since")
    long countRecent(@Param("mobile") String mobile, @Param("since") Instant since);

    /** Housekeeping: expired rows have no value and should not accumulate. */
    @Modifying
    @Query("delete from OtpRequest o where o.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
