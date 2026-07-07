package com.yueqian.ticketassistant.vo;

import java.util.List;

public record DashboardVo(
        long sessionCount,
        long messageCount,
        long activeOrderCount,
        long refundedOrderCount,
        List<String> securityHighlights,
        List<String> aiCapabilities
) {
}
