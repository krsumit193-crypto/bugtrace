package com.bugtrace.repository;

import com.bugtrace.entity.IncidentNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentNoteRepository extends JpaRepository<IncidentNote, Long> {

    List<IncidentNote> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
