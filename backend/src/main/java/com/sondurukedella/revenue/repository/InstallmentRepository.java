package com.sondurukedella.revenue.repository;

import com.sondurukedella.revenue.entity.Installment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    boolean existsByProjectId(Long projectId);

    // id breaks ties so two installments due the same day always come back in the same order
    List<Installment> findByProjectIdOrderByDueDateAscIdAsc(Long projectId);
}
