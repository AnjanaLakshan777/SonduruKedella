package com.sondurukedella.employees.service;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.employees.dto.OvertimeApprovalRequest;
import com.sondurukedella.employees.dto.OvertimeCreateRequest;
import com.sondurukedella.employees.dto.OvertimeResponse;
import com.sondurukedella.employees.entity.Employee;
import com.sondurukedella.employees.entity.EmployeeStatus;
import com.sondurukedella.employees.entity.OvertimeEntry;
import com.sondurukedella.employees.mapper.EmployeeMapper;
import com.sondurukedella.employees.repository.EmployeeRepository;
import com.sondurukedella.employees.repository.OvertimeEntryRepository;
import com.sondurukedella.employees.validation.EmployeeRules;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.service.ProjectService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Overtime is entered unapproved and only approved hours reach the payroll. Entries aren't edited
// (the form has no edit); approval can be given or taken back until the week is paid.
@Service
@RequiredArgsConstructor
public class OvertimeService {

    private final OvertimeEntryRepository repository;
    private final EmployeeRepository employees;
    private final EmployeeMapper mapper;
    private final ProjectService projectService;

    @Transactional
    public OvertimeResponse create(OvertimeCreateRequest request) {
        // the lock payroll takes before freezing a week (see AttendanceService.saveDay)
        Employee employee = employees.findByIdForUpdate(request.employeeId())
                .orElseThrow(() -> new InvalidRequestException("The selected worker does not exist."));
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new InvalidRequestException(employee.getName() + " is inactive and can't take new overtime.");
        }
        if (request.projectId() != null) {
            requireOpenConstruction(request.projectId());
        }
        OvertimeEntry entry = new OvertimeEntry();
        entry.setEmployeeId(employee.getId());
        entry.setWorkDate(request.date());
        entry.setProjectId(request.projectId());
        entry.setHours(request.hours());
        // the form pre-fills the worker's rate; the supervisor may change it
        entry.setHourlyRate(request.hourlyRate() != null ? request.hourlyRate() : employee.getOvertimeRate());
        entry.setRecordedBy(request.recordedBy().strip());
        entry.setNotes(EmployeeRules.trimToNull(request.notes()));
        try {
            return mapper.toResponse(repository.saveAndFlush(entry), employee.getName());
        } catch (DataIntegrityViolationException ex) {
            throw EmployeeRules.frozenOr(ex, employee.getName(), request.date());
        }
    }

    @Transactional
    public OvertimeResponse setApproved(long id, OvertimeApprovalRequest request) {
        OvertimeEntry entry = repository.findById(id).orElseThrow(() -> new NotFoundException("Overtime entry not found."));
        ConflictException.requireCurrentVersion(request.version(), entry.getVersion());
        String name = employees.findByIdForUpdate(entry.getEmployeeId()).map(Employee::getName).orElse("");
        if (entry.isApproved() == request.approved()) {
            return mapper.toResponse(entry, name);
        }
        entry.setApproved(request.approved());
        try {
            return mapper.toResponse(repository.saveAndFlush(entry), name);
        } catch (DataIntegrityViolationException ex) {
            throw EmployeeRules.frozenOr(ex, name, entry.getWorkDate());
        }
    }

    @Transactional(readOnly = true)
    public List<OvertimeResponse> list(LocalDate from, LocalDate to, Long employeeId, Boolean approved) {
        AttendanceService.requireRange(from, to);
        List<OvertimeEntry> rows = repository.search(from, to, employeeId, approved);
        Map<Long, String> names = rows.isEmpty() ? Map.of() : employees.findAllById(
                rows.stream().map(OvertimeEntry::getEmployeeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        return rows.stream().map(o -> mapper.toResponse(o, names.get(o.getEmployeeId()))).toList();
    }

    // the overtime form lists construction projects that aren't completed or cancelled
    private void requireOpenConstruction(long projectId) {
        ProjectResponse project;
        try {
            project = projectService.get(projectId);
        } catch (NotFoundException ex) {
            throw new InvalidRequestException("The selected project does not exist.");
        }
        if (project.type() != ProjectType.CONSTRUCTION) {
            throw new InvalidRequestException("Overtime can only be booked to construction projects.");
        }
        if (project.status() == ProjectStatus.COMPLETED || project.status() == ProjectStatus.CANCELLED) {
            throw new InvalidRequestException("Project " + project.code() + " is " + project.status().label() + ".");
        }
    }
}
