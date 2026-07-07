package com.yueqian.ticketassistant.dto;

import jakarta.validation.constraints.Size;

public record SessionRequest(
        @Size(max = 120) String title,
        @Size(max = 64) String userId
) {
}
