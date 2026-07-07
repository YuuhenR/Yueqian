package com.yueqian.ticketassistant.dto;

import java.math.BigDecimal;

public record TrainOption(
        String trainNo,
        String departureStation,
        String arrivalStation,
        String departureTime,
        String arrivalTime,
        String duration,
        String seatType,
        BigDecimal price,
        String reason
) {
}
