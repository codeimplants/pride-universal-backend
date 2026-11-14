package com.prideuniversal.domain.event;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.common.Versions;
import com.prideuniversal.domain.event.EventDtos.EntryRequest;
import com.prideuniversal.domain.event.EventDtos.EntryResponse;
import com.prideuniversal.domain.event.EventDtos.EventDetail;
import com.prideuniversal.domain.event.EventDtos.EventRequest;
import com.prideuniversal.domain.event.EventDtos.EventSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Festival and event funds.
 *
 * <p>Who sees what: every member (residents and the committee) reads the published events, as they
 * used to read the accounts posted on the WhatsApp group. Drafts are the committee's until published,
 * and a member asking for one gets a 404, not a 403, so a draft's existence does not leak. The security
 * guards are staff, not members, and are not shown the society's festival money.
 *
 * <p>Who writes: the committee, all of it. Festivals are run by a festival committee, not only by
 * the treasurer, so {@code COMMITTEE_MEMBER} may keep an event's books too.
 */
@Service
public class EventService {

    private static final String CAN_MANAGE =
            "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER','COMMITTEE_MEMBER')";
    private static final String MEMBERS = "isAuthenticated() and !hasRole('SECURITY')";

    private final SocietyEventRepository eventRepository;
    private final EventEntryRepository entryRepository;

    public EventService(SocietyEventRepository eventRepository, EventEntryRepository entryRepository) {
        this.eventRepository = eventRepository;
        this.entryRepository = entryRepository;
    }

    // =====================================================================
    // Reads
    // =====================================================================

    @PreAuthorize(MEMBERS)
    @Transactional(readOnly = true)
    public List<EventSummary> list() {
        boolean committee = AuthPrincipal.current().isManagement();
        EventBooks books = books();
        return books.events().stream()
                .filter(e -> committee || e.isPublished())
                .map(e -> summary(e, books))
                .toList();
    }

    @PreAuthorize(MEMBERS)
    @Transactional(readOnly = true)
    public EventDetail get(UUID id) {
        SocietyEvent event = findVisible(id);
        return detail(event, books());
    }

    // =====================================================================
    // Events
    // =====================================================================

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public EventDetail create(EventRequest request) {
        SocietyEvent event = new SocietyEvent();
        event.setCreatedBy(AuthPrincipal.current().displayName());
        apply(request, event);
        eventRepository.saveAndFlush(event);
        return detail(event, books());
    }

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public EventDetail update(UUID id, EventRequest request) {
        SocietyEvent event = find(id);
        Versions.requireCurrent(event.getVersion(), request.version(), "event");
        apply(request, event);
        eventRepository.saveAndFlush(event);
        return detail(event, books());
    }

    /** Deletes the event and its entries, unless another event opens with its balance. */
    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public void delete(UUID id) {
        SocietyEvent event = find(id);
        eventRepository.findByCarriedFromId(id).ifPresent(next -> {
            throw ApiException.conflict(next.getName() + " opens with this event's balance. "
                    + "Change its opening balance before deleting this event.");
        });
        eventRepository.delete(event);   // entries go with it: ON DELETE CASCADE
    }

    // =====================================================================
    // Entries
    // =====================================================================

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public EntryResponse addEntry(UUID eventId, EntryRequest request) {
        find(eventId);
        AuthPrincipal caller = AuthPrincipal.current();
        EventEntry entry = new EventEntry();
        entry.setEventId(eventId);
        entry.setRecordedBy(caller.displayName());
        entry.setRecordedById(caller.userId());
        apply(request, entry);
        return EntryResponse.from(entryRepository.saveAndFlush(entry));
    }

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public EntryResponse updateEntry(UUID eventId, UUID entryId, EntryRequest request) {
        EventEntry entry = findEntry(eventId, entryId);
        Versions.requireCurrent(entry.getVersion(), request.version(), "entry");
        apply(request, entry);
        return EntryResponse.from(entryRepository.saveAndFlush(entry));
    }

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public void deleteEntry(UUID eventId, UUID entryId) {
        entryRepository.delete(findEntry(eventId, entryId));
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private EventBooks books() {
        return new EventBooks(eventRepository.findAllByOrderByStartDateDescCreatedDateDesc(),
                entryRepository.totalsByEventAndType());
    }

    private SocietyEvent find(UUID id) {
        return eventRepository.findById(id).orElseThrow(() -> ApiException.notFound("Event", id));
    }

    /** A draft is not found by members who may not see it. */
    private SocietyEvent findVisible(UUID id) {
        SocietyEvent event = find(id);
        if (!event.isPublished() && !AuthPrincipal.current().isManagement()) {
            throw ApiException.notFound("Event", id);
        }
        return event;
    }

    private EventEntry findEntry(UUID eventId, UUID entryId) {
        return entryRepository.findById(entryId)
                .filter(e -> e.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.notFound("Entry", entryId));
    }

    private void apply(EventRequest r, SocietyEvent event) {
        if (r.endDate() != null && r.endDate().isBefore(r.startDate())) {
            throw ApiException.badRequest("The end date cannot be before the start date.");
        }
        UUID from = r.carriedFromId();
        if (from != null) {
            checkCarry(event, from, r);
        }
        event.setName(r.name().trim());
        event.setStartDate(r.startDate());
        event.setEndDate(r.endDate());
        event.setDescription(Text.blankToNull(r.description()));
        event.setCarriedFromId(from);
        event.setOpeningBalance(from != null || r.openingBalance() == null ? BigDecimal.ZERO : r.openingBalance());
        event.setPublished(Boolean.TRUE.equals(r.isPublished()));
    }

    /** One earlier event, not already carried elsewhere, and no loop back to this one. */
    private void checkCarry(SocietyEvent event, UUID from, EventRequest r) {
        if (from.equals(event.getId())) {
            throw ApiException.badRequest("An event cannot open with its own balance.");
        }
        SocietyEvent source = eventRepository.findById(from)
                .orElseThrow(() -> ApiException.badRequest("The event to carry the balance from was not found."));
        if (source.getStartDate().isAfter(r.startDate())) {
            throw ApiException.badRequest("A balance can only be carried forward from an earlier event.");
        }
        eventRepository.findByCarriedFromId(from)
                .filter(other -> !other.getId().equals(event.getId()))
                .ifPresent(other -> {
                    throw ApiException.conflict(source.getName() + "'s balance is already carried forward to "
                            + other.getName() + ".");
                });
        UUID cursor = source.getCarriedFromId();
        for (int hops = 0; cursor != null && hops < 1_000; hops++) {
            if (cursor.equals(event.getId())) {
                throw ApiException.badRequest(source.getName() + " already opens with this event's balance.");
            }
            cursor = eventRepository.findById(cursor).map(SocietyEvent::getCarriedFromId).orElse(null);
        }
    }

    private static void apply(EntryRequest r, EventEntry entry) {
        entry.setType(r.type());
        entry.setHead(r.head().trim());
        entry.setDescription(Text.blankToNull(r.description()));
        entry.setAmount(r.amount());
        entry.setEntryDate(r.entryDate());
        entry.setReference(Text.blankToNull(r.reference()));
    }

    private static EventSummary summary(SocietyEvent e, EventBooks books) {
        UUID id = e.getId();
        return new EventSummary(id, e.getName(), e.getStartDate(), e.getEndDate(), e.isPublished(),
                books.opening(e), books.carriedFrom(e), books.carriedTo(e),
                books.income(id), books.expense(id), books.closing(id));
    }

    private EventDetail detail(SocietyEvent e, EventBooks books) {
        UUID id = e.getId();
        List<EntryResponse> entries = entryRepository.findAllByEventIdOrderByEntryDateAscCreatedDateAsc(id)
                .stream().map(EntryResponse::from).toList();
        return new EventDetail(id, e.getName(), e.getStartDate(), e.getEndDate(), e.getDescription(),
                e.isPublished(), books.opening(e), books.carriedFrom(e), books.carriedTo(e),
                books.income(id), books.expense(id), books.closing(id),
                e.getCreatedBy(), e.getUpdatedDate(), e.getVersion(), entries);
    }
}
