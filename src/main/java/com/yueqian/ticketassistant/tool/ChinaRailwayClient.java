package com.yueqian.ticketassistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yueqian.ticketassistant.dto.StationOption;
import com.yueqian.ticketassistant.dto.TrainOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ChinaRailwayClient {

    private static final Pattern STATION_ROW = Pattern.compile("@([^|]+)\\|([^|]+)\\|([^|]+)\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|");
    private static final int MAX_CANDIDATES_PER_SIDE = 8;
    private static final int MAX_FUZZY_QUERIES = 12;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String stationUrl;
    private final String fallbackStationUrl;
    private final Map<String, Station> stationsByName = new ConcurrentHashMap<>();
    private final Map<String, String> codeToName = new ConcurrentHashMap<>();
    private volatile boolean stationLoaded;

    public ChinaRailwayClient(ObjectMapper objectMapper,
                              @Value("${ticket.assistant.railway.base-url:https://kyfw.12306.cn}") String baseUrl,
                              @Value("${ticket.assistant.railway.station-url:https://kyfw.12306.cn/otn/resources/js/framework/station_name.js}") String stationUrl,
                              @Value("${ticket.assistant.railway.fallback-station-url:https://www.12306.cn/index/script/core/common/station_name_v10026.js}") String fallbackStationUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = trimEnd(baseUrl);
        this.stationUrl = stationUrl;
        this.fallbackStationUrl = fallbackStationUrl;
    }

    public List<TrainOption> search(String from, String to, String date) {
        List<Station> fromStations = resolveStationCandidates(from);
        List<Station> toStations = resolveStationCandidates(to);
        if (fromStations.isEmpty() || toStations.isEmpty() || !StringUtils.hasText(date)) {
            return List.of();
        }
        try {
            warmup();
        } catch (Exception ignored) {
        }
        List<StationPair> pairs = new ArrayList<>();
        boolean fuzzySearch = fromStations.size() > 1 || toStations.size() > 1;
        pairing:
        for (Station fromStation : fromStations) {
            for (Station toStation : toStations) {
                if (fromStation.code().equals(toStation.code())) {
                    continue;
                }
                pairs.add(new StationPair(fromStation, toStation));
                if (fuzzySearch && pairs.size() >= MAX_FUZZY_QUERIES) {
                    break pairing;
                }
            }
        }
        Map<String, TrainOption> merged = new HashMap<>();
        pairs.parallelStream()
                .flatMap(pair -> query12306(pair.from(), pair.to(), date).stream())
                .forEach(option -> {
                    synchronized (merged) {
                        merged.putIfAbsent(option.trainNo() + "|" + option.departureStation() + "|" + option.arrivalStation() + "|" + option.departureTime(), option);
                    }
                });
        if (merged.isEmpty() && !fuzzySearch && (isCityName(from) || isCityName(to))) {
            for (Station fromStation : expandExactCity(from, fromStations)) {
                for (Station toStation : expandExactCity(to, toStations)) {
                    if (fromStation.code().equals(toStation.code())) {
                        continue;
                    }
                    for (TrainOption option : query12306(fromStation, toStation, date)) {
                        merged.putIfAbsent(option.trainNo() + "|" + option.departureStation() + "|" + option.arrivalStation() + "|" + option.departureTime(), option);
                    }
                }
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(TrainOption::departureTime)
                        .thenComparing(TrainOption::trainNo)
                        .thenComparing(TrainOption::departureStation)
                        .thenComparing(TrainOption::arrivalStation))
                .toList();
    }

    public List<StationOption> searchStations(String keyword) {
        ensureStations();
        String target = Station.clean(keyword);
        return stationsByName.values().stream()
                .filter(station -> !StringUtils.hasText(target)
                        || station.name().contains(target)
                        || station.city().contains(target)
                        || station.code().equalsIgnoreCase(target))
                .sorted(Comparator.comparingInt((Station station) -> stationScore(station, target))
                        .thenComparing(Station::name))
                .limit(80)
                .map(station -> new StationOption(station.name(), station.code(), station.city()))
                .toList();
    }

    public String normalizeCity(String station) {
        Station resolved = resolveStation(station);
        if (resolved == null) {
            return Station.clean(station);
        }
        return resolved.city();
    }

    private List<TrainOption> query12306(Station from, Station to, String date) {
        ensureStations();
        try {
            List<String> endpoints = new ArrayList<>(List.of("queryG", "queryO", "queryZ", "queryA", "queryC", "queryH", "query"));
            for (int index = 0; index < endpoints.size(); index++) {
                String api = endpoints.get(index);
                String url = baseUrl + "/otn/leftTicket/" + api
                        + "?leftTicketDTO.train_date=" + encode(date)
                        + "&leftTicketDTO.from_station=" + encode(from.code())
                        + "&leftTicketDTO.to_station=" + encode(to.code())
                        + "&purpose_codes=ADULT";
                HttpResponse<String> response = httpClient.send(request(url)
                        .header("Cookie", queryCookie(from, to, date))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                String body = response.body();
                if (!looksJson(body)) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(body);
                String suggested = root.path("c_url").asText("");
                if (StringUtils.hasText(suggested) && suggested.contains("leftTicket/")) {
                    String suggestedApi = suggested.substring(suggested.lastIndexOf('/') + 1);
                    if (!endpoints.contains(suggestedApi)) {
                        endpoints.add(index + 1, suggestedApi);
                    }
                    continue;
                }
                JsonNode rows = root.path("data").path("result");
                if (!rows.isArray()) {
                    continue;
                }
                List<TrainOption> options = new ArrayList<>();
                for (JsonNode row : rows) {
                    TrainOption option = parseTicketRow(row.asText(), from, to);
                    if (option != null) {
                        options.add(option);
                    }
                }
                options.sort(Comparator.comparing(TrainOption::departureTime));
                if (!options.isEmpty()) {
                    return options;
                }
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private TrainOption parseTicketRow(String raw, Station from, Station to) {
        String[] p = raw.split("\\|", -1);
        if (p.length < 33 || !StringUtils.hasText(p[3])) {
            return null;
        }
        if (!from.code().equals(p[6]) || !to.code().equals(p[7])) {
            return null;
        }
        String trainNo = p[3];
        String fromName = codeToName.getOrDefault(p[6], p[6]);
        String toName = codeToName.getOrDefault(p[7], p[7]);
        String start = p[8];
        String arrive = p[9];
        String duration = p[10];
        String business = valueAt(p, 32);
        String first = valueAt(p, 31);
        String second = valueAt(p, 30);
        String hardSeat = valueAt(p, 29);
        String noSeat = valueAt(p, 26);
        Map<String, String> seats = new LinkedHashMap<>();
        seats.put("\u4e8c\u7b49\u5ea7", second);
        seats.put("\u4e00\u7b49\u5ea7", first);
        seats.put("\u5546\u52a1\u5ea7", business);
        seats.put("\u786c\u5ea7", hardSeat);
        seats.put("\u65e0\u5ea7", noSeat);
        String seat = firstAvailable(seats);
        String summary = "\u771f\u5b9e12306\uff1a\u4e8c\u7b49\u5ea7 " + blankToDash(second)
                + "\uff0c\u4e00\u7b49\u5ea7 " + blankToDash(first)
                + "\uff0c\u5546\u52a1\u5ea7 " + blankToDash(business);
        return new TrainOption(trainNo, fromName, toName, start, arrive, duration, seat, BigDecimal.ZERO, summary);
    }

    private String firstAvailable(Map<String, String> seats) {
        for (Map.Entry<String, String> entry : seats.entrySet()) {
            String value = entry.getValue();
            if (StringUtils.hasText(value) && !"\u65e0".equals(value) && !"--".equals(value) && !"*".equals(value)) {
                return entry.getKey();
            }
        }
        return "\u4e8c\u7b49\u5ea7";
    }

    private Station resolveStation(String input) {
        ensureStations();
        String clean = Station.clean(input);
        if (!StringUtils.hasText(clean)) {
            return null;
        }
        Map<String, String> cityMajor = majorStations();
        clean = cityMajor.getOrDefault(clean, clean);
        Station exact = stationsByName.get(clean);
        if (exact != null) {
            return exact;
        }
        String target = clean.toUpperCase(Locale.ROOT);
        return stationsByName.values().stream()
                .filter(item -> item.code().equalsIgnoreCase(target))
                .findFirst()
                .orElse(null);
    }

    private List<Station> resolveStationCandidates(String input) {
        ensureStations();
        String clean = Station.clean(input);
        if (!StringUtils.hasText(clean)) {
            return List.of();
        }
        List<Station> cityMatches = stationsByName.values().stream()
                .filter(item -> item.city().equals(clean))
                .sorted(Comparator.comparingInt((Station station) -> stationCandidateScore(station, clean))
                        .thenComparing(Station::name))
                .limit(MAX_CANDIDATES_PER_SIDE)
                .toList();
        if (cityMatches.size() > 1) {
            return cityMatches;
        }
        Station exact = stationsByName.get(clean);
        if (exact != null) {
            return List.of(exact);
        }
        String upper = clean.toUpperCase(Locale.ROOT);
        List<Station> codeMatches = stationsByName.values().stream()
                .filter(item -> item.code().equalsIgnoreCase(upper))
                .toList();
        if (!codeMatches.isEmpty()) {
            return codeMatches;
        }
        if (!cityMatches.isEmpty()) {
            return cityMatches;
        }
        List<Station> fuzzyMatches = stationsByName.values().stream()
                .filter(item -> item.city().contains(clean) || item.name().contains(clean))
                .sorted(Comparator.comparingInt((Station station) -> stationCandidateScore(station, clean))
                        .thenComparing(Station::name))
                .limit(MAX_CANDIDATES_PER_SIDE)
                .toList();
        if (!fuzzyMatches.isEmpty()) {
            return fuzzyMatches;
        }
        Map<String, String> cityMajor = majorStations();
        Station major = stationsByName.get(cityMajor.getOrDefault(clean, ""));
        return major == null ? List.of() : List.of(major);
    }

    private boolean isCityName(String input) {
        String clean = Station.clean(input);
        return stationsByName.values().stream().filter(item -> item.city().equals(clean)).limit(2).count() > 1;
    }

    private List<Station> expandExactCity(String input, List<Station> current) {
        String clean = Station.clean(input);
        List<Station> cityMatches = stationsByName.values().stream()
                .filter(item -> item.city().equals(clean))
                .sorted(Comparator.comparingInt((Station station) -> stationCandidateScore(station, clean))
                        .thenComparing(Station::name))
                .limit(4)
                .toList();
        return cityMatches.isEmpty() ? current : cityMatches;
    }

    private synchronized void ensureStations() {
        if (stationLoaded) {
            return;
        }
        try {
            HttpResponse<String> response = httpClient.send(request(stationUrl).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            loadStationsFromText(response.body());
        } catch (Exception ignored) {
        }
        if (stationsByName.isEmpty()) {
            try {
                HttpResponse<String> response = httpClient.send(request(fallbackStationUrl).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                loadStationsFromText(response.body());
            } catch (Exception ignored) {
            }
        }
        if (stationsByName.isEmpty()) {
            try (InputStream input = ChinaRailwayClient.class.getResourceAsStream("/railway/station_name.js")) {
                if (input != null) {
                    loadStationsFromText(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
            }
        }
        stationLoaded = !stationsByName.isEmpty();
    }

    private void loadStationsFromText(String text) {
        Matcher matcher = STATION_ROW.matcher(text == null ? "" : text);
        while (matcher.find()) {
            Station station = new Station(matcher.group(2), matcher.group(3), StringUtils.hasText(matcher.group(8)) ? matcher.group(8) : matcher.group(2));
            stationsByName.put(station.name(), station);
            codeToName.put(station.code(), station.name());
        }
    }

    private void warmup() throws Exception {
        httpClient.send(request(baseUrl + "/otn/leftTicket/init").GET().build(), HttpResponse.BodyHandlers.discarding());
    }

    private String queryCookie(Station from, Station to, String date) {
        return "_jc_save_fromStation=" + cookieStation(from)
                + "; _jc_save_toStation=" + cookieStation(to)
                + "; _jc_save_fromDate=" + date
                + "; _jc_save_toDate=" + LocalDate.now()
                + "; _jc_save_wfdc_flag=dc";
    }

    private String cookieStation(Station station) {
        return encodeUnicodeCookie(station.name()) + "%2C" + station.code();
    }

    private String encodeUnicodeCookie(String value) {
        StringBuilder builder = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c > 127) {
                builder.append("%u").append(String.format(Locale.ROOT, "%04X", (int) c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(18))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126 Safari/537.36")
                .header("Referer", baseUrl + "/otn/leftTicket/init")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest");
    }

    private Map<String, String> majorStations() {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("\u5317\u4eac", "\u5317\u4eac\u5357");
        aliases.put("\u4e0a\u6d77", "\u4e0a\u6d77\u8679\u6865");
        aliases.put("\u5e7f\u5dde", "\u5e7f\u5dde\u5357");
        aliases.put("\u6df1\u5733", "\u6df1\u5733\u5317");
        aliases.put("\u6606\u660e", "\u6606\u660e\u5357");
        aliases.put("\u676d\u5dde", "\u676d\u5dde\u4e1c");
        aliases.put("\u5357\u4eac", "\u5357\u4eac\u5357");
        aliases.put("\u6210\u90fd", "\u6210\u90fd\u4e1c");
        aliases.put("\u897f\u5b89", "\u897f\u5b89\u5317");
        aliases.put("\u6e5b\u6c5f", "\u6e5b\u6c5f\u897f");
        return aliases;
    }

    private String valueAt(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index] : "";
    }

    private int stationScore(Station station, String target) {
        if (!StringUtils.hasText(target)) {
            return 10;
        }
        if (station.name().equals(target) || station.code().equalsIgnoreCase(target)) {
            return 0;
        }
        if (station.name().startsWith(target)) {
            return 1;
        }
        if (station.city().equals(target)) {
            return 2;
        }
        if (station.city().startsWith(target)) {
            return 3;
        }
        return 9;
    }

    private int stationCandidateScore(Station station, String target) {
        List<String> preferred = preferredStations(target);
        int preferredIndex = preferred.indexOf(station.name());
        if (preferredIndex >= 0) {
            return preferredIndex;
        }
        Map<String, String> cityMajor = majorStations();
        if (station.name().equals(cityMajor.get(target))) {
            return 10;
        }
        if (station.name().equals(target)) {
            return 20;
        }
        if (station.city().equals(target) && station.name().equals(target)) {
            return 21;
        }
        if (station.name().endsWith("\u5317") || station.name().endsWith("\u5357") || station.name().endsWith("\u4e1c") || station.name().endsWith("\u897f")) {
            return 30;
        }
        return 50;
    }

    private List<String> preferredStations(String target) {
        return switch (target) {
            case "\u5317\u4eac" -> List.of("\u5317\u4eac\u897f", "\u5317\u4eac\u5357", "\u5317\u4eac", "\u5317\u4eac\u4e30\u53f0", "\u5317\u4eac\u671d\u9633", "\u6e05\u6cb3");
            case "\u4e0a\u6d77" -> List.of("\u4e0a\u6d77\u8679\u6865", "\u4e0a\u6d77", "\u4e0a\u6d77\u5357", "\u4e0a\u6d77\u897f");
            case "\u5e7f\u5dde" -> List.of("\u5e7f\u5dde\u5357", "\u5e7f\u5dde", "\u5e7f\u5dde\u4e1c", "\u5e7f\u5dde\u767d\u4e91", "\u5e7f\u5dde\u5317");
            case "\u6df1\u5733" -> List.of("\u6df1\u5733\u5317", "\u798f\u7530", "\u6df1\u5733", "\u6df1\u5733\u576a\u5c71", "\u5149\u660e\u57ce", "\u6df1\u5733\u673a\u573a");
            case "\u676d\u5dde" -> List.of("\u676d\u5dde\u4e1c", "\u676d\u5dde\u897f", "\u676d\u5dde", "\u676d\u5dde\u5357");
            case "\u5357\u4eac" -> List.of("\u5357\u4eac\u5357", "\u5357\u4eac", "\u6c5f\u5b81");
            case "\u6210\u90fd" -> List.of("\u6210\u90fd\u4e1c", "\u6210\u90fd\u897f", "\u6210\u90fd\u5357", "\u6210\u90fd");
            case "\u6e5b\u6c5f" -> List.of("\u6e5b\u6c5f\u897f", "\u6e5b\u6c5f");
            default -> List.of();
        };
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value : "--";
    }

    private boolean looksJson(String body) {
        return body != null && body.trim().startsWith("{");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimEnd(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record Station(String name, String code, String city) {
        static String clean(String value) {
            if (value == null) {
                return "";
            }
            return value.trim()
                    .replace("\u7684\u8f66\u6b21", "")
                    .replace("\u8f66\u6b21", "")
                    .replace("\u8f66\u7968", "")
                    .replace("\u7ad9", "");
        }
    }

    private record StationPair(Station from, Station to) {
    }
}
