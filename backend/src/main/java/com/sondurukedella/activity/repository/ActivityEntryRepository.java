package com.sondurukedella.activity.repository;

import com.sondurukedella.activity.entity.ActivityEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActivityEntryRepository extends JpaRepository<ActivityEntry, Long>, JpaSpecificationExecutor<ActivityEntry> {
}
