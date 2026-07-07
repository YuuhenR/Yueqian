package com.yueqian.ticketassistant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yueqian.ticketassistant.dto.ApiResponse;
import com.yueqian.ticketassistant.dto.RouteAssistRequest;
import com.yueqian.ticketassistant.dto.RouteIntentRequest;
import com.yueqian.ticketassistant.dto.RouteIntentResponse;
import com.yueqian.ticketassistant.dto.RouteSearchRequest;
import com.yueqian.ticketassistant.dto.StationOption;
import com.yueqian.ticketassistant.dto.TrainOption;
import com.yueqian.ticketassistant.tool.ChinaRailwayClient;
import com.yueqian.ticketassistant.tool.DeepSeekClient;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final ChinaRailwayClient railwayClient;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    public RouteController(ChinaRailwayClient railwayClient, DeepSeekClient deepSeekClient, ObjectMapper objectMapper) {
        this.railwayClient = railwayClient;
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/search")
    public ApiResponse<List<TrainOption>> search(@Valid @RequestBody RouteSearchRequest request) {
        return ApiResponse.ok(railwayClient.search(
                request.departureStation(),
                request.arrivalStation(),
                request.travelDate()
        ));
    }

    @GetMapping("/stations")
    public ApiResponse<List<StationOption>> stations(@RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(railwayClient.searchStations(keyword));
    }

    @PostMapping("/intent")
    public ApiResponse<RouteIntentResponse> intent(@Valid @RequestBody RouteIntentRequest request) {
        if (isDestinationAdvice(request.userText())) {
            return ApiResponse.ok(nonBookingIntent());
        }
        RouteIntentResponse fallback = fallbackIntent(request.userText());
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String reply = deepSeekClient.chat(List.of(
                new DeepSeekClient.Message("system", """
                        你是铁路出行语义解析器，只返回 JSON，不要解释。
                        字段：needsBookingPage(boolean), departureStation, arrivalStation, travelDate(yyyy-MM-dd), timePreference(all/morning/noon/afternoon/evening), passengerCount(number), seatType。
                        规则：
                        1. 只抽取用户真实表达的出发地、到达地、日期、时段、人数、座位。
                        2. “一个人/两个人/1人”是人数，不得并入站点。
                        3. “明天/后天/今天”按当前日期换算。
                        4. 站点不确定时保留城市名，不要编造复杂站名。
                        5. 只有用户给出了明确出发地和目的地，并且是在查车次、买票、订票、安排行程时，needsBookingPage 才为 true。
                        6. “去哪比较好/周末休息去哪/推荐目的地/哪里好玩/想出去玩”属于目的地建议，不是购票；needsBookingPage 必须为 false，arrivalStation 为空。
                        7. 如果用户只说“广州去哪比较好”，departureStation 可填“广州”，arrivalStation 必须为空，needsBookingPage 为 false。
                        8. 用户说“北京一个人/深圳中午”等人数或时段不得当作站名。
                        9. 没说座位时 seatType 填“二等座”；没说人数 passengerCount 填 1；没说日期 travelDate 为空字符串。
                        """),
                new DeepSeekClient.Message("user", "当前日期：" + today + "\n用户输入：" + request.userText())
        ));
        RouteIntentResponse parsed = parseIntentJson(reply);
        return ApiResponse.ok(parsed == null ? fallback : mergeIntent(parsed, fallback));
    }

    @PostMapping("/assist")
    public ApiResponse<String> assist(@Valid @RequestBody RouteAssistRequest request) {
        StringBuilder trains = new StringBuilder();
        request.options().stream().limit(12).forEach(option -> trains
                .append(option.trainNo()).append(' ')
                .append(option.departureStation()).append(' ')
                .append(option.departureTime()).append(" -> ")
                .append(option.arrivalStation()).append(' ')
                .append(option.arrivalTime()).append("，历时")
                .append(option.duration()).append("，")
                .append(option.reason()).append('\n'));
        String reply = deepSeekClient.chat(List.of(
                new DeepSeekClient.Message("system", """
                        你是铁路出行决策助手。你只能基于系统提供的真实12306候选车次做比较和建议。
                        不得编造车次、票价或余票，不得声称已经下单或出票。
                        输出简短中文，最多三条建议。必须提醒用户最终点选车次并确认乘车人后，系统才会生成待确认订单。
                        """),
                new DeepSeekClient.Message("user", "用户需求：" + request.userText()
                        + "\n出发：" + request.departureStation()
                        + "\n到达：" + request.arrivalStation()
                        + "\n日期：" + request.travelDate()
                        + "\n时段：" + (request.timePreference() == null ? "不限" : request.timePreference())
                        + "\n真实候选车次：\n" + trains)
        ));
        if (reply == null || reply.isBlank()) {
            TrainOption first = request.options().get(0);
            reply = "已列出真实12306候选车次。可优先看 " + first.trainNo()
                    + "（" + first.departureTime() + " -> " + first.arrivalTime()
                    + "）。请点选车次并确认乘车人后生成待确认订单。";
        }
        return ApiResponse.ok(reply.trim());
    }

    private RouteIntentResponse parseIntentJson(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            String json = text.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            JsonNode node = objectMapper.readTree(json);
            return new RouteIntentResponse(
                    node.path("needsBookingPage").asBoolean(false),
                    node.path("departureStation").asText(""),
                    node.path("arrivalStation").asText(""),
                    node.path("travelDate").asText(""),
                    node.path("timePreference").asText("all"),
                    node.path("passengerCount").asInt(1),
                    node.path("seatType").asText("\u4e8c\u7b49\u5ea7")
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private RouteIntentResponse mergeIntent(RouteIntentResponse ai, RouteIntentResponse fallback) {
        String from = choose(ai.departureStation(), fallback.departureStation());
        String to = choose(ai.arrivalStation(), fallback.arrivalStation());
        boolean validRoute = StringUtils.hasText(from) && StringUtils.hasText(to) && !isUnclearDestination(to);
        return new RouteIntentResponse(
                ai.needsBookingPage() && validRoute,
                from,
                validRoute ? to : "",
                choose(ai.travelDate(), fallback.travelDate()),
                choose(ai.timePreference(), fallback.timePreference()),
                ai.passengerCount() == null || ai.passengerCount() < 1 ? fallback.passengerCount() : ai.passengerCount(),
                choose(ai.seatType(), fallback.seatType())
        );
    }

    private RouteIntentResponse fallbackIntent(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (isDestinationAdvice(cleaned)) {
            return nonBookingIntent();
        }
        String routeText = cleaned.replaceAll("20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2}", "")
                .replaceAll("今天|明天|后天|早上|上午|中午|下午|晚上|夜间|一个人|一人|两个人|二人|三个人|三人|\\d+\\s*[人张]|高铁|动车|火车|车次|车票|买票|购票|订票|帮我|我要|我想|请", "");
        Matcher route = Pattern.compile("(?:从)?([\\u4e00-\\u9fa5]{2,12})(?:到|去|至|->|→)([\\u4e00-\\u9fa5]{2,12})").matcher(routeText);
        String from = "";
        String to = "";
        if (route.find()) {
            from = cleanStation(route.group(1));
            to = cleanStation(route.group(2));
        }
        boolean validRoute = StringUtils.hasText(from) && StringUtils.hasText(to) && !isUnclearDestination(to);
        return new RouteIntentResponse(
                validRoute,
                from,
                validRoute ? to : "",
                fallbackDate(cleaned),
                fallbackTime(cleaned),
                fallbackCount(cleaned),
                fallbackSeat(cleaned)
        );
    }

    private String fallbackDate(String text) {
        Matcher iso = Pattern.compile("\\b(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b").matcher(text);
        if (iso.find()) {
            return iso.group(1) + "-" + pad2(iso.group(2)) + "-" + pad2(iso.group(3));
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (text.contains("\u540e\u5929")) return today.plusDays(2).toString();
        if (text.contains("\u660e\u5929")) return today.plusDays(1).toString();
        if (text.contains("\u4eca\u5929")) return today.toString();
        return "";
    }

    private String fallbackTime(String text) {
        if (text.contains("\u65e9\u4e0a") || text.contains("\u4e0a\u5348")) return "morning";
        if (text.contains("\u4e2d\u5348")) return "noon";
        if (text.contains("\u4e0b\u5348")) return "afternoon";
        if (text.contains("\u665a\u4e0a") || text.contains("\u591c\u95f4")) return "evening";
        return "all";
    }

    private Integer fallbackCount(String text) {
        if (text.contains("\u4e24\u4e2a\u4eba") || text.contains("\u4e8c\u4eba") || text.contains("\u4e24\u5f20")) return 2;
        if (text.contains("\u4e09\u4e2a\u4eba") || text.contains("\u4e09\u4eba") || text.contains("\u4e09\u5f20")) return 3;
        Matcher count = Pattern.compile("([1-5])\\s*(?:\u4eba|\u5f20)").matcher(text);
        return count.find() ? Integer.parseInt(count.group(1)) : 1;
    }

    private String fallbackSeat(String text) {
        for (String seat : List.of("\u5546\u52a1\u5ea7", "\u4e00\u7b49\u5ea7", "\u4e8c\u7b49\u5ea7", "\u786c\u5ea7", "\u8f6f\u5ea7", "\u786c\u5367", "\u8f6f\u5367")) {
            if (text.contains(seat)) return seat;
        }
        return "\u4e8c\u7b49\u5ea7";
    }

    private String cleanStation(String value) {
        return value == null ? "" : value.replaceAll("出发|车票|买票|购票|订票|高铁|动车|火车|票", "").trim();
    }

    private boolean isDestinationAdvice(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return Pattern.compile("(去哪|去哪里|哪儿|哪里|什么地方|比较好|好玩|推荐|周末|休息|旅游|旅行|度假|散心|目的地)").matcher(text).find()
                && !Pattern.compile("(买票|购票|订票|车票|车次|下单|订单|退票)").matcher(text).find();
    }

    private boolean isUnclearDestination(String value) {
        return !StringUtils.hasText(value)
                || Pattern.compile("(哪|哪里|哪儿|何处|什么地方|比较好|好玩|推荐|周末|休息|旅游|旅行|一个人|\\d+人)").matcher(value).find();
    }

    private RouteIntentResponse nonBookingIntent() {
        return new RouteIntentResponse(false, "", "", "", "all", 1, "\u4e8c\u7b49\u5ea7");
    }

    private String choose(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary.trim() : (fallback == null ? "" : fallback);
    }

    private String pad2(String value) {
        return value != null && value.length() == 1 ? "0" + value : value;
    }
}
