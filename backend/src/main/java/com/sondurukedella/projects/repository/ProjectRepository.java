package com.sondurukedella.projects.repository;

import com.sondurukedella.projects.entity.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    @Query("select p.code from Project p where p.code like :prefix")
    List<String> findCodesStartingWith(String prefix);

    // Serialises code generation for one year until the transaction ends, so two people
    // creating projects at the same moment don't both pick PRJ-2026-009. Different years
    // don't block each other. Wrapped in a select because the function returns void.
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:namespace, :year)) AS l", nativeQuery = true)
    Integer lockCodeYear(int namespace, int year);
}
