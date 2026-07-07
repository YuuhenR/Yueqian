package com.yueqian.ticketassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RouteSearchRequest(
        @NotBlank @Size(max = 40) String departureStation,
        @NotBlank @Size(max = 40) String arrivalStation,
        @NotBlank @Pattern(regexp = "20\\d{2}-\\d{2}-\\d{2}") String travelDate
) {
}
