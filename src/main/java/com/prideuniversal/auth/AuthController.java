package com.prideuniversal.auth;

import com.prideuniversal.auth.dto.AuthDtos.AuthResponse;
import com.prideuniversal.auth.dto.AuthDtos.RefreshRequest;
import com.prideuniversal.auth.dto.AuthDtos.SendOtpRequest;
import com.prideuniversal.auth.dto.AuthDtos.SendOtpResponse;
import com.prideuniversal.auth.dto.AuthDtos.UserResponse;
import com.prideuniversal.auth.dto.AuthDtos.VerifyOtpRequest;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.domain.user.User;
import com.prideuniversal.domain.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The four endpoints a client needs: request a code, exchange it for tokens,
 * refresh those tokens, and end the session.
 *
 * <p>Controllers here stay thin - validate, delegate, map to a DTO. All the
 * rules live in the services, which is what lets them be unit-tested without
 * standing up a web layer.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "OTP login and session management")
public class AuthController {

    private final OtpService otpService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    public AuthController(OtpService otpService,
                          TokenService tokenService,
                          UserRepository userRepository) {
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send a login OTP to a registered mobile number")
    public SendOtpResponse sendOtp(@Valid @RequestBody SendOtpRequest request,
                                   HttpServletRequest httpRequest) {
        String devOtp = otpService.send(request.mobile(), clientIp(httpRequest));
        return new SendOtpResponse(true, "OTP sent successfully", devOtp);
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify an OTP and receive an access/refresh token pair")
    public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request,
                                  HttpServletRequest httpRequest) {
        User user = otpService.verify(request.mobile(), request.otp());
        var tokens = tokenService.issue(user, httpRequest.getHeader("User-Agent"));

        return new AuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.expiresInSeconds(),
                UserResponse.from(user));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request,
                                HttpServletRequest httpRequest) {
        var result = tokenService.refresh(
                request.refreshToken(), httpRequest.getHeader("User-Agent"));

        return new AuthResponse(
                result.tokens().accessToken(),
                result.tokens().refreshToken(),
                result.tokens().expiresInSeconds(),
                UserResponse.from(result.user()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the presented refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        tokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "The currently authenticated user")
    public UserResponse me() {
        AuthPrincipal principal = AuthPrincipal.current();
        User user = userRepository.findWithFlatById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("User"));
        return UserResponse.from(user);
    }

    /**
     * Behind a reverse proxy the socket address is the proxy's, so prefer
     * X-Forwarded-For. Only trustworthy because Caddy sets it and the app is
     * not directly reachable - a client-supplied header is otherwise a lie.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
