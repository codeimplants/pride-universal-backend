package com.prideuniversal.domain.event;

import com.prideuniversal.domain.event.EventDtos.EventRef;
import com.prideuniversal.domain.transaction.TxnType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every event's balances, worked out together, because an opening balance can be another event's
 * closing one: Kojagiri opens with what the Ganesh Festival closed with, which opened with last year's
 * balance. {@code closing = opening + income - expense}, memoised, so a chain is walked once.
 *
 * <p>A request-scoped snapshot: build it, read it, drop it. A society has a handful of events a year,
 * so one read of all events and one grouped sum of all entries is the cheap way to be right.
 */
final class EventBooks {

    private final Map<UUID, SocietyEvent> events = new LinkedHashMap<>();
    private final Map<UUID, BigDecimal> income = new HashMap<>();
    private final Map<UUID, BigDecimal> expense = new HashMap<>();
    private final Map<UUID, SocietyEvent> carriedTo = new HashMap<>();
    private final Map<UUID, BigDecimal> closing = new HashMap<>();

    EventBooks(List<SocietyEvent> all, List<Object[]> totals) {
        for (SocietyEvent e : all) {
            events.put(e.getId(), e);
            if (e.getCarriedFromId() != null) {
                carriedTo.put(e.getCarriedFromId(), e);
            }
        }
        for (Object[] row : totals) {
            UUID id = (UUID) row[0];
            BigDecimal sum = (BigDecimal) row[2];
            (row[1] == TxnType.INCOME ? income : expense).merge(id, sum, BigDecimal::add);
        }
    }

    List<SocietyEvent> events() {
        return List.copyOf(events.values());
    }

    BigDecimal income(UUID id) {
        return income.getOrDefault(id, BigDecimal.ZERO);
    }

    BigDecimal expense(UUID id) {
        return expense.getOrDefault(id, BigDecimal.ZERO);
    }

    BigDecimal opening(SocietyEvent e) {
        UUID from = e.getCarriedFromId();
        return from != null && events.containsKey(from) ? closing(from) : e.getOpeningBalance();
    }

    BigDecimal closing(UUID id) {
        return closing(id, new HashSet<>());
    }

    private BigDecimal closing(UUID id, Set<UUID> path) {
        BigDecimal known = closing.get(id);
        if (known != null) {
            return known;
        }
        SocietyEvent e = events.get(id);
        UUID from = e.getCarriedFromId();
        // The writes refuse cycles; the path check only guarantees this can never loop.
        BigDecimal open = from != null && events.containsKey(from) && path.add(id)
                ? closing(from, path)
                : e.getOpeningBalance();
        BigDecimal result = open.add(income(id)).subtract(expense(id));
        closing.put(id, result);
        return result;
    }

    EventRef carriedFrom(SocietyEvent e) {
        SocietyEvent from = e.getCarriedFromId() == null ? null : events.get(e.getCarriedFromId());
        return from == null ? null : new EventRef(from.getId(), from.getName());
    }

    EventRef carriedTo(SocietyEvent e) {
        SocietyEvent to = carriedTo.get(e.getId());
        return to == null ? null : new EventRef(to.getId(), to.getName());
    }
}
