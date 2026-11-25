package com.bugtrace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A short label attached to an incident (for example {@code database} or
 * {@code payment}). Tags are lowercase and unique per incident.
 */
@Entity
@Table(name = "incident_tag")
public class IncidentTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "tag", nullable = false, length = 32)
    private String tag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IncidentTag() {
    }

    public IncidentTag(Long incidentId, String tag, Instant createdAt) {
        this.incidentId = incidentId;
        this.tag = tag;
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

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
