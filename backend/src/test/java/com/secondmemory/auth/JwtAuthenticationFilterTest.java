package com.secondmemory.auth;

import com.secondmemory.config.AuthProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    private final JwtService jwt = new JwtService(new AuthProperties(
            new AuthProperties.Jwt("test-secret-value-that-is-long-enough-for-hmac-sha256", 30),
            new AuthProperties.Bootstrap(null, null)));
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void run(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void validTokenOfExistingUserAuthenticates() throws Exception {
        UUID userId = UUID.randomUUID();
        when(users.existsById(userId)).thenReturn(true);
        run(jwt.issue(userId, "alice"));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(userId.toString());
    }

    @Test
    void tokenOfDeletedUserIsIgnored() throws Exception {
        UUID userId = UUID.randomUUID();
        when(users.existsById(userId)).thenReturn(false);
        run(jwt.issue(userId, "alice"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
