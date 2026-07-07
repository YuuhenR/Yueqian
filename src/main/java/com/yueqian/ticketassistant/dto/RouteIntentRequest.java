package com.yueqian.ticketassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RouteIntentRequest(
        @NotBlank @Size(max = 300) String userText
) {
}
