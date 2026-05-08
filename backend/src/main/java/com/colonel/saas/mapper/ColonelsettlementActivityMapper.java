package com.colonel.saas.mapper;

import com.colonel.saas.entity.ColonelsettlementActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ColonelsettlementActivityMapper {

    long countLocalActivities();

    long countPage(@Param("status") Integer status, @Param("now") LocalDateTime now);

    List<ColonelsettlementActivity> selectPage(
            @Param("offset") long offset,
            @Param("limit") long limit,
            @Param("status") Integer status,
            @Param("now") LocalDateTime now
    );

    List<ColonelsettlementActivity> selectExportPage(
            @Param("offset") long offset,
            @Param("limit") long limit,
            @Param("activityName") String activityName,
            @Param("now") LocalDateTime now
    );

    void insertSeedActivity(
            @Param("id") UUID id,
            @Param("activityId") String activityId,
            @Param("activityName") String activityName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statusText") String statusText,
            @Param("createTime") LocalDateTime createTime
    );
}
