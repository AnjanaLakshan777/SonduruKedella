package com.sondurukedella.employees.service;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.employees.dto.AttendanceDayRequest;
import com.sondurukedella.employees.dto.AttendanceResponse;
import com.sondurukedella.employees.dto.PayrollInputs;
import com.sondurukedella.employees.entity.Attendance;
import com.sondurukedella.employees.entity.Employee;
import com.sondurukedella.employees.entity.EmployeeStatus;
import com.sondurukedella.employees.mapper.EmployeeMapper;
import com.sondurukedella.employees.repository.AttendanceRepository;
import com.sondurukedella.employees.repository.EmployeeRepository;
import com.sondurukedella.employees.repository.OvertimeEntryRepository;
import com.sondurukedella.employees.validation.EmployeeRules;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.service.ProjectService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The daily attendance register. It also hands payroll the days and overtime it prices (payrollInputs),
// so payroll never reads our tables.
@Service
@RequiredArgsConstructor
public class AttendanceService {

    public static final int MAX_RANGE_DAYS = 93;
    // the attendance page offers only these, and only construction projects
    private static final Set<ProjectStatus> SITE_OPEN = EnumSet.of(ProjectStatus.IN_PROGRESS, ProjectStatus.ON_HOLD);

    private final AttendanceRepository repository;
    private final OvertimeEntryRepository overtime;
    private final EmployeeRepository employees;
    private final EmployeeMapper mapper;
    private final ProjectService projectService;
    private final Clock clock;

    @Transactional
    public List<AttendanceResponse> saveDay(LocalDate date, AttendanceDayRequest request) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new InvalidRequestException("Attendance can't be recorded for future dates.");
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new InvalidRequestException("Sunday is not a work day. Choose another date.");
        }
        Set<Long> seen = new HashSet<>();
        for (AttendanceDayRequest.Entry entry : request.entries()) {
            if (!seen.add(entry.employeeId())) {
                throw new InvalidRequestException("A worker appears twice on the register.");
            }
        }
        Map<Long, Attendance> existing = repository.findByWorkDate(date).stream()
                .collect(Collectors.toMap(Attendance::getEmployeeId, Function.identity()));
        // Lock every worker we may touch, in id order (no deadlocks), the same lock payroll takes before it
        // freezes a week: a day saved while a week is being paid either lands before the freeze reads the
        // week, or is refused by the trigger after it.
        Map<Long, Employee> byId = new HashMap<>();
        Stream.concat(seen.stream(), existing.keySet().stream()).distinct().sorted()
                .forEach(id -> employees.findByIdForUpdate(id).ifPresent(e -> byId.put(id, e)));
        Map<Long, ProjectResponse> projects = new HashMap<>();

        for (AttendanceDayRequest.Entry entry : request.entries()) {
            Employee employee = byId.get(entry.employeeId());
            if (employee == null) {
                throw new InvalidRequestException("The selected worker does not exist.");
            }
            Attendance row = existing.remove(entry.employeeId());
            String notes = EmployeeRules.trimToNull(entry.notes());
            if (row != null && row.getStatus() == entry.status() && Objects.equals(row.getProjectId(), entry.projectId())
                    && Objects.equals(row.getNotes(), notes)) {
                continue; // unchanged: not written, so a paid week can be re-saved as it is
            }
            if (employee.getStatus() != EmployeeStatus.ACTIVE) {
                throw new InvalidRequestException(employee.getName() + " is inactive and can't be marked.");
            }
            if (employee.getJoinedOn().isAfter(date)) {
                throw new InvalidRequestException(employee.getName() + " joined on " + employee.getJoinedOn() + ".");
            }
            if (entry.projectId() != null) {
                requireSite(projects.computeIfAbsent(entry.projectId(), this::project));
            }
            if (row == null) {
                row = new Attendance(employee.getId(), date);
            }
            row.setStatus(entry.status());
            row.setProject(entry.projectId());
            row.setNotes(notes);
            try {
                repository.saveAndFlush(row);
            } catch (DataIntegrityViolationException ex) {
                throw EmployeeRules.frozenOr(ex, employee.getName(), date);
            }
        }
        // whoever was marked before but isn't on the register now has no attendance that day
        for (Attendance gone : existing.values()) {
            try {
                repository.delete(gone);
                repository.flush();
            } catch (DataIntegrityViolationException ex) {
                throw EmployeeRules.frozenOr(ex, byId.get(gone.getEmployeeId()).getName(), date);
            }
        }
        return list(date, date, null, null);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> list(LocalDate from, LocalDate to, Long employeeId, Long projectId) {
        requireRange(from, to);
        List<Attendance> rows = repository.search(from, to, employeeId, projectId);
        Map<Long, String> names = names(rows.stream().map(Attendance::getEmployeeId).collect(Collectors.toSet()));
        return rows.stream().map(a -> mapper.toResponse(a, names.get(a.getEmployeeId()))).toList();
    }

    // For payroll: every day and overtime entry in [from, to], optionally for one worker.
    @Transactional(readOnly = true)
    public PayrollInputs payrollInputs(LocalDate from, LocalDate to, Long employeeId) {
        List<PayrollInputs.Day> days = repository.search(from, to, employeeId, null).stream()
                .map(a -> new PayrollInputs.Day(a.getEmployeeId(), a.getWorkDate(), a.getStatus(), a.getProjectId())).toList();
        List<PayrollInputs.Overtime> ot = overtime.search(from, to, employeeId, null).stream()
                .map(o -> new PayrollInputs.Overtime(o.getId(), o.getEmployeeId(), o.getWorkDate(), o.getProjectId(), o.getHours(),
                        o.getHourlyRate(), o.isApproved())).toList();
        return new PayrollInputs(days, ot);
    }

    // For payroll's project labour: which worker-weeks touched the project, through a day or approved overtime.
    @Transactional(readOnly = true)
    public List<WorkerWeek> workerWeeksOnProject(long projectId) {
        return Stream.concat(repository.daysOnProject(projectId).stream(), overtime.approvedDaysOnProject(projectId).stream())
                .map(d -> new WorkerWeek(d.getEmployeeId(), EmployeeRules.weekStart(d.getDay())))
                .distinct().sorted((a, b) -> a.weekStart().equals(b.weekStart())
                        ? Long.compare(a.employeeId(), b.employeeId()) : a.weekStart().compareTo(b.weekStart()))
                .toList();
    }

    static void requireRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("from must be on or before to.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new InvalidRequestException("Ask for at most " + MAX_RANGE_DAYS + " days at a time.");
        }
    }

    private ProjectResponse project(long id) {
        try {
            return projectService.get(id);
        } catch (NotFoundException ex) {
            throw new InvalidRequestException("The selected project does not exist.");
        }
    }

    private static void requireSite(ProjectResponse project) {
        if (project.type() != ProjectType.CONSTRUCTION) {
            throw new InvalidRequestException("Workers can only be marked on construction projects.");
        }
        if (!SITE_OPEN.contains(project.status())) {
            throw new InvalidRequestException("Project " + project.code() + " is " + project.status().label()
                    + "; attendance goes on projects in progress or on hold.");
        }
    }

    private Map<Long, String> names(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : employees.findAllById(ids).stream().collect(Collectors.toMap(Employee::getId, Employee::getName));
    }

    public record WorkerWeek(long employeeId, LocalDate weekStart) {
    }
}
