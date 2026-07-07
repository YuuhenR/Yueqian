package com.yueqian.ticketassistant.dto;

import jakarta.validation.constraints.NotBlank;

public record PassengerProfileRequest(
        @NotBlank String passengerName,
        @NotBlank String idCard
) {
}
