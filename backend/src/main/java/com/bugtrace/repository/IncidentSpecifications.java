package com.bugtrace.repository;

import com.bugtrace.entity.Incident;
import com.bugtrace.entity.IncidentTag;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dynamic, parameterized filters for the incident list.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    public static Specification<Incident> filter(String query, Severity severity,
                                                 IncidentStatus status,
                                                 Instant from, Instant toExclusive) {
        return filter(query, severity, status, null, null, null, from, toExclusive);
    }

    public static Specification<Incident> filter(String query, Severity severity,
                                                 IncidentStatus status, String tag,
                                                 Instant from, Instant toExclusive) {
        return filter(query, severity, status, tag, null, null, from, toExclusive);
    }

    /**
     * @param snoozed {@code TRUE} keeps only incidents muted at {@code now},
     *                {@code FALSE} keeps only active ones, {@code null} keeps both
     * @param now     reference instant for snooze comparisons
     */
    public static Specification<Incident> filter(String query, Severity severity,
                                                 IncidentStatus status, String tag,
                                                 Boolean snoozed, Instant now,
                                                 Instant from, Instant toExclusive) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sampleMessage")), pattern, '\\'),
                        cb.like(cb.lower(root.get("serviceName")), pattern, '\\')
                ));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (tag != null && !tag.isBlank()) {
                // Correlated subquery keeps the page query free of duplicate rows
                // (a join on incident_tag could return one incident several times).
                Subquery<Long> tagged = criteriaQuery.subquery(Long.class);
                Root<IncidentTag> tagRoot = tagged.from(IncidentTag.class);
                tagged.select(tagRoot.get("incidentId"))
                        .where(cb.equal(tagRoot.get("tag"), tag.strip().toLowerCase(Locale.ROOT)));
                predicates.add(root.get("id").in(tagged));
            }
            if (snoozed != null) {
                Instant reference = now == null ? Instant.now() : now;
                if (snoozed) {
                    predicates.add(cb.greaterThan(root.get("snoozedUntil"), reference));
                } else {
                    // A past snooze has expired, so it counts as active again.
                    predicates.add(cb.or(
                            cb.isNull(root.get("snoozedUntil")),
                            cb.lessThanOrEqualTo(root.get("snoozedUntil"), reference)));
                }
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastSeen"), from));
            }
            if (toExclusive != null) {
                predicates.add(cb.lessThan(root.get("lastSeen"), toExclusive));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
