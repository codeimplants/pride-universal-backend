package com.prideuniversal.domain.bill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceBillRepository extends JpaRepository<MaintenanceBill, UUID> {

    /**
     * The flats already billed for a period.
     *
     * <p>Returns ids rather than entities: generation only needs to know which
     * flats to skip, and loading a few hundred bills to read one field each
     * would be waste. This is the query that makes "generate" idempotent
     * without relying on the unique-constraint exception path.
     */
    @Query("select b.flat.id from MaintenanceBill b where b.year = :year and b.month = :month")
    List<UUID> findBilledFlatIds(@Param("year") int year, @Param("month") int month);

    /**
     * Filtered billing register. Every filter is optional; a null means
     * "no restriction on this column".
     */
    @Query("""
            select b from MaintenanceBill b
            where (:year is null or b.year = :year)
              and (:month is null or b.month = :month)
              and (:wing is null or b.wing = :wing)
              and (:status is null or b.status = :status)
            order by b.year desc, b.month desc, b.wing asc, b.flatNumber asc
            """)
    List<MaintenanceBill> search(@Param("year") Integer year,
                                 @Param("month") Integer month,
                                 @Param("wing") String wing,
                                 @Param("status") BillStatus status);

    List<MaintenanceBill> findByFlat_IdOrderByYearDescMonthDesc(UUID flatId);

    /** Count and total (amount + late fee) of bills in the given states; a null flat means every flat. */
    @Query("""
            select new com.prideuniversal.domain.bill.BillTotals(count(b), coalesce(sum(b.amount + b.lateFee), 0))
            from MaintenanceBill b
            where b.status in :statuses
              and (:flatId is null or b.flatId = :flatId)
            """)
    BillTotals totals(@Param("statuses") java.util.Collection<BillStatus> statuses, @Param("flatId") UUID flatId);

    /** Money collected against one billing period. */
    @Query("""
            select new com.prideuniversal.domain.bill.BillTotals(count(b), coalesce(sum(b.amount + b.lateFee), 0))
            from MaintenanceBill b
            where b.status = com.prideuniversal.domain.bill.BillStatus.PAID
              and b.year = :year and b.month = :month
            """)
    BillTotals collected(@Param("year") int year, @Param("month") int month);

    /** Candidates for the nightly overdue sweep. */
    List<MaintenanceBill> findByStatusAndDueDateBefore(BillStatus status, LocalDate date);

    boolean existsByFlat_IdAndYearAndMonth(UUID flatId, int year, int month);

    Optional<MaintenanceBill> findByFlat_IdAndYearAndMonth(UUID flatId, int year, int month);
}
