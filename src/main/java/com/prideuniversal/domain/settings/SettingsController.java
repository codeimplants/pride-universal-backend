package com.prideuniversal.domain.settings;

import com.prideuniversal.domain.settings.dto.SettingsDtos.WingSettingsRequest;
import com.prideuniversal.domain.settings.dto.SettingsDtos.WingSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings", description = "Per-wing billing, bank and society details")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @Operation(summary = "Settings for every wing")
    public List<WingSettingsResponse> all() {
        return settingsService.all();
    }

    @GetMapping("/{wing}")
    public WingSettingsResponse get(@PathVariable String wing) {
        return settingsService.get(wing);
    }

    @PutMapping("/{wing}")
    @Operation(summary = "Edit a wing's settings (admin, chairman); requires the version last read")
    public WingSettingsResponse update(@PathVariable String wing, @Valid @RequestBody WingSettingsRequest request) {
        return settingsService.update(wing, request);
    }
}
