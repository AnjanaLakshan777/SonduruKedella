package com.sondurukedella.employees.repository;

import com.sondurukedella.employees.entity.OvertimeEntry;
import com.sondurukedella.employees.repository.AttendanceRepository.EmployeeDay;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OvertimeEntryRepository extends JpaRepository<OvertimeEntry, Long> {

    @Query("select o from OvertimeEntry o where o.workDate between :from and :to "
            + "and (:employeeId is null or o.employeeId = :employeeId) "
            + "and (:approved is null or o.approved = :approved) order by o.workDate, o.employeeId, o.id")
    List<OvertimeEntry> search(LocalDate from, LocalDate to, Long employeeId, Boolean approved);

    @Query("select distinct o.workDate as day, o.employeeId as employeeId from OvertimeEntry o "
            + "where o.projectId = :projectId and o.approved = true")
    List<EmployeeDay> approvedDaysOnProject(Long projectId);
}
