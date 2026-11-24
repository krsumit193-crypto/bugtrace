package com.bugtrace.repository;

import com.bugtrace.entity.Incident;
import com.bugtrace.entity.LogEntry;
import com.bugtrace.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Entry-level queries used by incident details and dashboard aggregates.
 */
public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {

    @EntityGraph(attributePaths = "upload")
    Page<LogEntry> findByIncidentId(Long incidentId, Pageable pageable);

    long countBySeverity(Severity severity);

    long countBySeverityIn(Collection<Severity> severities);

    @Query("select e.severity, count(e) from LogEntry e group by e.severity")
    List<Object[]> countGroupedBySeverity();

    @Query("""
            select e.serviceName, count(e)
            from LogEntry e
            where e.severity in :severities
            group by e.serviceName
            order by count(e) desc
            """)
    List<Object[]> countByService(@Param("severities") Collection<Severity> severities, Pageable pageable);

    /**
     * Daily ERROR/FATAL counts. Uses the parsed timestamp when present and the
     * upload time otherwise. Native SQL keeps the GROUP BY simple and portable
     * between MySQL and H2 (MySQL mode).
     */
    @Query(value = """
            select cast(coalesce(le.logged_at, lu.uploaded_at) as date) as bucket_date,
                   count(*) as total
            from log_entry le
            join log_upload lu on lu.id = le.upload_id
            where le.severity in (:severities)
              and coalesce(le.logged_at, lu.uploaded_at) >= :from
            group by cast(coalesce(le.logged_at, lu.uploaded_at) as date)
            order by bucket_date
            """, nativeQuery = true)
    List<Object[]> countDaily(@Param("severities") Collection<String> severities,
                              @Param("from") Instant from);

    @Query("""
            select distinct i
            from LogEntry e
            join e.incident i
            where e.upload.id = :uploadId
            order by i.lastSeen desc
            """)
    List<Incident> findIncidentsByUploadId(@Param("uploadId") Long uploadId, Pageable pageable);
}
