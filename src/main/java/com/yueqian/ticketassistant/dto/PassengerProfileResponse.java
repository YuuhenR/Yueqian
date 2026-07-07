package com.yueqian.ticketassistant.dto;

public record PassengerProfileResponse(
        String passengerName,
        String idCard,
        String maskedIdCard
) {
}
