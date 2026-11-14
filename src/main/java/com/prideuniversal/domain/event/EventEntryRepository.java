package com.prideuniversal.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EventEntryRepository extends JpaRepository<EventEntry, UUID> {

    List<EventEntry> findAllByEventIdOrderByEntryDateAscCreatedDateAsc(UUID eventId);

    /** Rows of {@code [eventId, TxnType, total]}: every event's income and expense in one query. */
    @Query("select e.eventId, e.type, sum(e.amount) from EventEntry e group by e.eventId, e.type")
    List<Object[]> totalsByEventAndType();
}
