package com.bugtrace.repository;

import com.bugtrace.entity.IncidentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface IncidentTagRepository extends JpaRepository<IncidentTag, Long> {

    List<IncidentTag> findByIncidentIdOrderByTagAsc(Long incidentId);

    List<IncidentTag> findByIncidentIdInOrderByTagAsc(Collection<Long> incidentIds);

    @Query("select distinct t.tag from IncidentTag t order by t.tag asc")
    List<String> findDistinctTags();
}
