package com.sondurukedella.inventory.repository;

import com.sondurukedella.inventory.entity.Material;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MaterialRepository extends JpaRepository<Material, Long>, JpaSpecificationExecutor<Material> {

    Optional<Material> findBySkuIgnoreCase(String sku);

    // SELECT ... FOR UPDATE. Every movement of a material takes this first, so receipts, issues,
    // returns and adjustments for it post one at a time and each reads the balance the last one left.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Material m where m.id = :id")
    Optional<Material> findByIdForUpdate(Long id);
}
