package com.sondurukedella.activity.repository;

import com.sondurukedella.activity.entity.ActivityEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityEntryRepository extends JpaRepository<ActivityEntry, Long> {
}
