package com.prideuniversal.domain.marketplace;

import com.prideuniversal.domain.marketplace.dto.ListingDtos.ListingRequest;
import com.prideuniversal.domain.marketplace.dto.ListingDtos.ListingResponse;
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
@RequestMapping("/api/marketplace")
@Tag(name = "Marketplace", description = "Residents buying, selling and giving away")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    @Operation(summary = "Active listings, newest first; mine=true for the caller's own")
    public List<ListingResponse> list(@RequestParam(required = false) ListingType type,
                                      @RequestParam(required = false) ListingCategory category,
                                      @RequestParam(defaultValue = "false") boolean mine) {
        return listingService.list(type, category, mine);
    }

    @GetMapping("/{id}")
    public ListingResponse get(@PathVariable UUID id) {
        return listingService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a listing")
    public ListingResponse create(@Valid @RequestBody ListingRequest request) {
        return listingService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a listing or mark it sold; requires the version last read")
    public ListingResponse update(@PathVariable UUID id, @Valid @RequestBody ListingRequest request) {
        return listingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a listing")
    public void remove(@PathVariable UUID id) {
        listingService.remove(id);
    }
}
