package com.prideuniversal.domain.dashboard;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.domain.bill.BillStatus;
import com.prideuniversal.domain.bill.BillTotals;
import com.prideuniversal.domain.bill.MaintenanceBillRepository;
import com.prideuniversal.domain.bill.dto.BillDtos.BillResponse;
import com.prideuniversal.domain.complaint.ComplaintRepository;
import com.prideuniversal.domain.complaint.ComplaintStatus;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.meeting.MeetingRepository;
import com.prideuniversal.domain.meeting.MeetingStatus;
import com.prideuniversal.domain.meeting.dto.MeetingDtos.MeetingResponse;
import com.prideuniversal.domain.notice.NoticeScope;
import com.prideuniversal.domain.notice.NoticeService;
import com.prideuniversal.domain.notice.WingScopes;
import com.prideuniversal.domain.notice.dto.NoticeDtos.NoticeResponse;
import com.prideuniversal.domain.visitor.VisitorRepository;
import com.prideuniversal.domain.visitor.VisitorStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the home screen shows, in one call. The Base44 dashboards made four or
 * five separate entity fetches and summed them in the browser — over every bill.
 *
 * <p>Sections are filled by what the caller is: committee members get the society
 * figures, anyone linked to a flat gets their flat's figures, and a committee
 * member who lives in the society gets both. Everyone gets the latest notices and
 * the upcoming meetings addressed to them.
 */
@Service
public class DashboardService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<BillStatus> DUE = EnumSet.of(BillStatus.PENDING, BillStatus.OVERDUE);
    private static final Set<ComplaintStatus> ACTIVE_COMPLAINTS = EnumSet.of(ComplaintStatus.OPEN, ComplaintStatus.IN_PROGRESS);

    private final FlatRepository flatRepository;
    private final MaintenanceBillRepository billRepository;
    private final ComplaintRepository complaintRepository;
    private final VisitorRepository visitorRepository;
    private final MeetingRepository meetingRepository;
    private final NoticeService noticeService;
    private final WingScopes wingScopes;

    public DashboardService(FlatRepository flatRepository, MaintenanceBillRepository billRepository,
                            ComplaintRepository complaintRepository, VisitorRepository visitorRepository,
                            MeetingRepository meetingRepository, NoticeService noticeService, WingScopes wingScopes) {
        this.flatRepository = flatRepository;
        this.billRepository = billRepository;
        this.complaintRepository = complaintRepository;
        this.visitorRepository = visitorRepository;
        this.meetingRepository = meetingRepository;
        this.noticeService = noticeService;
        this.wingScopes = wingScopes;
    }

    public record SocietyFigures(
            long activeFlats,
            long billsDue,
            BigDecimal amountDue,
            long billsOverdue,
            long collectedThisMonthCount,
            BigDecimal collectedThisMonth,
            long openComplaints,
            long visitorsWaiting
    ) {}

    public record FlatFigures(
            UUID flatId,
            String wing,
            String flatNumber,
            String ownerName,
            String tenantName,
            boolean isTenant,
            long billsDue,
            BigDecimal amountDue,
            List<BillResponse> recentBills,
            long activeComplaints,
            long visitorsWaiting
    ) {}

    public record DashboardResponse(
            SocietyFigures society,
            FlatFigures myFlat,
            List<NoticeResponse> latestNotices,
            List<MeetingResponse> upcomingMeetings
    ) {}

    @Transactional(readOnly = true)
    public DashboardResponse forCaller() {
        AuthPrincipal caller = AuthPrincipal.current();
        LocalDate today = LocalDate.now(IST);

        SocietyFigures society = caller.isManagement() ? societyFigures(today) : null;
        FlatFigures myFlat = caller.flatId() == null ? null
                : flatRepository.findById(caller.flatId()).map(this::flatFigures).orElse(null);

        List<NoticeResponse> notices = noticeService.list().stream().limit(3).toList();
        Set<NoticeScope> scopes = caller.isManagement() ? EnumSet.allOf(NoticeScope.class) : wingScopes.addressing(caller);
        List<MeetingResponse> meetings = meetingRepository
                .findTop3ByScopeInAndStatusAndScheduledDateGreaterThanEqualOrderByScheduledDateAsc(
                        scopes, MeetingStatus.SCHEDULED, today)
                .stream().map(m -> MeetingResponse.from(m, null)).toList();

        return new DashboardResponse(society, myFlat, notices, meetings);
    }

    private SocietyFigures societyFigures(LocalDate today) {
        BillTotals due = billRepository.totals(DUE, null);
        BillTotals overdue = billRepository.totals(EnumSet.of(BillStatus.OVERDUE), null);
        BillTotals collected = billRepository.collected(today.getYear(), today.getMonthValue());
        long openComplaints = complaintRepository.countByStatus(ComplaintStatus.OPEN)
                + complaintRepository.countByStatus(ComplaintStatus.IN_PROGRESS);
        return new SocietyFigures(
                flatRepository.countByActiveTrue(),
                due.count(), due.amount(),
                overdue.count(),
                collected.count(), collected.amount(),
                openComplaints,
                visitorRepository.countByStatus(VisitorStatus.PENDING));
    }

    private FlatFigures flatFigures(Flat flat) {
        BillTotals due = billRepository.totals(DUE, flat.getId());
        List<BillResponse> recent = billRepository.findByFlat_IdOrderByYearDescMonthDesc(flat.getId())
                .stream().limit(3).map(BillResponse::from).toList();
        return new FlatFigures(
                flat.getId(), flat.getWing(), flat.getFlatNumber(), flat.getOwnerName(), flat.getTenantName(),
                flat.isTenant(),
                due.count(), due.amount(), recent,
                complaintRepository.countByFlatIdAndStatusIn(flat.getId(), ACTIVE_COMPLAINTS),
                visitorRepository.countByFlatIdAndStatus(flat.getId(), VisitorStatus.PENDING));
    }
}
