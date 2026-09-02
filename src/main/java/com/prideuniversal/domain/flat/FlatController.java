package com.prideuniversal.domain.flat;

import com.prideuniversal.domain.flat.dto.FlatDtos.FlatDetail;
import com.prideuniversal.domain.flat.dto.FlatDtos.FlatRequest;
import com.prideuniversal.domain.flat.dto.FlatDtos.FlatSummary;
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

/**
 * HTTP surface for flats.
 *
 * <p>Note how little is here. The controller maps HTTP to method calls and
 * nothing else: no permission checks, no validation logic, no entity access.
 * That is the point — the rules live in {@link FlatService}, where they apply
 * to every caller rather than only to callers who arrived over HTTP.
 *
 * <p>Status codes are chosen rather than defaulted: 201 for a create with the
 * new resource returned, 204 for a deactivate that has nothing to say.
 */
@RestController
@RequestMapping("/api/flats")
@Tag(name = "Flats", description = "The society's flat register")
public class FlatController {

    private final FlatService flatService;

    public FlatController(FlatService flatService) {
        this.flatService = flatService;
    }

    @GetMapping
    @Operation(summary = "List active flats (directory view, no contact details)")
    public List<FlatSummary> list(@RequestParam(required = false) String wing) {
        return flatService.directory(wing);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full details of one flat (committee, or your own flat)")
    public FlatDetail get(@PathVariable UUID id) {
        return flatService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a flat to the register")
    public FlatDetail create(@Valid @RequestBody FlatRequest request) {
        return flatService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a flat; requires the version last read")
    public FlatDetail update(@PathVariable UUID id, @Valid @RequestBody FlatRequest request) {
        return flatService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a flat (soft delete; history is preserved)")
    public void deactivate(@PathVariable UUID id) {
        flatService.deactivate(id);
    }
}
