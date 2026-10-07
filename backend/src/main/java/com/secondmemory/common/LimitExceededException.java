package com.secondmemory.common;

import java.time.Instant;

/** A usage or rate limit was reached; mapped to HTTP 429. {@code resetsAt} may be null. */
public class LimitExceededException extends RuntimeException {
    private final Instant resetsAt;

    public LimitExceededException(String message, Instant resetsAt) {
        super(message);
        this.resetsAt = resetsAt;
    }

    public Instant resetsAt() {
        return resetsAt;
    }
}
