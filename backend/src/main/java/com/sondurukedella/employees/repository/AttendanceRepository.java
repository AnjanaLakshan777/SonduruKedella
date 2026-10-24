package com.sondurukedella.employees.repository;

import com.sondurukedella.employees.entity.Attendance;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByWorkDate(LocalDate workDate);

    @Query("select a from Attendance a where a.workDate between :from and :to "
            + "and (:employeeId is null or a.employeeId = :employeeId) "
            + "and (:projectId is null or a.projectId = :projectId) order by a.workDate, a.employeeId")
    List<Attendance> search(LocalDate from, LocalDate to, Long employeeId, Long projectId);

    @Query("select distinct a.workDate as day, a.employeeId as employeeId from Attendance a where a.projectId = :projectId")
    List<EmployeeDay> daysOnProject(Long projectId);

    interface EmployeeDay {
        LocalDate getDay();

        Long getEmployeeId();
    }
}
