-- Triage ownership: an incident can be assigned to one person.
-- NULL means unassigned; the value is free text so teams can use names or handles.

ALTER TABLE incident ADD COLUMN assignee VARCHAR(100) NULL;
CREATE INDEX idx_incident_assignee ON incident (assignee);
