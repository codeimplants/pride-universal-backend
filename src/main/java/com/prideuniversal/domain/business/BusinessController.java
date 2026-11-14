package com.prideuniversal.domain.business;

import com.prideuniversal.domain.business.BusinessDtos.BusinessRequest;
import com.prideuniversal.domain.business.BusinessDtos.BusinessResponse;
import com.prideuniversal.domain.business.BusinessDtos.ReviewRequest;
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
@RequestMapping("/api/businesses")
@Tag(name = "Member businesses", description = "Businesses run by members, reviewed by the committee")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping
    @Operation(summary = "Approved listings and your own; the committee sees all, filterable by status")
    public List<BusinessResponse> list(@RequestParam(required = false) BusinessCategory category,
                                       @RequestParam(required = false) BusinessStatus status) {
        return businessService.list(category, status);
    }

    @GetMapping("/{id}")
    public BusinessResponse get(@PathVariable UUID id) {
        return businessService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "List a business; a member's listing waits for the committee")
    public BusinessResponse create(@Valid @RequestBody BusinessRequest request) {
        return businessService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a listing; requires the version last read")
    public BusinessResponse update(@PathVariable UUID id, @Valid @RequestBody BusinessRequest request) {
        return businessService.update(id, request);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Approve, reject or hide a listing. Committee only")
    public BusinessResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return businessService.review(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        businessService.delete(id);
    }
}
