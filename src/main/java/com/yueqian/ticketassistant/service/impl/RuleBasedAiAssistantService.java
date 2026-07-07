package com.yueqian.ticketassistant.service.impl;

import com.yueqian.ticketassistant.entity.ChatMessage;
import com.yueqian.ticketassistant.entity.PendingTicketOrder;
import com.yueqian.ticketassistant.entity.TicketOrder;
import com.yueqian.ticketassistant.service.AiAssistantService;
import com.yueqian.ticketassistant.service.MessageService;
import com.yueqian.ticketassistant.service.TicketOrderService;
import com.yueqian.ticketassistant.tool.DeepSeekClient;
import com.yueqian.ticketassistant.tool.DestinationTool;
import com.yueqian.ticketassistant.tool.WeatherTool;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleBasedAiAssistantService implements AiAssistantService {

    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{17}[0-9Xx]|\\d{15})(?!\\d)");
    private static final Pattern DATE = Pattern.compile("\\b20\\d{2}-\\d{2}-\\d{2}\\b");
    private static final Pattern TRAIN = Pattern.compile("\\b[GDCKZT]\\d{1,5}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER = Pattern.compile("\\bRA\\d{13,}\\b", Pattern.CASE_INSENSITIVE);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final MessageService messageService;
    private final TicketOrderService orderService;
    private final WeatherTool weatherTool;
    private final DestinationTool destinationTool;
    private final DeepSeekClient deepSeekClient;

    public RuleBasedAiAssistantService(MessageService messageService,
                                       TicketOrderService orderService,
                                       WeatherTool weatherTool,
                                       DestinationTool destinationTool,
                                       DeepSeekClient deepSeekClient) {
        this.messageService = messageService;
        this.orderService = orderService;
        this.weatherTool = weatherTool;
        this.destinationTool = destinationTool;
        this.deepSeekClient = deepSeekClient;
    }

    @Override
    public AiAnswer answer(String userId, Long sessionId, String message) {
        String normalized = message == null ? "" : message.trim();
        List<ChatMessage> memory = messageService.list(sessionId, userId);
        String combined = collect(memory) + "\n" + normalized;

        try {
            if (containsAny(normalized, "退票", "退款", "取消订单")) {
                return handleRefund(userId, combined);
            }
            if (containsAny(normalized, "购票", "买票", "订票", "车票", "帮我订", "下单")) {
                return handleBooking(userId, sessionId, combined);
            }
            if (containsAny(normalized, "天气", "下雨", "气温")) {
                String city = extractCity(normalized);
                return new AiAnswer(weatherTool.query(city), "weather.query");
            }
            if (containsAny(normalized, "随机", "抽取")) {
                DestinationTool.Destination d = destinationTool.random();
                String ai = destinationSuggestion(normalized, d);
                String text = "目的地：" + d.station()
                        + "\n车次：" + d.trainNo()
                        + "\n类型：" + d.tag()
                        + "\n建议：" + ai;
                return new AiAnswer(text, "destination.ai");
            }
            if (containsAny(normalized, "推荐目的地", "目的地建议", "去哪", "凉快", "旅行", "出发")) {
                DestinationTool.Destination d = destinationTool.random();
                return new AiAnswer(destinationSuggestion(normalized, d), "destination.ai");
            }
            if (containsAny(normalized, "订单", "查票", "我的票")) {
                return new AiAnswer("请提供订单号，或在订单页面查看当前账号下的订单。", null);
            }
            return new AiAnswer(deepSeekReply(memory, normalized), "deepseek.chat");
        } catch (IllegalArgumentException ex) {
            return new AiAnswer(ex.getMessage(), "tool.error");
        }
    }

    private AiAnswer handleBooking(String userId, Long sessionId, String combined) {
        Map<String, String> fields = extractBookingFields(combined);
        StringBuilder missing = new StringBuilder();
        require(fields, "passengerName", "乘车人姓名", missing);
        require(fields, "idCard", "身份证号", missing);
        require(fields, "trainNo", "车次", missing);
        require(fields, "travelDate", "乘车日期", missing);
        require(fields, "seatType", "座位类型", missing);
        if (!missing.isEmpty()) {
            return new AiAnswer("还需要补充：\n" + missing, null);
        }
        PendingTicketOrder order = orderService.prepare(userId, sessionId, fields.get("passengerName"), fields.get("idCard"),
                fields.get("trainNo"), fields.get("travelDate"), fields.get("seatType"),
                fields.get("departureStation"), fields.get("arrivalStation"), parseCount(fields.get("ticketCount")));
        String text = "已生成待确认订单。\n"
                + "编号：" + order.getId() + "\n"
                + "乘车人：" + order.getPassengerName() + "\n"
                + "证件：" + maskId(order.getIdCard()) + "\n"
                + "车次：" + order.getTrainNo() + "\n"
                + "日期：" + order.getTravelDate() + "\n"
                + "路线：" + order.getDepartureStation() + " → " + order.getArrivalStation() + "\n"
                + "座位：" + order.getSeatType() + "\n"
                + "预估：" + order.getEstimatedPrice() + " 元\n"
                + "请在订单页面确认。";
        return new AiAnswer(text, "ticket.prepare");
    }

    private AiAnswer handleRefund(String userId, String combined) {
        String orderNo = first(ORDER, combined);
        Map<String, String> fields = extractBookingFields(combined);
        if (!StringUtils.hasText(orderNo)
                && (!StringUtils.hasText(fields.get("passengerName")) || !StringUtils.hasText(fields.get("idCard")))) {
            return new AiAnswer("请先选择需要退票的订单。", "ticket.refund.intent");
        }
        String text = StringUtils.hasText(orderNo)
                ? "已识别订单：" + orderNo.toUpperCase() + "。请在退票确认窗口核对后提交。"
                : "已识别退票信息。请在退票确认窗口核对订单后提交。";
        return new AiAnswer(text, "ticket.refund.intent");
    }

    private Map<String, String> extractBookingFields(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("idCard", first(ID_CARD, text));
        fields.put("travelDate", extractDate(text));
        fields.put("trainNo", first(TRAIN, text));

        for (String seat : List.of("商务座", "一等座", "二等座", "硬座", "软座", "硬卧", "软卧")) {
            if (text.contains(seat)) {
                fields.put("seatType", seat);
                break;
            }
        }
        Matcher count = Pattern.compile("(\\d+)\\s*张").matcher(text);
        if (count.find()) {
            fields.put("ticketCount", count.group(1));
        }
        Matcher name = Pattern.compile("(?:姓名|乘车人|我叫|名字是)[:：\\s]*([\\u4e00-\\u9fa5]{2,6})").matcher(text);
        if (name.find()) {
            fields.put("passengerName", name.group(1));
        }
        Matcher route = Pattern.compile("([\\u4e00-\\u9fa5]{2,8})(?:到|至|->)([\\u4e00-\\u9fa5]{2,8})").matcher(text);
        if (route.find()) {
            fields.put("departureStation", route.group(1));
            fields.put("arrivalStation", route.group(2));
        }
        return fields;
    }

    private String extractDate(String text) {
        String explicit = first(DATE, text);
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        LocalDate today = LocalDate.now(ZONE);
        if (text.contains("明天")) {
            return today.plusDays(1).toString();
        }
        if (text.contains("后天")) {
            return today.plusDays(2).toString();
        }
        if (text.contains("今天")) {
            return today.toString();
        }
        return null;
    }

    private String deepSeekReply(List<ChatMessage> memory, String message) {
        String reply = deepSeekClient.chat(List.of(
                new DeepSeekClient.Message("system", "你是铁路票务助手。回答必须简洁，不输出账号提示，不承诺已经出票。购票只引导用户提供信息并等待系统生成待确认订单。"),
                new DeepSeekClient.Message("user", collect(memory) + "\n用户当前问题：" + message)
        ));
        return StringUtils.hasText(reply) ? reply.trim() : "请说明要办理的事项。";
    }

    private String destinationSuggestion(String message, DestinationTool.Destination destination) {
        String reply = deepSeekClient.chat(List.of(
                new DeepSeekClient.Message("system", "你是铁路出行顾问。给出一个简短目的地建议，包含目的地、原因、车次建议。不要超过80字。"),
                new DeepSeekClient.Message("user", "用户偏好：" + message + "\n候选目的地："
                        + destination.station() + "，" + destination.tag() + "，车次" + destination.trainNo()
                        + "，基础理由：" + destination.reason())
        ));
        if (StringUtils.hasText(reply)) {
            return reply.trim();
        }
        return destination.reason() + " 建议车次：" + destination.trainNo() + "。";
    }

    private String collect(List<ChatMessage> memory) {
        StringBuilder builder = new StringBuilder();
        int start = Math.max(0, memory.size() - 8);
        for (int i = start; i < memory.size(); i++) {
            builder.append(memory.get(i).getContent()).append('\n');
        }
        return builder.toString();
    }

    private void require(Map<String, String> fields, String key, String label, StringBuilder missing) {
        if (!StringUtils.hasText(fields.get(key))) {
            missing.append("- ").append(label).append('\n');
        }
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private Integer parseCount(String value) {
        return StringUtils.hasText(value) ? Integer.parseInt(value) : 1;
    }

    private String maskId(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return "已脱敏";
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    private String extractCity(String text) {
        Matcher matcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,8})(?:天气|气温|会不会下雨)").matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        for (String city : List.of("北京", "上海", "广州", "深圳", "杭州", "南京", "成都", "西安")) {
            if (text.contains(city)) {
                return city;
            }
        }
        return "北京";
    }
}
