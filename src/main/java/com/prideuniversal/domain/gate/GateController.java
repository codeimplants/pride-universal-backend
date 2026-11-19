package com.prideuniversal.domain.gate;

import com.prideuniversal.domain.gate.GateDtos.AttendanceResponse;
import com.prideuniversal.domain.gate.GateDtos.CheckInRequest;
import com.prideuniversal.domain.gate.GateDtos.CreatePassRequest;
import com.prideuniversal.domain.gate.GateDtos.HelpRequest;
import com.prideuniversal.domain.gate.GateDtos.HelpResponse;
import com.prideuniversal.domain.gate.GateDtos.PassResponse;
import com.prideuniversal.domain.gate.GateDtos.UsePassRequest;
import com.prideuniversal.domain.visitor.dto.VisitorDtos.VisitorResponse;
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
@RequestMapping("/api/gate")
@Tag(name = "Gate", description = "Gate passes and daily help")
public class GateController {

    private final GatePassService passes;
    private final DailyHelpService help;

    public GateController(GatePassService passes, DailyHelpService help) {
        this.passes = passes;
        this.help = help;
    }

    @GetMapping("/passes")
    @Operation(summary = "Gate passes: the caller's flat's, or everyone's latest for the gate")
    public List<PassResponse> passes() {
        return passes.list();
    }

    @PostMapping("/passes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Let a guest in ahead of time; returns the 6-digit code")
    public PassResponse createPass(@Valid @RequestBody CreatePassRequest request) {
        return passes.create(request);
    }

    @PostMapping("/passes/{id}/cancel")
    public PassResponse cancelPass(@PathVariable UUID id) {
        return passes.cancel(id);
    }

    @PostMapping("/passes/use")
    @Operation(summary = "Gate: admit a guest by their pass code")
    public VisitorResponse usePass(@Valid @RequestBody UsePassRequest request) {
        return passes.use(request.code());
    }

    @GetMapping("/help")
    @Operation(summary = "Daily help: those serving the caller's flat, or everyone for the gate")
    public List<HelpResponse> help() {
        return help.list();
    }

    @PostMapping("/help")
    @ResponseStatus(HttpStatus.CREATED)
    public HelpResponse addHelp(@Valid @RequestBody HelpRequest request) {
        return help.add(request);
    }

    @PutMapping("/help/{id}")
    public HelpResponse updateHelp(@PathVariable UUID id, @Valid @RequestBody HelpRequest request) {
        return help.update(id, request);
    }

    @DeleteMapping("/help/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeHelp(@PathVariable UUID id) {
        help.remove(id);
    }

    @PostMapping("/help/check-in")
    @Operation(summary = "Gate: mark daily help in by their code")
    public HelpResponse checkIn(@Valid @RequestBody CheckInRequest request) {
        return help.checkIn(request.code());
    }

    @PostMapping("/help/{id}/check-out")
    public HelpResponse checkOut(@PathVariable UUID id) {
        return help.checkOut(id);
    }

    @GetMapping("/help/{id}/attendance")
    public List<AttendanceResponse> attendance(@PathVariable UUID id) {
        return help.attendance(id);
    }
}
