package com.yueqian.ticketassistant.controller;

import com.yueqian.ticketassistant.dto.ApiResponse;
import com.yueqian.ticketassistant.dto.ChatRequest;
import com.yueqian.ticketassistant.service.AiAssistantService;
import com.yueqian.ticketassistant.service.AuditService;
import com.yueqian.ticketassistant.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AiAssistantService aiAssistantService;
    private final MessageService messageService;
    private final AuditService auditService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(AiAssistantService aiAssistantService, MessageService messageService, AuditService auditService) {
        this.aiAssistantService = aiAssistantService;
        this.messageService = messageService;
        this.auditService = auditService;
    }

    @PostMapping
    public ApiResponse<AiAssistantService.AiAnswer> chat(@Valid @RequestBody ChatRequest body, HttpServletRequest request) {
        String userId = resolveUser(body.userId(), request);
        messageService.save(body.sessionId(), userId, "user", body.message(), null);
        AiAssistantService.AiAnswer answer = aiAssistantService.answer(userId, body.sessionId(), body.message());
        messageService.save(body.sessionId(), userId, "assistant", answer.text(), answer.toolName());
        auditService.record(userId, "AI_CHAT", maskSensitive(body.message()), clientIp(request));
        return ApiResponse.ok(answer);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest body, HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        executor.submit(() -> {
            String userId = resolveUser(body.userId(), request);
            try {
                messageService.save(body.sessionId(), userId, "user", body.message(), null);
                AiAssistantService.AiAnswer answer = aiAssistantService.answer(userId, body.sessionId(), body.message());
                for (String part : answer.text().split("(?<=[。！？\\n])")) {
                    if (!part.isBlank()) {
                        emitter.send(SseEmitter.event().name("delta").data(part));
                        Thread.sleep(60);
                    }
                }
                messageService.save(body.sessionId(), userId, "assistant", answer.text(), answer.toolName());
                auditService.record(userId, "AI_CHAT", maskSensitive(body.message()), clientIp(request));
                emitter.send(SseEmitter.event().name("done").data(answer.toolName() == null ? "none" : answer.toolName()));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(ex.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private String resolveUser(String fallbackUserId, HttpServletRequest request) {
        Object username = request.getAttribute("username");
        if (username != null && !String.valueOf(username).isBlank()) {
            return String.valueOf(username);
        }
        if (fallbackUserId == null || fallbackUserId.isBlank()) {
            throw new IllegalArgumentException("缺少用户身份");
        }
        return fallbackUserId.trim();
    }

    private String maskSensitive(String text) {
        return text == null ? "" : text.replaceAll("\\d{6,}", "***");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
