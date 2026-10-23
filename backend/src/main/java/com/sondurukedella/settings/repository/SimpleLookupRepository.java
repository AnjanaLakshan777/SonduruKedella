package com.sondurukedella.settings.repository;

import com.sondurukedella.settings.entity.SimpleLookup;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/** Shared queries for the editable Settings lists. */
@NoRepositoryBean
public interface SimpleLookupRepository<E extends SimpleLookup> extends JpaRepository<E, Long> {

    List<E> findByActiveTrueOrderBySortOrderAscIdAsc();

    Optional<E> findByNameIgnoreCase(String name);

    @Query("select coalesce(max(e.sortOrder), 0) from #{#entityName} e")
    int findMaxSortOrder();

    /** Locks the active entries so two concurrent removals cannot both remove the last one. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from #{#entityName} e where e.active = true")
    List<E> findActiveForUpdate();
}
