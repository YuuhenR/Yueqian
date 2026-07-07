package com.yueqian.ticketassistant.controller;

import com.yueqian.ticketassistant.dto.ApiResponse;
import com.yueqian.ticketassistant.tool.ChinaRailwayClient;
import com.yueqian.ticketassistant.tool.WeatherTool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherTool weatherTool;
    private final ChinaRailwayClient railwayClient;

    public WeatherController(WeatherTool weatherTool, ChinaRailwayClient railwayClient) {
        this.weatherTool = weatherTool;
        this.railwayClient = railwayClient;
    }

    @GetMapping("/city")
    public ApiResponse<String> city(@RequestParam String city) {
        return ApiResponse.ok(weatherTool.query(cityName(city)));
    }

    @GetMapping("/route")
    public ApiResponse<String> route(@RequestParam String from, @RequestParam String to) {
        String departure = weatherTool.query(cityName(from));
        String arrival = weatherTool.query(cityName(to));
        return ApiResponse.ok("\u51fa\u53d1\u5730\uff1a" + departure + "\n\u5230\u8fbe\u5730\uff1a" + arrival);
    }

    private String cityName(String station) {
        if (station == null || station.isBlank()) {
            return "\u5317\u4eac";
        }
        String fromRailway = railwayClient.normalizeCity(station);
        String cleaned = (fromRailway == null || fromRailway.isBlank() ? station : fromRailway).trim()
                .replace("\u7684\u8f66\u6b21", "")
                .replace("\u8f66\u6b21", "")
                .replace("\u7ad9", "");
        for (String suffix : new String[]{"\u5357", "\u897f", "\u5317", "\u4e1c", "\u8679\u6865"}) {
            if (cleaned.endsWith(suffix) && cleaned.length() > suffix.length() + 1) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length());
                break;
            }
        }
        return cleaned;
    }
}
