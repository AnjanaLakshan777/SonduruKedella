package com.sondurukedella.employees.repository;

import com.sondurukedella.employees.entity.WorkHoliday;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkHolidayRepository extends JpaRepository<WorkHoliday, Long> {

    Optional<WorkHoliday> findByDate(LocalDate date);

    boolean existsByDate(LocalDate date);

    List<WorkHoliday> findByDateBetweenOrderByDate(LocalDate from, LocalDate to);
}
