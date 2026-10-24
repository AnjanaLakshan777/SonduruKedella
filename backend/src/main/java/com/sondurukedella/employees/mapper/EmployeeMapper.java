package com.sondurukedella.employees.mapper;

import com.sondurukedella.common.money.Money;
import com.sondurukedella.employees.dto.AttendanceResponse;
import com.sondurukedella.employees.dto.EmployeeResponse;
import com.sondurukedella.employees.dto.OvertimeResponse;
import com.sondurukedella.employees.entity.Attendance;
import com.sondurukedella.employees.entity.Employee;
import com.sondurukedella.employees.entity.OvertimeEntry;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public EmployeeResponse toResponse(Employee e) {
        return new EmployeeResponse(e.getId(), e.getName(), e.getRole(), e.getPhone(), e.getAddress(), e.getDailyWage(),
                e.getOvertimeRate(), e.getStatus(), e.getJoinedOn(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }

    public AttendanceResponse toResponse(Attendance a, String employeeName) {
        return new AttendanceResponse(a.getId(), a.getEmployeeId(), employeeName, a.getWorkDate(), a.getStatus(),
                a.getProjectId(), a.getNotes(), a.getVersion());
    }

    public OvertimeResponse toResponse(OvertimeEntry o, String employeeName) {
        return new OvertimeResponse(o.getId(), o.getEmployeeId(), employeeName, o.getWorkDate(), o.getProjectId(), o.getHours(),
                o.getHourlyRate(), Money.amount(o.getHours().multiply(o.getHourlyRate())), o.isApproved(), o.getRecordedBy(),
                o.getNotes(), o.getVersion());
    }
}
