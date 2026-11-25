package com.bugtrace.repository;

import com.bugtrace.entity.IncidentActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentActivityRepository extends JpaRepository<IncidentActivity, Long> {

    List<IncidentActivity> findByIncidentIdOrderByCreatedAtDescIdDesc(Long incidentId);
}
