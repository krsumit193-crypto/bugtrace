import { NavLink, Outlet } from 'react-router-dom';

const links = [
  { to: '/', label: 'Overview', icon: 'overview', end: true },
  { to: '/upload', label: 'Upload logs', icon: 'upload' },
  { to: '/incidents', label: 'Incidents', icon: 'incidents' },
  { to: '/entries', label: 'Log entries', icon: 'search' },
  { to: '/uploads', label: 'Upload history', icon: 'history' },
];

function Icon({ name }) {
  const paths = {
    overview: 'M4 13h6V4H4v9Zm0 7h6v-5H4v5Zm10 0h6v-9h-6v9Zm0-16v5h6V4h-6Z',
    upload: 'M12 16V4m0 0L7 9m5-5 5 5M5 20h14',
    incidents: 'M12 9v4m0 4h.01M10.3 4.2 2.9 17a2 2 0 0 0 1.7 3h14.8a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0Z',
    history: 'M12 8v4l3 2m6-2a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
    search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm10 2-4.3-4.3',
  };
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="nav-icon">
      <path
        d={paths[name]}
        fill={name === 'overview' ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export default function Layout() {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">BT</span>
          <span>
            <strong>BugTrace</strong>
            <small>Smart Error Log Analyzer</small>
          </span>
        </div>

        <nav className="nav" aria-label="Main navigation">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
            >
              <Icon name={link.icon} />
              {link.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <p>Java 21 · Spring Boot · MySQL</p>
          <p>Real uploaded logs only</p>
        </div>
      </aside>

      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
