package com.yueqian.ticketassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookingRequest(
        Long sessionId,
        @NotBlank @Size(max = 20) String passengerName,
        @NotBlank @Size(max = 32) String idCard,
        @NotBlank @Size(max = 20) String trainNo,
        @NotBlank @Pattern(regexp = "20\\d{2}-\\d{2}-\\d{2}") String travelDate,
        @NotBlank @Size(max = 20) String seatType,
        @NotBlank @Size(max = 40) String departureStation,
        @NotBlank @Size(max = 40) String arrivalStation,
        @NotNull Integer ticketCount
) {
}
