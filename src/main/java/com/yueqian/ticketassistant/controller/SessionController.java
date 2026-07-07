package com.yueqian.ticketassistant.controller;

import com.yueqian.ticketassistant.dto.ApiResponse;
import com.yueqian.ticketassistant.dto.RenameSessionRequest;
import com.yueqian.ticketassistant.dto.SessionRequest;
import com.yueqian.ticketassistant.entity.ChatSession;
import com.yueqian.ticketassistant.service.SessionService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/list")
    public ApiResponse<List<ChatSession>> list(HttpServletRequest request) {
        return ApiResponse.ok(sessionService.list(resolveUser(request)));
    }

    @PostMapping
    public ApiResponse<ChatSession> create(@Valid @RequestBody SessionRequest body, HttpServletRequest request) {
        return ApiResponse.ok(sessionService.create(resolveUser(request), body.title()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChatSession> rename(@PathVariable Long id, @Valid @RequestBody RenameSessionRequest body, HttpServletRequest request) {
        return ApiResponse.ok(sessionService.rename(resolveUser(request), id, body.title()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        sessionService.delete(resolveUser(request), id);
        return ApiResponse.ok(null);
    }

    private String resolveUser(HttpServletRequest request) {
        return String.valueOf(request.getAttribute("username"));
    }
}
