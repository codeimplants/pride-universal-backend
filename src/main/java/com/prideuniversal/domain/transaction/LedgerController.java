package com.prideuniversal.domain.transaction;

import com.prideuniversal.domain.transaction.dto.LedgerDtos.LedgerEntryResponse;
import com.prideuniversal.domain.transaction.dto.LedgerDtos.LedgerPage;
import com.prideuniversal.domain.transaction.dto.LedgerDtos.LedgerRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** At {@code /api/transactions}, which SecurityConfig already fences to the finance roles. */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Accounts", description = "The society ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    @Operation(summary = "Entries and income/expense totals for a wing (A, B, common) and period")
    public LedgerPage page(@RequestParam(required = false) String wing,
                           @RequestParam(required = false) Integer year,
                           @RequestParam(required = false) Integer month) {
        return ledgerService.page(wing, year, month);
    }

    @GetMapping("/{id}")
    public LedgerEntryResponse get(@PathVariable UUID id) {
        return ledgerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a transaction")
    public LedgerEntryResponse record(@Valid @RequestBody LedgerRequest request) {
        return ledgerService.record(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Correct a transaction; requires the version last read")
    public LedgerEntryResponse correct(@PathVariable UUID id, @Valid @RequestBody LedgerRequest request) {
        return ledgerService.correct(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        ledgerService.delete(id);
    }
}
