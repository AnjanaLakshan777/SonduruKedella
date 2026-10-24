package com.sondurukedella.agreements.service;

import com.sondurukedella.agreements.dto.AgreementResponse;

// replaced tells the controller whether to answer 200 (replaced) or 201 (first agreement)
public record AgreementUpload(AgreementResponse agreement, boolean replaced) {
}
