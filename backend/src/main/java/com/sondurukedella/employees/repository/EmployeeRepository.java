package com.sondurukedella.employees.repository;

import com.sondurukedella.employees.entity.Employee;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    // SELECT ... FOR UPDATE. Payroll takes this before freezing or paying any of the worker's weeks, so
    // two payments (or a payment and an advance recovery) for one worker never run side by side.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findByIdForUpdate(Long id);
}
