package com.sondurukedella.suppliers.service;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.settings.service.CompanyProfileService;
import com.sondurukedella.suppliers.dto.SupplierCreateRequest;
import com.sondurukedella.suppliers.dto.SupplierResponse;
import com.sondurukedella.suppliers.dto.SupplierStatusRequest;
import com.sondurukedella.suppliers.dto.SupplierUpdateRequest;
import com.sondurukedella.suppliers.entity.Supplier;
import com.sondurukedella.suppliers.mapper.SupplierMapper;
import com.sondurukedella.suppliers.repository.SupplierRepository;
import com.sondurukedella.suppliers.repository.SupplierSpecifications;
import com.sondurukedella.suppliers.validation.SupplierText;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The supplier directory. No duplicate check: two suppliers may share a name or a phone, and nothing
// in the schema or the UI says otherwise. No delete: deactivate instead, so records that point at a
// supplier keep working.
@Service
@RequiredArgsConstructor
public class SupplierService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final Set<String> SORTABLE_PROPERTIES = Set.of("name", "creditDays", "createdAt", "id");

    private final SupplierRepository repository;
    private final SupplierMapper mapper;
    private final CompanyProfileService companyProfile;

    @Transactional
    public SupplierResponse create(SupplierCreateRequest request) {
        Supplier supplier = new Supplier();
        apply(supplier, request.name(), request.contactPerson(), request.phone(), request.email(), request.address(),
                request.supplies());
        // the dialog pre-fills this from Settings > Company, so do the same when it isn't sent
        supplier.setCreditDays(request.creditDays() != null ? request.creditDays() : companyProfile.get().supplierCreditDays());
        return mapper.toResponse(repository.saveAndFlush(supplier));
    }

    @Transactional
    public SupplierResponse update(long id, SupplierUpdateRequest request) {
        Supplier supplier = load(id);
        ConflictException.requireCurrentVersion(request.version(), supplier.getVersion());
        apply(supplier, request.name(), request.contactPerson(), request.phone(), request.email(), request.address(),
                request.supplies());
        supplier.setCreditDays(request.creditDays());
        return mapper.toResponse(repository.saveAndFlush(supplier));
    }

    // Only the flag changes; nothing that already references the supplier is touched.
    @Transactional
    public SupplierResponse setActive(long id, SupplierStatusRequest request) {
        Supplier supplier = load(id);
        ConflictException.requireCurrentVersion(request.version(), supplier.getVersion());
        if (supplier.isActive() == request.active()) {
            return mapper.toResponse(supplier);
        }
        supplier.setActive(request.active());
        return mapper.toResponse(repository.saveAndFlush(supplier));
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> search(String query, Boolean active, Pageable pageable) {
        return PageResponse.of(repository.findAll(SupplierSpecifications.search(query, active), stablePage(pageable)),
                mapper::toResponse);
    }

    // The contract the expenses module uses instead of our repository. Inactive suppliers are
    // included: an expense that already names one must still read and save.
    @Transactional(readOnly = true)
    public Optional<SupplierResponse> find(long id) {
        return repository.findById(id).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> findNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream().collect(Collectors.toMap(Supplier::getId, Supplier::getName));
    }

    private static void apply(Supplier supplier, String name, String contactPerson, String phone, String email,
            String address, String supplies) {
        supplier.setName(name.strip());
        supplier.setContactPerson(SupplierText.trimToNull(contactPerson));
        supplier.setPhone(phone.strip());
        supplier.setEmail(SupplierText.trimToNull(email));
        supplier.setAddress(SupplierText.trimToNull(address));
        supplier.setSupplies(SupplierText.trimToNull(supplies));
    }

    private Supplier load(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Supplier not found."));
    }

    // name A-Z by default; id breaks ties so suppliers with the same name keep their order across pages
    private static Pageable stablePage(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Suppliers cannot be sorted by \"" + order.getProperty()
                        + "\". Use one of: name, creditDays, createdAt, id.");
            }
        }
        if (sort.isUnsorted()) {
            sort = Sort.by("name");
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
