package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementActivity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ColonelsettlementActivityService {

    private final List<ColonelsettlementActivity> mockActivities = new ArrayList<>();

    public ColonelsettlementActivityService() {
        initMockData();
    }

    public IPage<ColonelsettlementActivity> getPage(long page, long size, Integer status) {
        List<ColonelsettlementActivity> filtered = mockActivities.stream()
                .filter(activity -> status == null || status.equals(activity.getStatus()))
                .sorted(Comparator.comparing(ColonelsettlementActivity::getStartTime).reversed())
                .toList();

        long current = Math.max(page, 1);
        long pageSize = Math.max(size, 1);
        int fromIndex = (int) ((current - 1) * pageSize);
        int toIndex = Math.min(fromIndex + (int) pageSize, filtered.size());

        List<ColonelsettlementActivity> records = fromIndex >= filtered.size()
                ? List.of()
                : filtered.subList(fromIndex, toIndex);

        Page<ColonelsettlementActivity> result = new Page<>(current, pageSize);
        result.setTotal(filtered.size());
        result.setRecords(records);
        return result;
    }

    private void initMockData() {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 5; i++) {
            ColonelsettlementActivity activity = new ColonelsettlementActivity();
            activity.setId(UUID.nameUUIDFromBytes(("activity-" + i).getBytes()));
            activity.setName("测试活动-" + i);
            activity.setStartTime(now.minusDays(i * 2L));
            activity.setEndTime(now.plusDays(15L - i));
            activity.setStatus(i % 2 == 0 ? 0 : 1);
            activity.setCreateTime(now.minusDays(i * 2L));
            mockActivities.add(activity);
        }
    }
}
