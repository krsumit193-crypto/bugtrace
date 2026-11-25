package com.bugtrace.entity;

import com.bugtrace.enums.IncidentActivityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One entry in an incident's audit trail. Activities are append-only and are
 * written for status changes, notes and tag edits.
 */
@Entity
@Table(name = "incident_activity")
public class IncidentActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 32)
    private IncidentActivityType type;

    @Column(name = "detail", nullable = false, length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IncidentActivity() {
    }

    public IncidentActivity(Long incidentId, IncidentActivityType type, String detail, Instant createdAt) {
        this.incidentId = incidentId;
        this.type = type;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public IncidentActivityType getType() {
        return type;
    }

    public void setType(IncidentActivityType type) {
        this.type = type;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
