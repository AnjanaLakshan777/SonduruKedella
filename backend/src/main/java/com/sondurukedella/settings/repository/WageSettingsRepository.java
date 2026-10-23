package com.sondurukedella.settings.repository;

import com.sondurukedella.settings.entity.WageSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WageSettingsRepository extends JpaRepository<WageSettings, Short> {
}
