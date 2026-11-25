import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api } from '../api';
import { SeverityBadge, StatusBadge } from '../components/Badges';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/States';
import StatCard from '../components/StatCard';
import useAsync from '../hooks/useAsync';
import { formatDate, formatDateTime, formatDuration, formatNumber } from '../utils';

const RANGES = [7, 14, 30, 90];

const COLORS = {
  FATAL: '#b42318',
  ERROR: '#e5484d',
  WARN: '#f79009',
  INFO: '#0d9488',
  DEBUG: '#667085',
  UNKNOWN: '#98a2b3',
};

function MiniIncidentList({ items, emptyMessage }) {
  if (!items.length) {
    return <p className="muted">{emptyMessage}</p>;
  }
  return (
    <ul className="incident-mini-list">
      {items.map((item) => (
        <li key={item.id}>
          <Link to={`/incidents/${item.id}`} className="incident-mini-link">
            <span className="incident-mini-title">{item.title}</span>
            <span className="incident-mini-meta">
              <SeverityBadge severity={item.severity} />
              <StatusBadge status={item.status} />
              <span className="count-pill">×{formatNumber(item.occurrenceCount)}</span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export default function DashboardPage() {
  const [rangeDays, setRangeDays] = useState(14);
  const { data, error, loading, reload } = useAsync(
    () => api.dashboard(rangeDays),
    [rangeDays],
  );

  if (loading) {
    return <LoadingState label="Loading dashboard metrics…" />;
  }
  if (error) {
    return <ErrorState message={error.message} onRetry={reload} />;
  }

  const stats = data;
  const isEmpty =
    stats.totalEntries === 0 && stats.uniqueIncidents === 0 && stats.totalUploads === 0;
  const trend = stats.errorsOverTime.map((point) => ({
    ...point,
    label: formatDate(point.date).replace(/,?\s*\d{4}$/, ''),
  }));
  const severity = stats.severityDistribution.filter((item) => item.count > 0);

  return (
    <div className="page">
      <PageHeader
        eyebrow="Overview"
        title="Dashboard"
        description="Every number below is computed from the logs you uploaded. Nothing is mocked."
        actions={
          <>
            <label className="range-select">
              Range
              <select
                value={rangeDays}
                onChange={(event) => setRangeDays(Number(event.target.value))}
              >
                {RANGES.map((days) => (
                  <option key={days} value={days}>Last {days} days</option>
                ))}
              </select>
            </label>
            <Link className="button button-primary" to="/upload">
              Upload a log file
            </Link>
          </>
        }
      />

      {isEmpty ? (
        <EmptyState
          title="No logs analyzed yet"
          message="Upload a .log or .txt file to see real incidents, severity breakdowns and trends here."
          action={
            <Link className="button button-primary" to="/upload">
              Upload your first log
            </Link>
          }
        />
      ) : null}

      <section className="stat-grid" aria-label="Key metrics">
        <StatCard label="Uploads" value={formatNumber(stats.totalUploads)} hint="Files analyzed" />
        <StatCard label="Log lines" value={formatNumber(stats.totalLines)} hint="Total lines read" />
        <StatCard
          label="Parsed entries"
          value={formatNumber(stats.parsedEntries)}
          hint={`${formatNumber(stats.malformedEntries)} malformed`}
        />
        <StatCard
          label="Unique incidents"
          value={formatNumber(stats.uniqueIncidents)}
          hint={`${formatNumber(stats.openIncidents)} still open`}
        />
        <StatCard
          label="ERROR + FATAL"
          value={formatNumber(stats.errorEntries + stats.fatalEntries)}
          hint={`${formatNumber(stats.errorEntries)} error · ${formatNumber(stats.fatalEntries)} fatal`}
        />
        <StatCard
          label={`Incidents (${stats.rangeDays}d)`}
          value={formatNumber(stats.incidentsInRange)}
          hint={`${formatNumber(stats.resolvedInRange)} resolved in range`}
        />
        <StatCard
          label="Mean time to resolve"
          value={formatDuration(stats.mttrMinutes)}
          hint={stats.mttrMinutes === null ? 'No resolutions in range' : 'First seen → resolved'}
        />
      </section>

      <section className="chart-grid">
        <article className="panel chart-panel">
          <h2>Errors over time</h2>
          <p className="panel-subtitle">Daily ERROR and FATAL entries (last {stats.rangeDays} days)</p>
          <div className="chart-body">
            {stats.errorEntries + stats.fatalEntries === 0 ? (
              <p className="muted">No ERROR or FATAL entries to plot.</p>
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={trend} margin={{ top: 8, right: 16, left: -16, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e4e7ec" />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
                  <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Line
                    type="monotone"
                    dataKey="count"
                    name="Errors"
                    stroke="#e5484d"
                    strokeWidth={2}
                    dot={{ r: 2 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>
        </article>

        <article className="panel chart-panel">
          <h2>Severity distribution</h2>
          <p className="panel-subtitle">All parsed log entries</p>
          <div className="chart-body">
            {severity.length === 0 ? (
              <p className="muted">No severities recorded yet.</p>
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie
                    data={severity}
                    dataKey="count"
                    nameKey="severity"
                    outerRadius={80}
                    label={(entry) => entry.severity}
                  >
                    {severity.map((item) => (
                      <Cell key={item.severity} fill={COLORS[item.severity] ?? COLORS.UNKNOWN} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            )}
          </div>
        </article>

        <article className="panel chart-panel">
          <h2>Errors by service</h2>
          <p className="panel-subtitle">Top services by ERROR + FATAL entries</p>
          <div className="chart-body">
            {stats.errorsByService.length === 0 ? (
              <p className="muted">No failing services recorded yet.</p>
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart
                  data={stats.errorsByService}
                  layout="vertical"
                  margin={{ top: 8, right: 16, left: 16, bottom: 0 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke="#e4e7ec" />
                  <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
                  <YAxis
                    type="category"
                    dataKey="serviceName"
                    width={110}
                    tick={{ fontSize: 11 }}
                  />
                  <Tooltip />
                  <Bar dataKey="count" name="Entries" fill="#e5484d" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </article>

        <article className="panel">
          <h2>Top repeated errors</h2>
          <p className="panel-subtitle">Highest occurrence counts across all uploads</p>
          <MiniIncidentList
            items={stats.topRepeatedErrors}
            emptyMessage="No repeated errors found yet."
          />
        </article>

        <article className="panel">
          <h2>Recent incidents</h2>
          <p className="panel-subtitle">Most recently seen problems</p>
          <MiniIncidentList items={stats.recentIncidents} emptyMessage="No incidents found yet." />
        </article>
      </section>

      {stats.recentIncidents[0]?.lastSeen ? (
        <p className="footer-note">
          Most recent incident seen at {formatDateTime(stats.recentIncidents[0].lastSeen)}
        </p>
      ) : null}
    </div>
  );
}
