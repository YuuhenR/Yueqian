package com.yueqian.ticketassistant.dto;

public record RouteIntentResponse(
        boolean needsBookingPage,
        String departureStation,
        String arrivalStation,
        String travelDate,
        String timePreference,
        Integer passengerCount,
        String seatType
) {
}
