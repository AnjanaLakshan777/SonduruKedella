package com.sondurukedella.settings.service;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.settings.dto.WageSettingsResponse;
import com.sondurukedella.settings.dto.WageSettingsUpdateRequest;
import com.sondurukedella.settings.entity.WageSettings;
import com.sondurukedella.settings.mapper.SettingsMapper;
import com.sondurukedella.settings.repository.WageSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Defaults for new employees; each worker keeps their own daily wage and overtime rate. */
@Service
@RequiredArgsConstructor
public class WageSettingsService {

    private final WageSettingsRepository repository;
    private final SettingsMapper mapper;

    @Transactional(readOnly = true)
    public WageSettingsResponse get() {
        return mapper.toResponse(load());
    }

    @Transactional
    public WageSettingsResponse update(WageSettingsUpdateRequest request) {
        WageSettings wages = load();
        ConflictException.requireCurrentVersion(request.version(), wages.getVersion());
        wages.setDefaultDailyWage(request.defaultDailyRate());
        wages.setHoursPerDay(request.hoursPerDay());
        wages.setOvertimeMultiplier(request.overtimeMultiplier());
        wages.setPayday(request.payday());
        return mapper.toResponse(repository.saveAndFlush(wages));
    }

    private WageSettings load() {
        return repository.findById(WageSettings.SINGLETON_ID)
                .orElseThrow(() -> new NotFoundException("Wage settings are missing."));
    }
}
