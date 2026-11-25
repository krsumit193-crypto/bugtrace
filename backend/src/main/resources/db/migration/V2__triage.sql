-- Triage workflow additions: tags, activity audit trail, resolution time.
--
-- resolved_at is set when an incident becomes RESOLVED and cleared when it is
-- reopened, so MTTR can be computed without a separate status-history table.

ALTER TABLE incident ADD COLUMN resolved_at DATETIME(6) NULL;
CREATE INDEX idx_incident_resolved_at ON incident (resolved_at);

CREATE TABLE incident_tag (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  tag VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_tag_incident FOREIGN KEY (incident_id)
    REFERENCES incident (id) ON DELETE CASCADE,
  CONSTRAINT uk_incident_tag UNIQUE (incident_id, tag),
  INDEX idx_tag_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE incident_activity (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  activity_type VARCHAR(32) NOT NULL,
  detail VARCHAR(500) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_activity_incident FOREIGN KEY (incident_id)
    REFERENCES incident (id) ON DELETE CASCADE,
  INDEX idx_activity_incident (incident_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
