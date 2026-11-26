package com.bugtrace.repository;

import com.bugtrace.entity.LogEntry;
import com.bugtrace.enums.Severity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dynamic, parameterized filters for searching raw log entries across every
 * uploaded file.
 *
 * <p>Incident search answers "which problem occurred"; entry search answers
 * "which exact line said this". Both use LIKE patterns only through JPA
 * parameters, and LIKE wildcards supplied by the user are escaped.</p>
 */
public final class LogEntrySpecifications {

    private LogEntrySpecifications() {
    }

    /**
     * @param query       free text matched against message, raw line, service and logger
     * @param severity    exact level, or {@code null} for any
     * @param serviceName partial service match, or {@code null}
     * @param uploadId    exact upload id, or {@code null}
     * @param incidentId  exact incident id, or {@code null}
     * @param from        inclusive lower bound for the parsed timestamp
     * @param toExclusive exclusive upper bound for the parsed timestamp
     */
    public static Specification<LogEntry> search(String query, Severity severity,
                                                 String serviceName, Long uploadId,
                                                 Long incidentId,
                                                 Instant from, Instant toExclusive) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query != null && !query.isBlank()) {
                String pattern = "%" + IncidentSpecifications.escapeLike(
                        query.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("message")), pattern, '\\'),
                        cb.like(cb.lower(root.get("rawLine")), pattern, '\\'),
                        cb.like(cb.lower(root.get("serviceName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("loggerName")), pattern, '\\')
                ));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (serviceName != null && !serviceName.isBlank()) {
                String pattern = "%" + IncidentSpecifications.escapeLike(
                        serviceName.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("serviceName")), pattern, '\\'));
            }
            if (uploadId != null) {
                predicates.add(cb.equal(root.get("upload").get("id"), uploadId));
            }
            if (incidentId != null) {
                predicates.add(cb.equal(root.get("incident").get("id"), incidentId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("loggedAt"), from));
            }
            if (toExclusive != null) {
                predicates.add(cb.lessThan(root.get("loggedAt"), toExclusive));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
