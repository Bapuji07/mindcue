package com.secondmemory.auth;

import com.secondmemory.common.LimitExceededException;
import com.secondmemory.config.LimitsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerTest {
    private final UUID userId = UUID.randomUUID();
    private final Authentication auth = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AccountService accounts = mock(AccountService.class);
    private final AuthRateLimiter rateLimiter = new AuthRateLimiter(LimitsProperties.defaults());
    private final AccountController controller = new AccountController(users, encoder, accounts, rateLimiter);

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.2.3.4");
        return request;
    }

    private void userWithPassword(String password) {
        Instant now = Instant.now();
        when(users.findById(userId)).thenReturn(Optional.of(
                new AppUser(userId, "alice", encoder.encode(password), now, now)));
    }

    @Test
    void correctPasswordDeletesTheAccount() throws IOException {
        userWithPassword("correct horse");
        var response = controller.delete(new DeleteAccountRequest("correct horse"), auth, request());
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(accounts).deleteAccount(userId);
    }

    @Test
    void wrongPasswordIsForbiddenNotUnauthorized() throws IOException {
        userWithPassword("correct horse");
        var ex = assertThrows(ResponseStatusException.class,
                () -> controller.delete(new DeleteAccountRequest("wrong"), auth, request()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(accounts, never()).deleteAccount(any());
    }

    @Test
    void repeatedWrongPasswordsAreRateLimited() {
        userWithPassword("correct horse");
        for (int i = 0; i < 10; i++) {
            assertThrows(ResponseStatusException.class,
                    () -> controller.delete(new DeleteAccountRequest("wrong"), auth, request()));
        }
        assertThrows(LimitExceededException.class,
                () -> controller.delete(new DeleteAccountRequest("correct horse"), auth, request()));
    }
}
