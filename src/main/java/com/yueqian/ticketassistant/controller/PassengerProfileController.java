package com.yueqian.ticketassistant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yueqian.ticketassistant.dto.ApiResponse;
import com.yueqian.ticketassistant.dto.PassengerProfileRequest;
import com.yueqian.ticketassistant.dto.PassengerProfileResponse;
import com.yueqian.ticketassistant.entity.PassengerProfile;
import com.yueqian.ticketassistant.entity.TicketOrder;
import com.yueqian.ticketassistant.mapper.PassengerProfileMapper;
import com.yueqian.ticketassistant.mapper.TicketOrderMapper;
import com.yueqian.ticketassistant.service.SensitiveDataService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/passenger/profile")
public class PassengerProfileController {

    private final PassengerProfileMapper mapper;
    private final TicketOrderMapper orderMapper;
    private final SensitiveDataService sensitiveDataService;

    public PassengerProfileController(PassengerProfileMapper mapper, TicketOrderMapper orderMapper,
                                      SensitiveDataService sensitiveDataService) {
        this.mapper = mapper;
        this.orderMapper = orderMapper;
        this.sensitiveDataService = sensitiveDataService;
    }

    @GetMapping
    public ApiResponse<PassengerProfileResponse> get(HttpServletRequest request) {
        PassengerProfile profile = mapper.selectOne(new LambdaQueryWrapper<PassengerProfile>()
                .eq(PassengerProfile::getUserId, resolveUser(request)));
        if (profile == null) {
            return ApiResponse.ok(null);
        }
        String idCard = sensitiveDataService.decrypt(profile.getIdCard());
        return ApiResponse.ok(new PassengerProfileResponse(
                profile.getPassengerName(),
                idCard,
                sensitiveDataService.maskIdCard(idCard)
        ));
    }

    @GetMapping("/list")
    public ApiResponse<List<PassengerProfileResponse>> list(HttpServletRequest request) {
        String userId = resolveUser(request);
        List<PassengerProfileResponse> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        PassengerProfile profile = mapper.selectOne(new LambdaQueryWrapper<PassengerProfile>()
                .eq(PassengerProfile::getUserId, userId));
        if (profile != null) {
            addPassenger(result, seen, profile.getPassengerName(), sensitiveDataService.decrypt(profile.getIdCard()));
        }
        List<TicketOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId)
                .orderByDesc(TicketOrder::getCreateTime)
                .last("limit 20"));
        for (TicketOrder order : orders) {
            addPassenger(result, seen, order.getPassengerName(), sensitiveDataService.decrypt(order.getIdCard()));
        }
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse<PassengerProfileResponse> save(@Valid @RequestBody PassengerProfileRequest body,
                                                      HttpServletRequest request) {
        String userId = resolveUser(request);
        PassengerProfile profile = mapper.selectOne(new LambdaQueryWrapper<PassengerProfile>()
                .eq(PassengerProfile::getUserId, userId));
        LocalDateTime now = LocalDateTime.now();
        if (profile == null) {
            profile = new PassengerProfile();
            profile.setUserId(userId);
            profile.setCreateTime(now);
        }
        profile.setPassengerName(body.passengerName().trim());
        profile.setIdCard(sensitiveDataService.encrypt(body.idCard().trim()));
        profile.setUpdateTime(now);
        if (profile.getId() == null) {
            mapper.insert(profile);
        } else {
            mapper.updateById(profile);
        }
        return ApiResponse.ok(new PassengerProfileResponse(
                profile.getPassengerName(),
                body.idCard().trim(),
                sensitiveDataService.maskIdCard(body.idCard().trim())
        ));
    }

    private String resolveUser(HttpServletRequest request) {
        return String.valueOf(request.getAttribute("username"));
    }

    private void addPassenger(List<PassengerProfileResponse> result, Set<String> seen, String name, String idCard) {
        String key = name + "|" + idCard;
        if (seen.add(key)) {
            result.add(new PassengerProfileResponse(name, idCard, sensitiveDataService.maskIdCard(idCard)));
        }
    }
}
