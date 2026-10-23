package com.sondurukedella.settings.repository;

import com.sondurukedella.settings.entity.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Short> {
}
