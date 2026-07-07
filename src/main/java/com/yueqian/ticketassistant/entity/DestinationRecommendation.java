package com.yueqian.ticketassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("destination_recommendation")
public class DestinationRecommendation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String station;
    private String tag;
    private String trainNo;
    private String reason;
    private Integer popularity;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getTrainNo() { return trainNo; }
    public void setTrainNo(String trainNo) { this.trainNo = trainNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getPopularity() { return popularity; }
    public void setPopularity(Integer popularity) { this.popularity = popularity; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
