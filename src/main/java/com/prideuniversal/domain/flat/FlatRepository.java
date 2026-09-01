package com.prideuniversal.domain.flat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlatRepository extends JpaRepository<Flat, UUID> {

    List<Flat> findAllByActiveTrueOrderByWingAscFlatNumberAsc();

    List<Flat> findAllByWingAndActiveTrueOrderByFlatNumberAsc(String wing);

    Optional<Flat> findByWingAndFlatNumber(String wing, String flatNumber);

    boolean existsByWingAndFlatNumber(String wing, String flatNumber);

    /**
     * Finds the flat a mobile number is entitled to, whether it is registered
     * as the owner's, the tenant's, or the owner's alternate number.
     *
     * <p>This is the gate for OTP login: a number that matches no flat cannot
     * receive an OTP at all.
     */
    @Query("""
            select f from Flat f
            where f.active = true
              and (f.ownerContact = :mobile
                or f.tenantContact = :mobile
                or f.ownerAlternateContact = :mobile)
            """)
    List<Flat> findByAnyContact(@Param("mobile") String mobile);

    long countByActiveTrue();

    long countByWingAndActiveTrue(String wing);
}
