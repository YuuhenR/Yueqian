package com.yueqian.ticketassistant.service;

import com.yueqian.ticketassistant.entity.TicketOrder;
import com.yueqian.ticketassistant.entity.PendingTicketOrder;

import java.util.List;
import java.util.Optional;

public interface TicketOrderService {
    PendingTicketOrder prepare(String userId, Long sessionId, String passengerName, String idCard, String trainNo,
                     String travelDate, String seatType, String departureStation, String arrivalStation, Integer ticketCount);
    TicketOrder confirmPending(String userId, Long pendingId);
    PendingTicketOrder prepareOrder(String userId, Long sessionId, String passengerName, String idCard, String trainNo,
                                    String travelDate, String seatType, String departureStation, String arrivalStation, Integer ticketCount);
    List<PendingTicketOrder> pendingList(String userId);
    TicketOrder book(String userId, Long sessionId, String passengerName, String idCard, String trainNo,
                     String travelDate, String seatType, String departureStation, String arrivalStation, Integer ticketCount);
    TicketOrder refund(String userId, String orderNo, String passengerName, String idCard);
    TicketOrder refundById(String userId, Long orderId);
    TicketOrder findById(String userId, Long orderId);
    Optional<TicketOrder> findByOrderNo(String orderNo);
    List<TicketOrder> list(String userId);
    long activeCount();
    long refundedCount();
    long activeCount(String userId);
    long refundedCount(String userId);
}
