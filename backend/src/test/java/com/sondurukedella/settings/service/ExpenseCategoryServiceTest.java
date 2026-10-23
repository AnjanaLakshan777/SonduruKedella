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
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.settings.dto.ExpenseCategoryCreateRequest;
import com.sondurukedella.settings.dto.ExpenseCategoryResponse;
import com.sondurukedella.settings.entity.ExpenseCategory;
import com.sondurukedella.settings.entity.ExpenseGroup;
import com.sondurukedella.settings.mapper.SettingsMapper;
import com.sondurukedella.settings.repository.ExpenseCategoryRepository;
import com.sondurukedella.support.TestEntities;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExpenseCategoryServiceTest {

    @Mock
    private ExpenseCategoryRepository repository;

    private ExpenseCategoryService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseCategoryService(repository, new SettingsMapper());
    }

    @Test
    void addCreatesTrimmedCategoryAtTheEndOfItsGroup() {
        when(repository.findByExpenseGroupAndNameIgnoreCase(ExpenseGroup.COMPANY, "Rent")).thenReturn(Optional.empty());
        when(repository.findMaxSortOrder(ExpenseGroup.COMPANY)).thenReturn(4);
        when(repository.saveAndFlush(any(ExpenseCategory.class))).thenAnswer(call -> {
            ExpenseCategory saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 20L); // assigned by the database identity column
            return saved;
        });

        LookupAddResult<ExpenseCategoryResponse> result =
                service.add(new ExpenseCategoryCreateRequest(ExpenseGroup.COMPANY, "  Rent  "));

        assertThat(result.created()).isTrue();
        assertThat(result.item().name()).isEqualTo("Rent");
        assertThat(result.item().group()).isEqualTo(ExpenseGroup.COMPANY);
        assertThat(result.item().sortOrder()).isEqualTo(5);
        assertThat(result.item().locked()).isFalse();
        assertThat(result.item().systemGenerated()).isFalse();
    }

    @Test
    void addRejectsANameAlreadyInTheGroupIgnoringCase() {
        ExpenseCategory existing = category(7L, "Marketing", false, true);
        when(repository.findByExpenseGroupAndNameIgnoreCase(ExpenseGroup.COMPANY, "marketing"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.add(new ExpenseCategoryCreateRequest(ExpenseGroup.COMPANY, "marketing")))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void addReactivatesAPreviouslyRemovedCategory() {
        ExpenseCategory removed = category(7L, "Marketing", false, false);
        when(repository.findByExpenseGroupAndNameIgnoreCase(ExpenseGroup.COMPANY, "Marketing"))
                .thenReturn(Optional.of(removed));
        when(repository.saveAndFlush(removed)).thenReturn(removed);

        LookupAddResult<ExpenseCategoryResponse> result =
                service.add(new ExpenseCategoryCreateRequest(ExpenseGroup.COMPANY, "Marketing"));

        assertThat(result.created()).isFalse();
        assertThat(result.item().id()).isEqualTo(7L);
        assertThat(removed.isActive()).isTrue();
    }

    @Test
    void addRejectsANameLongerThanTheColumn() {
        String tooLong = "x".repeat(ExpenseCategory.NAME_MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.add(new ExpenseCategoryCreateRequest(ExpenseGroup.DESIGN, tooLong)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void lockedCategoriesCannotBeRemoved() {
        ExpenseCategory labour = category(4L, "Labour", true, true);
        when(repository.findById(4L)).thenReturn(Optional.of(labour));

        assertThatThrownBy(() -> service.deactivate(4L)).isInstanceOf(BusinessRuleException.class);
        assertThat(labour.isActive()).isTrue();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void removingAnUnlockedCategoryDeactivatesIt() {
        ExpenseCategory water = category(9L, "Water", false, true);
        when(repository.findById(9L)).thenReturn(Optional.of(water));

        service.deactivate(9L);

        assertThat(water.isActive()).isFalse();
        verify(repository).saveAndFlush(water);
    }

    @Test
    void removingAnUnknownCategoryIsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(99L)).isInstanceOf(NotFoundException.class);
    }

    private static ExpenseCategory category(long id, String name, boolean locked, boolean active) {
        return TestEntities.create(ExpenseCategory.class, Map.of(
                "id", id,
                "expenseGroup", ExpenseGroup.COMPANY,
                "name", name,
                "locked", locked,
                "systemGenerated", locked,
                "active", active,
                "sortOrder", 1));
    }
}
