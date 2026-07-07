package com.yueqian.ticketassistant.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yueqian.ticketassistant.entity.DestinationRecommendation;
import com.yueqian.ticketassistant.mapper.DestinationRecommendationMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class DestinationTool {

    private final DestinationRecommendationMapper mapper;

    public DestinationTool(DestinationRecommendationMapper mapper) {
        this.mapper = mapper;
    }

    public Destination random() {
        List<Destination> destinations = hot();
        if (destinations.isEmpty()) {
            throw new IllegalStateException("目的地数据为空");
        }
        return destinations.get(ThreadLocalRandom.current().nextInt(destinations.size()));
    }

    public List<Destination> hot() {
        return mapper.selectList(new LambdaQueryWrapper<DestinationRecommendation>()
                        .orderByDesc(DestinationRecommendation::getPopularity)
                        .last("limit 12"))
                .stream()
                .map(item -> new Destination(item.getStation(), item.getTag(), item.getTrainNo(), item.getReason()))
                .toList();
    }

    public record Destination(String station, String tag, String trainNo, String reason) {
    }
}
