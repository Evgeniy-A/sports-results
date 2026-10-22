import { EventCatalogPage } from './pages/EventCatalogPage'
import { EventResultsPage } from './pages/EventResultsPage'
import { EventDetailPage } from './pages/EventDetailPage'
import { PublicLandingPage } from './pages/PublicLandingPage'
import { AdminApp } from './admin/AdminApp'
import { publicCatalogMode } from './utils/publicCatalog'
import './App.css'

function App() {
  if (window.location.pathname === '/admin' || window.location.pathname.startsWith('/admin/')) {
    return <AdminApp />
  }
  if (/^\/$/.test(window.location.pathname)) return <PublicLandingPage />
  const resultsMatch = window.location.pathname.match(/^\/events\/([^/]+)\/results\/?$/)
  const eventMatch = window.location.pathname.match(/^\/events\/([^/]+)\/?$/)
  const catalogMode = publicCatalogMode(window.location.pathname)

  if (resultsMatch) return <EventResultsPage slug={decodeURIComponent(resultsMatch[1])} />
  if (eventMatch) return <EventDetailPage slug={decodeURIComponent(eventMatch[1])} />
  if (catalogMode) return <EventCatalogPage mode={catalogMode} />
  return <PublicLandingPage />
}

export default App
