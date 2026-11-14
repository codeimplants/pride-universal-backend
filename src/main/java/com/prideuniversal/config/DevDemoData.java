package com.prideuniversal.config;

import com.prideuniversal.domain.event.EventEntry;
import com.prideuniversal.domain.event.EventEntryRepository;
import com.prideuniversal.domain.event.SocietyEvent;
import com.prideuniversal.domain.event.SocietyEventRepository;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.transaction.TxnType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Demo data for the newer screens, on dev only: the Ganesh Festival 2026 and Kojagiri accounts exactly
 * as the committee posted them (so the event screen can be checked against the poster), and vehicle
 * registrations on the seeded flats for the gate's plate lookup.
 *
 * <p>Each part is guarded on its own, so a dev database seeded before these screens existed gets them
 * on the next start, and a database where someone has entered real events or vehicles is left alone.
 * Called by {@link DevDataSeeder}, after its flats exist.
 */
@Component
@Profile("dev")
class DevDemoData {

    private static final Logger log = LoggerFactory.getLogger(DevDemoData.class);

    private final SocietyEventRepository eventRepository;
    private final EventEntryRepository entryRepository;
    private final FlatRepository flatRepository;

    DevDemoData(SocietyEventRepository eventRepository, EventEntryRepository entryRepository,
                FlatRepository flatRepository) {
        this.eventRepository = eventRepository;
        this.entryRepository = entryRepository;
        this.flatRepository = flatRepository;
    }

    void seed() {
        seedEvents();
        seedVehicles();
    }

    private void seedEvents() {
        if (eventRepository.count() > 0) {
            return;
        }
        SocietyEvent ganesh = event("Ganesh Festival 2026", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 24),
                null, "7680");
        LocalDate g = ganesh.getStartDate();
        entry(ganesh, TxnType.INCOME, "vargani", null, "176642", g);
        entry(ganesh, TxnType.EXPENSE, "ganesh_sthapana", null, "10905", g);
        entry(ganesh, TxnType.EXPENSE, "mandap_decoration", null, "8802", g);
        entry(ganesh, TxnType.EXPENSE, "mahaprasad", null, "57491", g.plusDays(5));
        entry(ganesh, TxnType.EXPENSE, "bhajan_sandhya", null, "7558", g.plusDays(3));
        entry(ganesh, TxnType.EXPENSE, "games_culture", null, "12712", g.plusDays(6));
        entry(ganesh, TxnType.EXPENSE, "krishna_jayanti", null, "820", g.minusDays(10));
        entry(ganesh, TxnType.EXPENSE, "visarjan", null, "8920", g.plusDays(10));

        SocietyEvent kojagiri = event("Kojagiri 2026", LocalDate.of(2026, 10, 25), null, ganesh.getId(), "0");
        LocalDate k = kojagiri.getStartDate();
        entry(kojagiri, TxnType.EXPENSE, "Milk (80 litres)", null, "6240", k);
        entry(kojagiri, TxnType.EXPENSE, "Pav bhaji", null, "28000", k);
        entry(kojagiri, TxnType.EXPENSE, "Milk masala, sugar & dry fruits", null, "4474", k);
        entry(kojagiri, TxnType.EXPENSE, "Extra pav (ladi)", null, "1980", k);
        entry(kojagiri, TxnType.EXPENSE, "Plates & paper glasses", null, "385", k);
        entry(kojagiri, TxnType.EXPENSE, "Iron kadai, gas & other rent", null, "1322", k);
        log.info("Seeded demo event funds: Ganesh Festival 2026 (closing 77,114) and Kojagiri 2026 (closing 34,713)");
    }

    /** Plates on the seeded flats, in the free-text shapes people really type. */
    private void seedVehicles() {
        boolean anyRegistered = flatRepository.findAllByActiveTrueOrderByWingAscFlatNumberAsc().stream()
                .anyMatch(f -> f.getFourWheelerNumbers() != null || f.getTwoWheelerNumbers() != null);
        if (anyRegistered) {
            return;
        }
        vehicles("A", "101", "MH 12 AB 1234", "MH12XY4321");
        vehicles("A", "102", "MH14CD5678", null);
        vehicles("B", "101", "MH-12-EF-9012", "MH 12 GH 3456, MH12JK7890");
        vehicles("B", "202", null, "MH12LM2468");
        log.info("Seeded demo vehicle registrations (try MH12AB1234, or just 5678)");
    }

    private void vehicles(String wing, String number, String cars, String bikes) {
        flatRepository.findByWingAndFlatNumber(wing, number).ifPresent(f -> {
            f.setFourWheeler(cars != null);
            f.setFourWheelerNumbers(cars);
            f.setFourWheelerCount(cars == null ? 0 : cars.split(",").length);
            f.setTwoWheeler(bikes != null);
            f.setTwoWheelerNumbers(bikes);
            f.setTwoWheelerCount(bikes == null ? 0 : bikes.split(",").length);
            flatRepository.save(f);
        });
    }

    private SocietyEvent event(String name, LocalDate start, LocalDate end, UUID carriedFrom, String opening) {
        SocietyEvent e = new SocietyEvent();
        e.setName(name);
        e.setStartDate(start);
        e.setEndDate(end);
        e.setCarriedFromId(carriedFrom);
        e.setOpeningBalance(new BigDecimal(opening));
        e.setPublished(true);
        e.setCreatedBy("Festival Committee");
        return eventRepository.saveAndFlush(e);
    }

    private void entry(SocietyEvent event, TxnType type, String head, String description, String amount, LocalDate on) {
        EventEntry e = new EventEntry();
        e.setEventId(event.getId());
        e.setType(type);
        e.setHead(head);
        e.setDescription(description);
        e.setAmount(new BigDecimal(amount));
        e.setEntryDate(on);
        e.setRecordedBy("Festival Committee");
        entryRepository.save(e);
    }
}
