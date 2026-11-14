package com.prideuniversal.domain.vehicle;

import com.prideuniversal.domain.vehicle.VehicleDtos.VehicleLookupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicles", description = "Number-plate lookup for the gate")
public class VehicleController {

    private final VehicleLookupService lookupService;

    public VehicleController(VehicleLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping("/lookup")
    @Operation(summary = "Is this plate registered with a flat, and who do we call? Security and committee only")
    public VehicleLookupResponse lookup(@RequestParam String plate) {
        return lookupService.lookup(plate);
    }
}
