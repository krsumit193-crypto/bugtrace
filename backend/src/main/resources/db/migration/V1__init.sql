-- BugTrace initial schema (MySQL 8.x)
--
-- Note: Hibernate 6.6 maps Java boolean to MySQL BIT(1) (see MySQLDialect),
-- so the two boolean flags below use BIT(1) instead of BOOLEAN (TINYINT(1)).
-- This keeps `spring.jpa.hibernate.ddl-auto=validate` working against this schema.

CREATE TABLE log_upload (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  upload_uuid CHAR(36) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  file_size_bytes BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  total_lines INT NOT NULL DEFAULT 0,
  parsed_entries INT NOT NULL DEFAULT 0,
  malformed_entries INT NOT NULL DEFAULT 0,
  incident_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(500) NULL,
  uploaded_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL,
  CONSTRAINT uk_upload_uuid UNIQUE (upload_uuid),
  INDEX idx_upload_uploaded_at (uploaded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE incident (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  fingerprint CHAR(64) NOT NULL,
  title VARCHAR(500) NOT NULL,
  sample_message TEXT NOT NULL,
  service_name VARCHAR(150) NULL,
  severity VARCHAR(10) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  occurrence_count BIGINT NOT NULL DEFAULT 0,
  first_seen DATETIME(6) NOT NULL,
  last_seen DATETIME(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_incident_fingerprint UNIQUE (fingerprint),
  INDEX idx_incident_status (status),
  INDEX idx_incident_severity (severity),
  INDEX idx_incident_service (service_name),
  INDEX idx_incident_last_seen (last_seen),
  INDEX idx_incident_count (occurrence_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE log_entry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  upload_id BIGINT NOT NULL,
  incident_id BIGINT NULL,
  line_number INT NOT NULL,
  logged_at DATETIME(6) NULL,
  severity VARCHAR(10) NULL,
  severity_inferred BIT(1) NOT NULL DEFAULT b'0',
  service_name VARCHAR(150) NULL,
  logger_name VARCHAR(255) NULL,
  message TEXT NOT NULL,
  stack_trace MEDIUMTEXT NULL,
  raw_line TEXT NOT NULL,
  CONSTRAINT fk_entry_upload FOREIGN KEY (upload_id)
    REFERENCES log_upload (id) ON DELETE CASCADE,
  CONSTRAINT fk_entry_incident FOREIGN KEY (incident_id)
    REFERENCES incident (id) ON DELETE SET NULL,
  INDEX idx_entry_upload (upload_id),
  INDEX idx_entry_incident (incident_id),
  INDEX idx_entry_logged_at (logged_at),
  INDEX idx_entry_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE incident_note (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  note_text VARCHAR(2000) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_note_incident FOREIGN KEY (incident_id)
    REFERENCES incident (id) ON DELETE CASCADE,
  INDEX idx_note_incident (incident_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
