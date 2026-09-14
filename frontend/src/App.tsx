import { EventCatalogPage } from './pages/EventCatalogPage'
import { EventResultsPage } from './pages/EventResultsPage'
import { EventDetailPage } from './pages/EventDetailPage'
import { AdminApp } from './admin/AdminApp'
import './App.css'

function App() {
  if (window.location.pathname === '/admin' || window.location.pathname.startsWith('/admin/')) {
    return <AdminApp />
  }
  const resultsMatch = window.location.pathname.match(/^\/events\/([^/]+)\/results\/?$/)
  const eventMatch = window.location.pathname.match(/^\/events\/([^/]+)\/?$/)

  if (resultsMatch) return <EventResultsPage slug={decodeURIComponent(resultsMatch[1])} />
  if (eventMatch) return <EventDetailPage slug={decodeURIComponent(eventMatch[1])} />
  return <EventCatalogPage />
}

export default App
