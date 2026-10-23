package com.sondurukedella.settings.service;

import com.sondurukedella.settings.dto.RevenueCategoryResponse;
import com.sondurukedella.settings.entity.RevenueCategory;
import com.sondurukedella.settings.mapper.SettingsMapper;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read-only: the four revenue categories are fixed and cannot be added, renamed or removed. */
@Service
@RequiredArgsConstructor
public class RevenueCategoryService {

    private final SettingsMapper mapper;

    public List<RevenueCategoryResponse> list() {
        return Arrays.stream(RevenueCategory.values()).map(mapper::toResponse).toList();
    }
}
