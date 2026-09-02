package com.prideuniversal.domain.bill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Nightly sweep that moves unpaid bills past their due date to
 * {@code overdue} and applies the wing's late fee.
 *
 * <h2>Why this is a separate bean</h2>
 * It could have been a {@code @Scheduled} method on
 * {@link MaintenanceBillService}, but then the schedule — an operational
 * concern — would be tangled with the billing rules, and the service's
 * transaction proxy would be entered by a self-invocation rather than from
 * outside. Keeping the trigger in its own bean means the call crosses a proxy
 * boundary, so {@code @Transactional} on {@code applyOverdue} actually applies.
 *
 * <h2>Scheduling notes</h2>
 * <ul>
 *   <li>02:30 Asia/Kolkata: after midnight so "yesterday's due date" has
 *       genuinely passed in the society's timezone, and late enough to be off
 *       the daily backup window.</li>
 *   <li>The zone is stated explicitly. A cron expression without one silently
 *       follows the server's default, which is how a job scheduled for
 *       "overnight" ends up running at lunchtime after a VPS is rebuilt.</li>
 *   <li>{@code applyOverdue} only moves {@code PENDING -> OVERDUE}, so a
 *       missed night is harmless: the next run catches up, and a bill that is
 *       already overdue is never charged a second late fee.</li>
 * </ul>
 *
 * <h2>Before running more than one instance</h2>
 * Spring's scheduler is per-JVM, so every node would run this. Today the
 * deployment is a single VPS instance and that is fine. If it ever scales out,
 * this needs a lock (ShedLock, or an advisory lock in Postgres) — the
 * idempotent status transition limits the damage but does not prevent two
 * nodes doing the same work.
 */
@Component
public class OverdueBillJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueBillJob.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MaintenanceBillService billService;

    public OverdueBillJob(MaintenanceBillService billService) {
        this.billService = billService;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Kolkata")
    public void markOverdueBills() {
        LocalDate today = LocalDate.now(IST);
        try {
            int marked = billService.applyOverdue(today);
            log.info("Overdue sweep for {} finished: {} bills marked overdue", today, marked);
        } catch (Exception ex) {
            // Never let a scheduled task die silently. An uncaught exception
            // here is logged and swallowed so the next night still runs; the
            // sweep is idempotent, so retrying tomorrow loses nothing.
            log.error("Overdue sweep for {} failed", today, ex);
        }
    }
}
