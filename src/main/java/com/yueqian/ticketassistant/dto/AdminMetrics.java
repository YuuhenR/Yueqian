package com.yueqian.ticketassistant.dto;

import java.util.List;

public record AdminMetrics(
        long userCount,
        long orderCount,
        long activeOrderCount,
        long refundedOrderCount,
        long pendingOrderCount,
        List<ChartPoint> dailyOrders,
        List<ChartPoint> routeRanking
) {
    public record ChartPoint(String label, long value) {
    }
}
