package com.secondmemory.session;

import jakarta.validation.constraints.Size;

/** Partial update of a conversation: a new title, and/or which transcript speaker is the user. */
public record UpdateSessionRequest(
        @Size(max = 255) String title,
        @Size(max = 100) String selfSpeaker
) {}
