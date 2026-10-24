package com.sondurukedella.settings.service;

import com.sondurukedella.common.exception.BusinessRuleException;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.settings.dto.LookupItemResponse;
import com.sondurukedella.settings.entity.SimpleLookup;
import com.sondurukedella.settings.mapper.SettingsMapper;
import com.sondurukedella.settings.repository.SimpleLookupRepository;
import com.sondurukedella.settings.validation.SettingsText;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rules shared by the units and payment-methods lists, matching the Settings page:
 * names are unique ignoring case, removed entries are deactivated (history keeps referring to them),
 * re-adding a removed name reactivates it, and at least one active entry must remain.
 */
public abstract class SimpleLookupService<E extends SimpleLookup> {

    private final SimpleLookupRepository<E> repository;
    private final SettingsMapper mapper;
    private final BiFunction<String, Integer, E> factory;
    private final int nameMaxLength;
    private final String label;

    protected SimpleLookupService(SimpleLookupRepository<E> repository, SettingsMapper mapper,
            BiFunction<String, Integer, E> factory, int nameMaxLength, String label) {
        this.repository = repository;
        this.mapper = mapper;
        this.factory = factory;
        this.nameMaxLength = nameMaxLength;
        this.label = label;
    }

    // Contract for other modules (Revenue). Includes removed entries: an old receipt still shows
    // the method it was paid by, even after that method is taken off the list.
    @Transactional(readOnly = true)
    public Map<Long, String> findNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream().collect(Collectors.toMap(SimpleLookup::getId, SimpleLookup::getName));
    }

    // New records may only use an entry that is still on the list.
    @Transactional(readOnly = true)
    public boolean isActive(long id) {
        return repository.findById(id).map(SimpleLookup::isActive).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<LookupItemResponse> listActive() {
        return repository.findByActiveTrueOrderBySortOrderAscIdAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public LookupAddResult<LookupItemResponse> add(String requestedName) {
        String name = SettingsText.requireName(requestedName, nameMaxLength);
        Optional<E> existing = repository.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            E item = existing.get();
            if (item.isActive()) {
                throw new ConflictException("\"" + name + "\" is already in the list.");
            }
            item.setName(name);
            item.setActive(true);
            return new LookupAddResult<>(mapper.toResponse(repository.saveAndFlush(item)), false);
        }
        E created = factory.apply(name, repository.findMaxSortOrder() + 1);
        return new LookupAddResult<>(mapper.toResponse(repository.saveAndFlush(created)), true);
    }

    @Transactional
    public void deactivate(long id) {
        List<E> active = repository.findActiveForUpdate();
        E item = repository.findById(id).orElseThrow(() -> new NotFoundException(label + " not found."));
        if (!item.isActive()) {
            return;
        }
        if (active.size() <= 1) {
            throw new BusinessRuleException("Keep at least one " + label.toLowerCase() + ".");
        }
        item.setActive(false);
        repository.saveAndFlush(item);
    }
}
