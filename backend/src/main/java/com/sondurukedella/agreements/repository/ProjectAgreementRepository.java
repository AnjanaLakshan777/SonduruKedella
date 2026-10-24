package com.sondurukedella.agreements.repository;

import com.sondurukedella.agreements.entity.ProjectAgreement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectAgreementRepository extends JpaRepository<ProjectAgreement, Long> {

    // the current one; superseded rows have removed_at set
    Optional<ProjectAgreement> findByProjectIdAndRemovedAtIsNull(Long projectId);
}
