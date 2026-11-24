package com.bugtrace.config;

import com.bugtrace.enums.Severity;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed application settings (prefix {@code app} in application.yml).
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();
    private Upload upload = new Upload();
    private LogSettings log = new LogSettings();
    private Dashboard dashboard = new Dashboard();

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    public LogSettings getLog() {
        return log;
    }

    public void setLog(LogSettings log) {
        this.log = log;
    }

    public Dashboard getDashboard() {
        return dashboard;
    }

    public void setDashboard(Dashboard dashboard) {
        this.dashboard = dashboard;
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Upload {
        private long maxFileSizeBytes = 10L * 1024 * 1024;
        private List<String> allowedExtensions = new ArrayList<>(List.of(".log", ".txt"));

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }
    }

    public static class LogSettings {
        private String timezone = "UTC";
        private List<Severity> incidentSeverities =
                new ArrayList<>(List.of(Severity.WARN, Severity.ERROR, Severity.FATAL));

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public List<Severity> getIncidentSeverities() {
            return incidentSeverities;
        }

        public void setIncidentSeverities(List<Severity> incidentSeverities) {
            this.incidentSeverities = incidentSeverities;
        }
    }

    public static class Dashboard {
        private int trendDays = 14;

        public int getTrendDays() {
            return trendDays;
        }

        public void setTrendDays(int trendDays) {
            this.trendDays = trendDays;
        }
    }
}
