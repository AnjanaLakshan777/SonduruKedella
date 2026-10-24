package com.sondurukedella.employees.controller;

import com.sondurukedella.employees.dto.AttendanceDayRequest;
import com.sondurukedella.employees.dto.AttendanceResponse;
import com.sondurukedella.employees.dto.OvertimeApprovalRequest;
import com.sondurukedella.employees.dto.OvertimeCreateRequest;
import com.sondurukedella.employees.dto.OvertimeResponse;
import com.sondurukedella.employees.service.AttendanceService;
import com.sondurukedella.employees.service.OvertimeService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// The daily inputs to payroll. Ranges are capped (AttendanceService.MAX_RANGE_DAYS) because both lists
// grow every day.
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendance;
    private final OvertimeService overtime;

    @GetMapping("/attendance")
    public List<AttendanceResponse> attendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long projectId) {
        return attendance.list(from, to, employeeId, projectId);
    }

    // the whole register for one day; returns what is saved for that day afterwards
    @PutMapping("/attendance/{date}")
    public List<AttendanceResponse> saveDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody AttendanceDayRequest request) {
        return attendance.saveDay(date, request);
    }

    @GetMapping("/overtime")
    public List<OvertimeResponse> overtime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Boolean approved) {
        return overtime.list(from, to, employeeId, approved);
    }

    @PostMapping("/overtime")
    @ResponseStatus(HttpStatus.CREATED)
    public OvertimeResponse recordOvertime(@Valid @RequestBody OvertimeCreateRequest request) {
        return overtime.create(request);
    }

    @PutMapping("/overtime/{id}/approval")
    public OvertimeResponse approve(@PathVariable long id, @Valid @RequestBody OvertimeApprovalRequest request) {
        return overtime.setApproved(id, request);
    }
}
