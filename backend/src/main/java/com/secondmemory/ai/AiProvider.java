package com.secondmemory.ai;

import java.util.Set;

public interface AiProvider {
    String name();
    Set<AiCapability> capabilities();
}
