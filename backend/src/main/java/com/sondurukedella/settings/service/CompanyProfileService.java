package com.sondurukedella.settings.service;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.settings.dto.CompanyProfileResponse;
import com.sondurukedella.settings.dto.CompanyProfileUpdateRequest;
import com.sondurukedella.settings.entity.CompanyProfile;
import com.sondurukedella.settings.mapper.SettingsMapper;
import com.sondurukedella.settings.repository.CompanyProfileRepository;
import com.sondurukedella.settings.validation.SettingsText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyProfileService {

    private final CompanyProfileRepository repository;
    private final SettingsMapper mapper;

    @Transactional(readOnly = true)
    public CompanyProfileResponse get() {
        return mapper.toResponse(load());
    }

    @Transactional
    public CompanyProfileResponse update(CompanyProfileUpdateRequest request) {
        CompanyProfile profile = load();
        ConflictException.requireCurrentVersion(request.version(), profile.getVersion());
        profile.setName(request.name().strip());
        profile.setTagline(SettingsText.trimToNull(request.tagline()));
        profile.setAddress(SettingsText.trimToNull(request.address()));
        profile.setPhone(SettingsText.trimToNull(request.phone()));
        profile.setEmail(SettingsText.trimToNull(request.email()));
        profile.setRegistrationNo(SettingsText.trimToNull(request.registration()));
        profile.setOwnerName(SettingsText.trimToNull(request.owner()));
        profile.setSupplierCreditDays(request.supplierCreditDays());
        return mapper.toResponse(repository.saveAndFlush(profile));
    }

    private CompanyProfile load() {
        return repository.findById(CompanyProfile.SINGLETON_ID)
                .orElseThrow(() -> new NotFoundException("Company profile is missing."));
    }
}
