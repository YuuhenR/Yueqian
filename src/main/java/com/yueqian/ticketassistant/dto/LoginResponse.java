package com.yueqian.ticketassistant.dto;

public record LoginResponse(String token, String username, String displayName, String role) {
}
