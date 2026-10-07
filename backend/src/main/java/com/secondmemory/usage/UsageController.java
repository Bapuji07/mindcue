package com.secondmemory.usage;

import com.secondmemory.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsageController {
    private final UsageService usage;

    public UsageController(UsageService usage) {
        this.usage = usage;
    }

    @GetMapping("/api/v1/usage")
    public UsageSummary get(Authentication auth) {
        return usage.summary(CurrentUser.id(auth));
    }
}
