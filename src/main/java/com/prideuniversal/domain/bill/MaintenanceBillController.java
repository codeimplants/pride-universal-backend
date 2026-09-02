package com.prideuniversal.domain.bill;

import com.prideuniversal.domain.bill.dto.BillDtos.BillResponse;
import com.prideuniversal.domain.bill.dto.BillDtos.GenerateBillsRequest;
import com.prideuniversal.domain.bill.dto.BillDtos.GenerationResult;
import com.prideuniversal.domain.bill.dto.BillDtos.MarkPaidRequest;
import com.prideuniversal.domain.bill.dto.BillDtos.OverdueSweepResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HTTP surface for maintenance bills.
 *
 * <p>As with flats, every permission decision is in the service. Note the two
 * list endpoints: {@code /api/bills} is the committee's register of everyone's
 * bills, {@code /api/bills/mine} is scoped to the caller's own flat by the
 * token. They are separate routes rather than one route that behaves
 * differently by role, because an endpoint whose meaning changes with the
 * caller is an endpoint whose access rule is easy to get wrong.
 */
@RestController
@RequestMapping("/api/bills")
@Tag(name = "Maintenance bills", description = "Billing, payment and arrears")
public class MaintenanceBillController {

    private final MaintenanceBillService billService;

    public MaintenanceBillController(MaintenanceBillService billService) {
        this.billService = billService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Raise bills for a period; safe to run twice")
    public GenerationResult generate(@Valid @RequestBody GenerateBillsRequest request) {
        return billService.generate(request.year(), request.month());
    }

    @GetMapping
    @Operation(summary = "The billing register, filtered (committee only)")
    public List<BillResponse> list(@RequestParam(required = false) Integer year,
                                   @RequestParam(required = false) Integer month,
                                   @RequestParam(required = false) String wing,
                                   @RequestParam(required = false) BillStatus status) {
        return billService.search(year, month, wing, status);
    }

    @GetMapping("/mine")
    @Operation(summary = "The caller's own bills")
    public List<BillResponse> mine() {
        return billService.myBills();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One bill (committee, or your own flat's)")
    public BillResponse get(@PathVariable UUID id) {
        return billService.detail(id);
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Record a payment; requires the version last read")
    public BillResponse markPaid(@PathVariable UUID id,
                                 @Valid @RequestBody MarkPaidRequest request) {
        return billService.markPaid(id, request);
    }

    @PostMapping("/run-overdue")
    @Operation(summary = "Run the overdue sweep now instead of waiting for 02:30")
    public OverdueSweepResult runOverdueSweep() {
        return billService.runOverdueSweep();
    }
}
