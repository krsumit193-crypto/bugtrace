-- Snooze support: temporarily mute an incident so it drops out of the active
-- queue. A NULL value or a timestamp in the past means the incident is active.
-- The scheduler is not involved: every query compares snoozed_until with the
-- current time, so an expired snooze wakes up on its own.

ALTER TABLE incident ADD COLUMN snoozed_until DATETIME(6) NULL;
CREATE INDEX idx_incident_snoozed_until ON incident (snoozed_until);
