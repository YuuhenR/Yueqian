package com.yueqian.ticketassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yueqian.ticketassistant.entity.PendingTicketOrder;
import com.yueqian.ticketassistant.entity.TicketOrder;
import com.yueqian.ticketassistant.mapper.PendingTicketOrderMapper;
import com.yueqian.ticketassistant.mapper.TicketOrderMapper;
import com.yueqian.ticketassistant.service.SensitiveDataService;
import com.yueqian.ticketassistant.service.TicketOrderService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TicketOrderServiceImpl implements TicketOrderService {

    private static final String STATUS_PENDING = "待确认";
    private static final String STATUS_CONFIRMED = "已确认";
    private static final String STATUS_ISSUED = "已出票";
    private static final String STATUS_REFUNDED = "已退票";

    private final TicketOrderMapper orderMapper;
    private final PendingTicketOrderMapper pendingMapper;
    private final SensitiveDataService sensitiveDataService;

    public TicketOrderServiceImpl(TicketOrderMapper orderMapper, PendingTicketOrderMapper pendingMapper,
                                  SensitiveDataService sensitiveDataService) {
        this.orderMapper = orderMapper;
        this.pendingMapper = pendingMapper;
        this.sensitiveDataService = sensitiveDataService;
    }

    @Override
    public PendingTicketOrder prepare(String userId, Long sessionId, String passengerName, String idCard, String trainNo,
                                      String travelDate, String seatType, String departureStation, String arrivalStation, Integer ticketCount) {
        int count = normalizeCount(ticketCount);
        PendingTicketOrder pending = new PendingTicketOrder();
        pending.setUserId(normalizeUser(userId));
        pending.setSessionId(sessionId);
        pending.setPassengerName(requireText(passengerName, "乘车人姓名"));
        pending.setIdCard(sensitiveDataService.encrypt(requireText(idCard, "身份证号")));
        pending.setTrainNo(requireText(trainNo, "车次").toUpperCase());
        pending.setDepartureStation(cleanStation(departureStation, "北京"));
        pending.setArrivalStation(cleanStation(arrivalStation, "上海"));
        pending.setTravelDate(LocalDate.parse(requireText(travelDate, "乘车日期")));
        pending.setSeatType(requireText(seatType, "座位类型"));
        pending.setTicketCount(count);
        pending.setEstimatedPrice(basePrice(seatType).multiply(BigDecimal.valueOf(count)));
        pending.setStatus(STATUS_PENDING);
        pending.setCreateTime(LocalDateTime.now());
        pendingMapper.insert(pending);
        return pending;
    }

    @Override
    public PendingTicketOrder prepareOrder(String userId, Long sessionId, String passengerName, String idCard, String trainNo,
                                           String travelDate, String seatType, String departureStation, String arrivalStation, Integer ticketCount) {
        return prepare(userId, sessionId, passengerName, idCard, trainNo, travelDate, seatType, departureStation, arrivalStation, ticketCount);
    }

    @Override
    public TicketOrder confirmPending(String userId, Long pendingId) {
        PendingTicketOrder pending = pendingMapper.selectOne(new LambdaQueryWrapper<PendingTicketOrder>()
                .eq(PendingTicketOrder::getId, pendingId)
                .eq(PendingTicketOrder::getUserId, normalizeUser(userId))
                .eq(PendingTicketOrder::getStatus, STATUS_PENDING));
        if (pending == null) {
            throw new IllegalArgumentException("待确认订单不存在或已处理");
        }
        TicketOrder order = book(userId, pending.getSessionId(), pending.getPassengerName(), pending.getIdCard(),
                pending.getTrainNo(), pending.getTravelDate().toString(), pending.getSeatType(),
                pending.getDepartureStation(), pending.getArrivalStation(), pending.getTicketCount());
        pending.setStatus(STATUS_CONFIRMED);
        pendingMapper.updateById(pending);
        return order;
    }

    @Override
    public List<PendingTicketOrder> pendingList(String userId) {
        return pendingMapper.selectList(new LambdaQueryWrapper<PendingTicketOrder>()
                .eq(PendingTicketOrder::getUserId, normalizeUser(userId))
                .eq(PendingTicketOrder::getStatus, STATUS_PENDING)
                .orderByDesc(PendingTicketOrder::getCreateTime));
    }

    @Override
    public TicketOrder book(String userId, Long sessionId, String passengerName, String idCard, String trainNo,
                            String travelDate, String seatType, String departureStation, String arrivalStation, Integer ticketCount) {
        int count = normalizeCount(ticketCount);
        LocalDate date = LocalDate.parse(requireText(travelDate, "乘车日期"));
        if (LocalDateTime.now().isAfter(LocalDateTime.of(date, LocalTime.of(7, 30)).minusMinutes(30))) {
            throw new IllegalArgumentException("订单已超过出票时间");
        }
        TicketOrder order = new TicketOrder();
        order.setOrderNo("RA" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999));
        order.setUserId(normalizeUser(userId));
        order.setSessionId(sessionId);
        order.setPassengerName(requireText(passengerName, "乘车人姓名"));
        order.setIdCard(idCard != null && idCard.startsWith("enc:")
                ? idCard
                : sensitiveDataService.encrypt(requireText(idCard, "身份证号")));
        order.setTrainNo(requireText(trainNo, "车次").toUpperCase());
        order.setDepartureStation(cleanStation(departureStation, "北京"));
        order.setArrivalStation(cleanStation(arrivalStation, "上海"));
        order.setTravelDate(date);
        order.setSeatType(requireText(seatType, "座位类型"));
        order.setSeatNo(randomSeat(seatType));
        order.setTicketCount(count);
        order.setPrice(basePrice(seatType).multiply(BigDecimal.valueOf(count)));
        order.setStatus(STATUS_ISSUED);
        order.setBookingTime(LocalDateTime.now());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(0);
        orderMapper.insert(order);
        return order;
    }

    @Override
    public TicketOrder refund(String userId, String orderNo, String passengerName, String idCard) {
        TicketOrder order = null;
        if (StringUtils.hasText(orderNo)) {
            order = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                    .eq(TicketOrder::getOrderNo, orderNo.trim())
                    .eq(TicketOrder::getUserId, normalizeUser(userId)));
        }
        if (order == null && StringUtils.hasText(passengerName) && StringUtils.hasText(idCard)) {
            List<TicketOrder> candidates = orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                    .eq(TicketOrder::getPassengerName, passengerName.trim())
                    .eq(TicketOrder::getUserId, normalizeUser(userId))
                    .orderByDesc(TicketOrder::getCreateTime));
            order = candidates.stream()
                    .filter(item -> sensitiveDataService.matches(idCard, item.getIdCard()))
                    .findFirst()
                    .orElse(null);
        }
        if (order == null) {
            throw new IllegalArgumentException("未找到可退票订单");
        }
        if (STATUS_REFUNDED.equals(order.getStatus())) {
            throw new IllegalArgumentException("该订单已经退票");
        }
        LocalDateTime departTime = LocalDateTime.of(order.getTravelDate(), LocalTime.of(7, 30));
        long hours = ChronoUnit.HOURS.between(LocalDateTime.now(), departTime);
        if (hours < 0) {
            throw new IllegalArgumentException("开车后不可退票");
        }
        BigDecimal rate = hours >= 24 ? new BigDecimal("0.10") : new BigDecimal("0.20");
        order.setRefundFee(order.getPrice().multiply(rate).setScale(2, RoundingMode.HALF_UP));
        order.setRefundTime(LocalDateTime.now());
        order.setStatus(STATUS_REFUNDED);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    @Override
    public TicketOrder refundById(String userId, Long orderId) {
        TicketOrder order = findById(userId, orderId);
        return refund(userId, order.getOrderNo(), null, null);
    }

    @Override
    public TicketOrder findById(String userId, Long orderId) {
        TicketOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getId, orderId)
                .eq(TicketOrder::getUserId, normalizeUser(userId)));
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    @Override
    public Optional<TicketOrder> findByOrderNo(String orderNo) {
        return Optional.ofNullable(orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getOrderNo, orderNo)));
    }

    @Override
    public List<TicketOrder> list(String userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, normalizeUser(userId))
                .orderByDesc(TicketOrder::getCreateTime));
    }

    @Override
    public long activeCount() {
        return orderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, STATUS_ISSUED));
    }

    @Override
    public long refundedCount() {
        return orderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, STATUS_REFUNDED));
    }

    @Override
    public long activeCount(String userId) {
        return orderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, normalizeUser(userId))
                .eq(TicketOrder::getStatus, STATUS_ISSUED));
    }

    @Override
    public long refundedCount(String userId) {
        return orderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, normalizeUser(userId))
                .eq(TicketOrder::getStatus, STATUS_REFUNDED));
    }

    private String normalizeUser(String userId) {
        return requireText(userId, "用户身份").trim();
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("缺少" + label);
        }
        return value.trim();
    }

    private String cleanStation(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String cleaned = value.trim()
                .replace("的车次", "")
                .replace("车次", "");
        return StringUtils.hasText(cleaned) ? cleaned : fallback;
    }

    private int normalizeCount(Integer ticketCount) {
        int count = ticketCount == null ? 1 : ticketCount;
        if (count < 1 || count > 5) {
            throw new IllegalArgumentException("每次最多可购买5张车票");
        }
        return count;
    }

    private BigDecimal basePrice(String seatType) {
        return switch (seatType) {
            case "一等座" -> new BigDecimal("268");
            case "商务座" -> new BigDecimal("588");
            case "软座" -> new BigDecimal("178");
            case "硬卧" -> new BigDecimal("268");
            case "软卧" -> new BigDecimal("398");
            default -> new BigDecimal("128");
        };
    }

    private String randomSeat(String seatType) {
        int car = ThreadLocalRandom.current().nextInt(1, 9);
        int row = ThreadLocalRandom.current().nextInt(1, 22);
        char seat = "ABCDF".charAt(ThreadLocalRandom.current().nextInt(5));
        String suffix = "硬卧".equals(seatType) || "软卧".equals(seatType) ? "下铺" : "座";
        return car + "车" + row + seat + suffix;
    }
}
