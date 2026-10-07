package com.secondmemory.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                          AuthRateLimiter rateLimiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        rateLimiter.checkLogin(ip);
        AppUser user = users.findByUsername(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
                .orElse(null);
        if (user == null) {
            rateLimiter.recordLoginFailure(ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        rateLimiter.clearLoginFailures(ip);
        String token = jwtService.issue(user.id(), user.username());
        return new LoginResponse(token, user.id(), user.username());
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimiter.checkAndRecordRegistration(http.getRemoteAddr());
        String username = request.username().trim();
        if (users.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        AppUser user;
        try {
            user = users.create(username, passwordEncoder.encode(request.password()));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        String token = jwtService.issue(user.id(), user.username());
        return new LoginResponse(token, user.id(), user.username());
    }
}
