package com.sondurukedella.revenue.repository;

import com.sondurukedella.revenue.entity.Installment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    boolean existsByProjectId(Long projectId);
}
