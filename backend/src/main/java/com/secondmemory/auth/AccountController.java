package com.secondmemory.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@RestController
public class AccountController {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accounts;
    private final AuthRateLimiter rateLimiter;

    public AccountController(AppUserRepository users, PasswordEncoder passwordEncoder,
                             AccountService accounts, AuthRateLimiter rateLimiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accounts = accounts;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Permanently deletes the signed-in account once the password is re-entered. A wrong password is
     * 403, not 401, so the app does not mistake it for an expired session and sign the user out.
     */
    @PostMapping("/api/v1/account/delete")
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteAccountRequest request,
                                       Authentication auth, HttpServletRequest http) throws IOException {
        UUID userId = CurrentUser.id(auth);
        String ip = http.getRemoteAddr();
        rateLimiter.checkLogin(ip);
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            rateLimiter.recordLoginFailure(ip);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That password is incorrect");
        }
        accounts.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }
}
