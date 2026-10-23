package com.sondurukedella.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.settings.dto.CommissionPolicyResponse;
import com.sondurukedella.settings.dto.CommissionPolicyUpdateRequest;
import com.sondurukedella.settings.dto.CompanyProfileResponse;
import com.sondurukedella.settings.dto.CompanyProfileUpdateRequest;
import com.sondurukedella.settings.dto.WageSettingsUpdateRequest;
import com.sondurukedella.settings.entity.CommissionPolicy;
import com.sondurukedella.settings.entity.CompanyProfile;
import com.sondurukedella.settings.entity.Payday;
import com.sondurukedella.settings.entity.WageSettings;
import com.sondurukedella.settings.mapper.SettingsMapper;
import com.sondurukedella.settings.repository.CommissionPolicyRepository;
import com.sondurukedella.settings.repository.CompanyProfileRepository;
import com.sondurukedella.settings.repository.WageSettingsRepository;
import com.sondurukedella.support.TestEntities;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SingletonSettingsServiceTest {

    @Mock
    private CompanyProfileRepository companyRepository;

    @Mock
    private CommissionPolicyRepository commissionRepository;

    @Mock
    private WageSettingsRepository wageRepository;

    private final SettingsMapper mapper = new SettingsMapper();

    @Test
    void companyUpdateTrimsTextAndStoresBlankOptionalFieldsAsNull() {
        CompanyProfile profile = companyProfile(3L);
        when(companyRepository.findById(CompanyProfile.SINGLETON_ID)).thenReturn(Optional.of(profile));
        when(companyRepository.saveAndFlush(profile)).thenReturn(profile);

        CompanyProfileResponse response = new CompanyProfileService(companyRepository, mapper).update(
                new CompanyProfileUpdateRequest("  Sonduru Kedella ", " ", "Anuradhapura", "", null,
                        " PV 1 ", "Owner", 21, 3L));

        assertThat(response.name()).isEqualTo("Sonduru Kedella");
        assertThat(response.tagline()).isNull();
        assertThat(response.phone()).isNull();
        assertThat(response.registration()).isEqualTo("PV 1");
        assertThat(response.supplierCreditDays()).isEqualTo(21);
        assertThat(response.financialYearStartMonth()).isEqualTo(4);
    }

    @Test
    void companyUpdateWithAStaleVersionIsAConflict() {
        CompanyProfile profile = companyProfile(5L);
        when(companyRepository.findById(CompanyProfile.SINGLETON_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> new CompanyProfileService(companyRepository, mapper).update(
                new CompanyProfileUpdateRequest("Name", null, null, null, null, null, null, 14, 4L)))
                .isInstanceOf(ConflictException.class);
        verify(companyRepository, never()).saveAndFlush(any());
    }

    @Test
    void commissionPolicyReportsTheCompanyShareAsHundredMinusThePool() {
        CommissionPolicy policy = TestEntities.create(CommissionPolicy.class, Map.of(
                "id", CommissionPolicy.SINGLETON_ID,
                "designCommissionPoolPct", new BigDecimal("20.0000"),
                "version", 0L));
        when(commissionRepository.findById(CommissionPolicy.SINGLETON_ID)).thenReturn(Optional.of(policy));
        when(commissionRepository.saveAndFlush(policy)).thenReturn(policy);

        CommissionPolicyResponse response = new CommissionPolicyService(commissionRepository, mapper).update(
                new CommissionPolicyUpdateRequest(new BigDecimal("22.5"), new BigDecimal("5"), 0L));

        assertThat(response.designCommissionPool()).isEqualByComparingTo("22.5");
        assertThat(response.designCompanyShare()).isEqualByComparingTo("77.5");
        assertThat(response.constructionDefaultRate()).isEqualByComparingTo("5");
    }

    @Test
    void commissionPolicyWarningThresholdCanBeCleared() {
        CommissionPolicy policy = TestEntities.create(CommissionPolicy.class, Map.of(
                "id", CommissionPolicy.SINGLETON_ID,
                "designCommissionPoolPct", new BigDecimal("20.0000"),
                "constructionWarningPct", new BigDecimal("6.0000"),
                "version", 2L));
        when(commissionRepository.findById(CommissionPolicy.SINGLETON_ID)).thenReturn(Optional.of(policy));
        when(commissionRepository.saveAndFlush(policy)).thenReturn(policy);

        new CommissionPolicyService(commissionRepository, mapper)
                .update(new CommissionPolicyUpdateRequest(new BigDecimal("20"), null, 2L));

        assertThat(policy.getConstructionWarningPct()).isNull();
    }

    @Test
    void wageUpdateWithAStaleVersionIsAConflict() {
        WageSettings wages = TestEntities.create(WageSettings.class, Map.of(
                "id", WageSettings.SINGLETON_ID,
                "defaultDailyWage", new BigDecimal("3500.00"),
                "hoursPerDay", new BigDecimal("8.00"),
                "overtimeMultiplier", new BigDecimal("1.50"),
                "payday", Payday.SATURDAY,
                "version", 1L));
        when(wageRepository.findById(WageSettings.SINGLETON_ID)).thenReturn(Optional.of(wages));

        assertThatThrownBy(() -> new WageSettingsService(wageRepository, mapper).update(new WageSettingsUpdateRequest(
                new BigDecimal("4000"), new BigDecimal("8"), new BigDecimal("1.5"), Payday.FRIDAY, 0L)))
                .isInstanceOf(ConflictException.class);
        assertThat(wages.getPayday()).isEqualTo(Payday.SATURDAY);
    }

    private static CompanyProfile companyProfile(long version) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("id", CompanyProfile.SINGLETON_ID);
        fields.put("name", "Sonduru Kedella");
        fields.put("financialYearStartMonth", (short) 4);
        fields.put("supplierCreditDays", 14);
        fields.put("version", version);
        return TestEntities.create(CompanyProfile.class, fields);
    }
}
