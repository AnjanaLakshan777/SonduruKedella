package com.sondurukedella.settings.service;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.settings.dto.CommissionPolicyResponse;
import com.sondurukedella.settings.dto.CommissionPolicyUpdateRequest;
import com.sondurukedella.settings.entity.CommissionPolicy;
import com.sondurukedella.settings.mapper.SettingsMapper;
import com.sondurukedella.settings.repository.CommissionPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The policy values only drive warnings; they never change recipients' rates. */
@Service
@RequiredArgsConstructor
public class CommissionPolicyService {

    private final CommissionPolicyRepository repository;
    private final SettingsMapper mapper;

    @Transactional(readOnly = true)
    public CommissionPolicyResponse get() {
        return mapper.toResponse(load());
    }

    @Transactional
    public CommissionPolicyResponse update(CommissionPolicyUpdateRequest request) {
        CommissionPolicy policy = load();
        ConflictException.requireCurrentVersion(request.version(), policy.getVersion());
        policy.setDesignCommissionPoolPct(request.designCommissionPool());
        policy.setConstructionWarningPct(request.constructionDefaultRate());
        return mapper.toResponse(repository.saveAndFlush(policy));
    }

    private CommissionPolicy load() {
        return repository.findById(CommissionPolicy.SINGLETON_ID)
                .orElseThrow(() -> new NotFoundException("Commission policy is missing."));
    }
}
