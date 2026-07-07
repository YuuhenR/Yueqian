package com.yueqian.ticketassistant.service;

public interface JwtService {
    String issue(String username, String role);
    com.yueqian.ticketassistant.dto.UserPrincipal verify(String token);
}
