import { Link, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import { PageHeader } from './components/States';
import DashboardPage from './pages/DashboardPage';
import IncidentDetailPage from './pages/IncidentDetailPage';
import IncidentsPage from './pages/IncidentsPage';
import LogEntriesPage from './pages/LogEntriesPage';
import UploadDetailPage from './pages/UploadDetailPage';
import UploadHistoryPage from './pages/UploadHistoryPage';
import UploadPage from './pages/UploadPage';

function NotFoundPage() {
  return (
    <div className="page">
      <PageHeader title="Page not found" description="That route does not exist." />
      <Link className="button button-primary" to="/">Go to dashboard</Link>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="upload" element={<UploadPage />} />
        <Route path="incidents" element={<IncidentsPage />} />
        <Route path="incidents/:id" element={<IncidentDetailPage />} />
        <Route path="entries" element={<LogEntriesPage />} />
        <Route path="uploads" element={<UploadHistoryPage />} />
        <Route path="uploads/:id" element={<UploadDetailPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
