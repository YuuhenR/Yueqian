package com.yueqian.ticketassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 80) String displayName,
        @NotBlank @Size(min = 6, max = 128) String password
) {
}
