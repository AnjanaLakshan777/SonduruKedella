package com.sondurukedella.employees.service;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.employees.dto.EmployeeCreateRequest;
import com.sondurukedella.employees.dto.EmployeeResponse;
import com.sondurukedella.employees.dto.EmployeeStatusRequest;
import com.sondurukedella.employees.dto.EmployeeUpdateRequest;
import com.sondurukedella.employees.entity.Employee;
import com.sondurukedella.employees.entity.EmployeeStatus;
import com.sondurukedella.employees.mapper.EmployeeMapper;
import com.sondurukedella.employees.repository.EmployeeRepository;
import com.sondurukedella.employees.repository.EmployeeSpecifications;
import com.sondurukedella.employees.validation.EmployeeRules;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// The worker register. No duplicate check: the schema has no staff number, and two workers can share a
// name (or a phone, in a family). No delete: attendance and paid weeks point at employees, so they are
// deactivated instead.
@Service
@RequiredArgsConstructor
public class EmployeeService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final Set<String> SORTABLE_PROPERTIES = Set.of("name", "role", "joinedOn", "dailyWage", "createdAt", "id");

    private final EmployeeRepository repository;
    private final EmployeeMapper mapper;

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        Employee employee = new Employee();
        apply(employee, request.name(), request.role(), request.phone(), request.address());
        employee.setDailyWage(request.dailyWage());
        employee.setOvertimeRate(request.overtimeRate());
        employee.setJoinedOn(request.joinedOn());
        if (request.status() != null) {
            employee.setStatus(request.status());
        }
        return mapper.toResponse(repository.saveAndFlush(employee));
    }

    @Transactional
    public EmployeeResponse update(long id, EmployeeUpdateRequest request) {
        Employee employee = load(id);
        ConflictException.requireCurrentVersion(request.version(), employee.getVersion());
        apply(employee, request.name(), request.role(), request.phone(), request.address());
        employee.setDailyWage(request.dailyWage());
        employee.setOvertimeRate(request.overtimeRate());
        employee.setJoinedOn(request.joinedOn());
        return mapper.toResponse(repository.saveAndFlush(employee));
    }

    @Transactional
    public EmployeeResponse setStatus(long id, EmployeeStatusRequest request) {
        Employee employee = load(id);
        ConflictException.requireCurrentVersion(request.version(), employee.getVersion());
        if (employee.getStatus() == request.status()) {
            return mapper.toResponse(employee);
        }
        employee.setStatus(request.status());
        return mapper.toResponse(repository.saveAndFlush(employee));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> search(String query, String role, EmployeeStatus status, Pageable pageable) {
        return PageResponse.of(repository.findAll(EmployeeSpecifications.search(query, role, status), stablePage(pageable)),
                mapper::toResponse);
    }

    // The contract payroll uses instead of our repository. Inactive workers are included: their
    // unpaid weeks and history still have to be read and paid.
    @Transactional(readOnly = true)
    public Optional<EmployeeResponse> find(long id) {
        return repository.findById(id).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Map<Long, EmployeeResponse> findAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream().map(mapper::toResponse)
                .collect(Collectors.toMap(EmployeeResponse::id, Function.identity()));
    }

    // Row lock on the worker, for payroll: every freeze, payment and advance for one worker runs one at
    // a time. MANDATORY because a lock outside the caller's transaction would be released at once.
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<EmployeeResponse> lockForPayroll(long id) {
        return repository.findByIdForUpdate(id).map(mapper::toResponse);
    }

    private static void apply(Employee employee, String name, String role, String phone, String address) {
        employee.setName(name.strip());
        employee.setRole(role.strip());
        employee.setPhone(EmployeeRules.trimToNull(phone));
        employee.setAddress(EmployeeRules.trimToNull(address));
    }

    Employee load(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Employee not found."));
    }

    // name A-Z by default; id breaks ties
    private static Pageable stablePage(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Employees cannot be sorted by \"" + order.getProperty()
                        + "\". Use one of: name, role, joinedOn, dailyWage, createdAt, id.");
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
