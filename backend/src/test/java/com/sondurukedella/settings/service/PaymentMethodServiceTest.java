package com.sondurukedella.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.settings.dto.LookupItemResponse;
import com.sondurukedella.settings.entity.PaymentMethod;
import com.sondurukedella.settings.mapper.SettingsMapper;
import com.sondurukedella.settings.repository.PaymentMethodRepository;
import com.sondurukedella.support.TestEntities;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers the shared list rules in {@link SimpleLookupService} through the payment methods list. */
@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {

    @Mock
    private PaymentMethodRepository repository;

    private PaymentMethodService service;

    @BeforeEach
    void setUp() {
        service = new PaymentMethodService(repository, new SettingsMapper());
    }

    @Test
    void theLastActivePaymentMethodCannotBeRemoved() {
        PaymentMethod cash = method(1L, "Cash", true);
        when(repository.findActiveForUpdate()).thenReturn(List.of(cash));
        when(repository.findById(1L)).thenReturn(Optional.of(cash));

        assertThatThrownBy(() -> service.deactivate(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Keep at least one");
        assertThat(cash.isActive()).isTrue();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void removingOneOfSeveralMethodsDeactivatesIt() {
        PaymentMethod cash = method(1L, "Cash", true);
        PaymentMethod cheque = method(3L, "Cheque", true);
        when(repository.findActiveForUpdate()).thenReturn(List.of(cash, cheque));
        when(repository.findById(3L)).thenReturn(Optional.of(cheque));

        service.deactivate(3L);

        assertThat(cheque.isActive()).isFalse();
        verify(repository).saveAndFlush(cheque);
    }

    @Test
    void removingAnAlreadyRemovedMethodChangesNothing() {
        PaymentMethod old = method(5L, "Old", false);
        when(repository.findActiveForUpdate()).thenReturn(List.of(method(1L, "Cash", true)));
        when(repository.findById(5L)).thenReturn(Optional.of(old));

        service.deactivate(5L);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void addRejectsDuplicatesIgnoringCase() {
        when(repository.findByNameIgnoreCase("cash")).thenReturn(Optional.of(method(1L, "Cash", true)));

        assertThatThrownBy(() -> service.add("cash")).isInstanceOf(ConflictException.class);
    }

    @Test
    void addRejectsBlankAndOverlongNames() {
        assertThatThrownBy(() -> service.add("   ")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.add("x".repeat(PaymentMethod.NAME_MAX_LENGTH + 1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void addAppendsANewMethod() {
        when(repository.findByNameIgnoreCase("Card")).thenReturn(Optional.empty());
        when(repository.findMaxSortOrder()).thenReturn(4);
        when(repository.saveAndFlush(any(PaymentMethod.class))).thenAnswer(call -> {
            PaymentMethod saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 20L); // assigned by the database identity column
            return saved;
        });

        LookupAddResult<LookupItemResponse> result = service.add(" Card ");

        assertThat(result.created()).isTrue();
        assertThat(result.item().name()).isEqualTo("Card");
        assertThat(result.item().sortOrder()).isEqualTo(5);
        assertThat(result.item().active()).isTrue();
    }

    private static PaymentMethod method(long id, String name, boolean active) {
        return TestEntities.create(PaymentMethod.class, Map.of("id", id, "name", name, "active", active, "sortOrder", 1));
    }
}
