package com.yueqian.ticketassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotNull Long sessionId,
        @NotBlank @Size(max = 1000) String message,
        @Size(max = 64) String userId
) {
}
