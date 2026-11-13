package com.prideuniversal.domain.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query("""
            select t from LedgerEntry t
            where (:wing is null or t.wing = :wing)
              and (:year is null or t.year = :year)
              and (:month is null or t.month = :month)
            order by t.txnDate desc, t.createdDate desc
            limit 1000
            """)
    List<LedgerEntry> search(@Param("wing") String wing, @Param("year") Integer year, @Param("month") Integer month);

    /** Totals over the same filter, computed in the database rather than over a capped page. */
    @Query("""
            select coalesce(sum(t.amount), 0) from LedgerEntry t
            where t.type = :type
              and (:wing is null or t.wing = :wing)
              and (:year is null or t.year = :year)
              and (:month is null or t.month = :month)
            """)
    BigDecimal total(@Param("type") TxnType type, @Param("wing") String wing,
                     @Param("year") Integer year, @Param("month") Integer month);
}
