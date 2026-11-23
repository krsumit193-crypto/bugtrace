package com.bugtrace.repository;

import com.bugtrace.entity.Incident;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

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
