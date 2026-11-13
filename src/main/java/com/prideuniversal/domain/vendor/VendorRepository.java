package com.prideuniversal.domain.vendor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    /**
     * {@code q} must not be null: "" matches every name. A null parameter inside
     * {@code lower()} has no column to infer its type from, so Hibernate binds it
     * as bytea and Postgres rejects {@code lower(bytea)} (found by the test suite).
     */
    @Query("""
            select v from Vendor v
            where v.active = true
              and (:type is null or v.serviceType = :type)
              and lower(v.name) like lower(concat('%', :q, '%'))
            order by v.approved desc, v.name asc
            limit 300
            """)
    List<Vendor> search(@Param("type") ServiceType type, @Param("q") String q);
}
