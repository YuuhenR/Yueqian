package com.yueqian.ticketassistant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yueqian.ticketassistant.dto.AdminMetrics;
import com.yueqian.ticketassistant.dto.ApiResponse;
import com.yueqian.ticketassistant.entity.AppUser;
import com.yueqian.ticketassistant.entity.PendingTicketOrder;
import com.yueqian.ticketassistant.entity.TicketOrder;
import com.yueqian.ticketassistant.mapper.AppUserMapper;
import com.yueqian.ticketassistant.mapper.PendingTicketOrderMapper;
import com.yueqian.ticketassistant.mapper.TicketOrderMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AppUserMapper userMapper;
    private final TicketOrderMapper orderMapper;
    private final PendingTicketOrderMapper pendingMapper;

    public AdminController(AppUserMapper userMapper, TicketOrderMapper orderMapper, PendingTicketOrderMapper pendingMapper) {
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.pendingMapper = pendingMapper;
    }

    @GetMapping("/metrics")
    public ApiResponse<AdminMetrics> metrics(HttpServletRequest request) {
        if (!"ADMIN".equals(request.getAttribute("role"))) {
            throw new IllegalArgumentException("需要管理员权限");
        }
        long orderCount = orderMapper.selectCount(null);
        long active = orderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, "已出票"));
        long refunded = orderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, "已退票"));
        long pending = pendingMapper.selectCount(new LambdaQueryWrapper<PendingTicketOrder>().eq(PendingTicketOrder::getStatus, "待确认"));
        return ApiResponse.ok(new AdminMetrics(
                userMapper.selectCount(new LambdaQueryWrapper<AppUser>().eq(AppUser::getEnabled, 1)),
                orderCount,
                active,
                refunded,
                pending,
                chart(orderMapper.selectDailyOrderStats()),
                chart(orderMapper.selectRouteRankingStats())
        ));
    }

    private List<AdminMetrics.ChartPoint> chart(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> new AdminMetrics.ChartPoint(
                        String.valueOf(row.get("label")),
                        ((Number) row.get("value")).intValue()))
                .toList();
    }
}
