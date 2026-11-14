package com.prideuniversal.domain.event;

import com.prideuniversal.domain.event.EventDtos.EntryRequest;
import com.prideuniversal.domain.event.EventDtos.EntryResponse;
import com.prideuniversal.domain.event.EventDtos.EventDetail;
import com.prideuniversal.domain.event.EventDtos.EventRequest;
import com.prideuniversal.domain.event.EventDtos.EventSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Event funds", description = "Festival and event accounts: collection, expenses and balance")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @Operation(summary = "Events with their balances; members see the published ones, the committee all")
    public List<EventSummary> list() {
        return eventService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "An event's accounts: balances and every entry")
    public EventDetail get(@PathVariable UUID id) {
        return eventService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetail create(@Valid @RequestBody EventRequest request) {
        return eventService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an event; requires the version last read")
    public EventDetail update(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
        return eventService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        eventService.delete(id);
    }

    @PostMapping("/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a collection or an expense")
    public EntryResponse addEntry(@PathVariable UUID id, @Valid @RequestBody EntryRequest request) {
        return eventService.addEntry(id, request);
    }

    @PutMapping("/{id}/entries/{entryId}")
    public EntryResponse updateEntry(@PathVariable UUID id, @PathVariable UUID entryId,
                                     @Valid @RequestBody EntryRequest request) {
        return eventService.updateEntry(id, entryId, request);
    }

    @DeleteMapping("/{id}/entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@PathVariable UUID id, @PathVariable UUID entryId) {
        eventService.deleteEntry(id, entryId);
    }
}
