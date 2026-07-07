package com.yueqian.ticketassistant.controller;

import com.yueqian.ticketassistant.dto.ApiResponse;
import com.yueqian.ticketassistant.dto.BookingRequest;
import com.yueqian.ticketassistant.entity.PendingTicketOrder;
import com.yueqian.ticketassistant.entity.TicketOrder;
import com.yueqian.ticketassistant.service.TicketOrderService;
import com.yueqian.ticketassistant.tool.ChinaRailwayClient;
import com.yueqian.ticketassistant.tool.WeatherTool;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final TicketOrderService orderService;
    private final WeatherTool weatherTool;
    private final ChinaRailwayClient railwayClient;

    public OrderController(TicketOrderService orderService, WeatherTool weatherTool, ChinaRailwayClient railwayClient) {
        this.orderService = orderService;
        this.weatherTool = weatherTool;
        this.railwayClient = railwayClient;
    }

    @GetMapping("/list")
    public ApiResponse<List<TicketOrder>> list(@RequestParam(required = false) String userId, HttpServletRequest request) {
        return ApiResponse.ok(orderService.list(resolveUser(userId, request)));
    }

    @GetMapping("/pending")
    public ApiResponse<List<PendingTicketOrder>> pending(@RequestParam(required = false) String userId, HttpServletRequest request) {
        return ApiResponse.ok(orderService.pendingList(resolveUser(userId, request)));
    }

    @PostMapping("/prepare")
    public ApiResponse<PendingTicketOrder> prepare(@Valid @RequestBody BookingRequest body, HttpServletRequest request) {
        String userId = resolveUser(null, request);
        return ApiResponse.ok(orderService.prepareOrder(userId, body.sessionId(), body.passengerName(), body.idCard(),
                body.trainNo(), body.travelDate(), body.seatType(), body.departureStation(), body.arrivalStation(), body.ticketCount()));
    }

    @PostMapping("/pending/{id}/confirm")
    public ApiResponse<TicketOrder> confirm(@PathVariable Long id,
                                            @RequestParam(required = false) String userId,
                                            HttpServletRequest request) {
        return ApiResponse.ok(orderService.confirmPending(resolveUser(userId, request), id));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<TicketOrder> refund(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(orderService.refundById(resolveUser(null, request), id));
    }

    @GetMapping("/{id}/weather")
    public ApiResponse<String> weather(@PathVariable Long id, HttpServletRequest request) {
        TicketOrder order = orderService.findById(resolveUser(null, request), id);
        String departure = weatherTool.query(cityName(order.getDepartureStation()));
        String arrival = weatherTool.query(cityName(order.getArrivalStation()));
        return ApiResponse.ok("\u51fa\u53d1\u5730\uff1a" + departure + "\n\u5230\u8fbe\u5730\uff1a" + arrival);
    }

    private String resolveUser(String userId, HttpServletRequest request) {
        return String.valueOf(request.getAttribute("username"));
    }

    private String cityName(String station) {
        String normalized = railwayClient.normalizeCity(station);
        return normalized == null || normalized.isBlank() ? "\u5317\u4eac" : normalized;
    }
}
