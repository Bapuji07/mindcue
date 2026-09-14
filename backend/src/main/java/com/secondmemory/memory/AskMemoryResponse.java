package com.secondmemory.memory;

import java.util.List;

public record AskMemoryResponse(String answer, List<AskMemorySource> sources) {}
