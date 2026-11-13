package com.prideuniversal.domain.meeting;

import com.prideuniversal.domain.notice.NoticeScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    @Query("""
            select m from Meeting m
            where m.scope in :scopes
              and (:status is null or m.status = :status)
            order by m.scheduledDate desc
            limit 200
            """)
    List<Meeting> search(@Param("scopes") Collection<NoticeScope> scopes, @Param("status") MeetingStatus status);

    /** Upcoming scheduled meetings for a dashboard, soonest first. */
    List<Meeting> findTop3ByScopeInAndStatusAndScheduledDateGreaterThanEqualOrderByScheduledDateAsc(
            Collection<NoticeScope> scopes, MeetingStatus status, LocalDate from);
}
