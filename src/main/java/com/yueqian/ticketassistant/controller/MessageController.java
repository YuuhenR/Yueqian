package com.yueqian.ticketassistant.controller;

import com.yueqian.ticketassistant.dto.ApiResponse;
import com.yueqian.ticketassistant.entity.ChatMessage;
import com.yueqian.ticketassistant.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<List<ChatMessage>> list(@PathVariable Long sessionId,
                                               HttpServletRequest request) {
        return ApiResponse.ok(messageService.list(sessionId, String.valueOf(request.getAttribute("username"))));
    }
}
