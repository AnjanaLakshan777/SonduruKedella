package com.sondurukedella.settings.service;

import com.sondurukedella.settings.entity.MeasurementUnit;
import com.sondurukedella.settings.mapper.SettingsMapper;
import com.sondurukedella.settings.repository.MeasurementUnitRepository;
import org.springframework.stereotype.Service;

@Service
public class MeasurementUnitService extends SimpleLookupService<MeasurementUnit> {

    public MeasurementUnitService(MeasurementUnitRepository repository, SettingsMapper mapper) {
        super(repository, mapper, MeasurementUnit::new, MeasurementUnit.NAME_MAX_LENGTH, "Unit");
    }
}
