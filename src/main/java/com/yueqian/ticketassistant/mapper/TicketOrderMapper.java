package com.yueqian.ticketassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yueqian.ticketassistant.entity.TicketOrder;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface TicketOrderMapper extends BaseMapper<TicketOrder> {
    @Select("""
            select label, value from (
              select date_format(day_value, '%m-%d') as label, day_value, value from (
                select date(create_time) as day_value, count(*) as value
                from ticket_order
                where deleted = 0
                group by date(create_time)
                order by day_value desc
                limit 7
              ) recent
              limit 7
            ) t
            order by day_value
            """)
    List<Map<String, Object>> selectDailyOrderStats();

    @Select("""
            select concat(departure_station, '-', arrival_station) as label, count(*) as value
            from ticket_order
            where deleted = 0
            group by departure_station, arrival_station
            order by value desc
            limit 6
            """)
    List<Map<String, Object>> selectRouteRankingStats();
}
