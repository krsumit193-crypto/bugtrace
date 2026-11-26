package com.bugtrace.repository;

import com.bugtrace.entity.Incident;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Incident persistence. Dynamic filtering is handled with Specifications so
 * every query remains parameterized.
 */
public interface IncidentRepository extends JpaRepository<Incident, Long>,
        JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByFingerprint(String fingerprint);

    long countByStatus(IncidentStatus status);

    long countBySnoozedUntilAfter(Instant now);

    long countByLastSeenGreaterThanEqual(Instant from);

    List<Incident> findByStatusAndResolvedAtGreaterThanEqual(IncidentStatus status, Instant from);

    List<Incident> findAllByOrderByOccurrenceCountDesc(Pageable pageable);

    List<Incident> findAllByOrderByLastSeenDesc(Pageable pageable);

    List<Incident> findBySeverityAndIdNotOrderByOccurrenceCountDesc(
            Severity severity, Long id, Pageable pageable);
}
