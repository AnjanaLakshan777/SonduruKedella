package com.sondurukedella.activity.mapper;

import com.sondurukedella.activity.dto.ActivityResponse;
import com.sondurukedella.activity.entity.ActivityEntry;
import org.springframework.stereotype.Component;

@Component
public class ActivityMapper {

    public ActivityResponse toResponse(ActivityEntry entry) {
        return new ActivityResponse(entry.getId(), entry.getProjectId(), entry.getActivityDate(), entry.getKind(), entry.getMessage(),
                entry.getCreatedAt(), entry.getCreatedBy());
    }
}
