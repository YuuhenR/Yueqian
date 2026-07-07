package com.yueqian.ticketassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RouteAssistRequest(
        @NotBlank @Size(max = 300) String userText,
        @NotBlank @Size(max = 40) String departureStation,
        @NotBlank @Size(max = 40) String arrivalStation,
        @NotBlank @Size(max = 20) String travelDate,
        @Size(max = 20) String timePreference,
        @NotEmpty @Size(max = 20) List<TrainOption> options
) {
}
