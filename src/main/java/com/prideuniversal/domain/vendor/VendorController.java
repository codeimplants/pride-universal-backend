package com.prideuniversal.domain.vendor;

import com.prideuniversal.domain.vendor.dto.VendorDtos.VendorRequest;
import com.prideuniversal.domain.vendor.dto.VendorDtos.VendorResponse;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendors")
@Tag(name = "Vendors", description = "The vendor directory")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @GetMapping
    @Operation(summary = "Active vendors, verified first; filter by service type or name")
    public List<VendorResponse> list(@RequestParam(required = false) ServiceType serviceType,
                                     @RequestParam(required = false) String q) {
        return vendorService.list(serviceType, q);
    }

    @GetMapping("/{id}")
    public VendorResponse get(@PathVariable UUID id) {
        return vendorService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recommend a vendor")
    public VendorResponse create(@Valid @RequestBody VendorRequest request) {
        return vendorService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a vendor (committee or whoever added it)")
    public VendorResponse update(@PathVariable UUID id, @Valid @RequestBody VendorRequest request) {
        return vendorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a vendor from the directory")
    public void deactivate(@PathVariable UUID id) {
        vendorService.deactivate(id);
    }
}
