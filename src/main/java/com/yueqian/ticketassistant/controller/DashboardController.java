package com.yueqian.ticketassistant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yueqian.ticketassistant.dto.ApiResponse;
import com.yueqian.ticketassistant.entity.ChatSession;
import com.yueqian.ticketassistant.mapper.ChatSessionMapper;
import com.yueqian.ticketassistant.service.MessageService;
import com.yueqian.ticketassistant.service.TicketOrderService;
import com.yueqian.ticketassistant.tool.DestinationTool;
import com.yueqian.ticketassistant.vo.DashboardVo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ChatSessionMapper sessionMapper;
    private final MessageService messageService;
    private final TicketOrderService orderService;
    private final DestinationTool destinationTool;

    public DashboardController(ChatSessionMapper sessionMapper, MessageService messageService,
                               TicketOrderService orderService, DestinationTool destinationTool) {
        this.sessionMapper = sessionMapper;
        this.messageService = messageService;
        this.orderService = orderService;
        this.destinationTool = destinationTool;
    }

    @GetMapping
    public ApiResponse<DashboardVo> dashboard(HttpServletRequest request) {
        String userId = String.valueOf(request.getAttribute("username"));
        return ApiResponse.ok(new DashboardVo(
                sessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getUserId, userId)),
                messageService.count(userId),
                orderService.activeCount(userId),
                orderService.refundedCount(userId),
                List.of(),
                List.of()
        ));
    }

    @GetMapping("/destinations")
    public ApiResponse<List<DestinationTool.Destination>> destinations() {
        return ApiResponse.ok(destinationTool.hot());
    }
}
