package com.sondurukedella.agreements.mapper;

import com.sondurukedella.agreements.dto.AgreementResponse;
import com.sondurukedella.agreements.entity.ProjectAgreement;
import org.springframework.stereotype.Component;

@Component
public class AgreementMapper {

    public AgreementResponse toResponse(ProjectAgreement agreement) {
        return new AgreementResponse(
                agreement.getId(),
                agreement.getProjectId(),
                agreement.getOriginalFilename(),
                agreement.getContentType(),
                agreement.getSizeBytes(),
                agreement.getSha256(),
                agreement.getUploadedAt(),
                agreement.getVersion());
    }
}
