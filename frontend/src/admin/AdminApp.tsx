import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { createAdminApi } from './api'
import type { AdminCredentials } from './types'
import { AdminEventsPage } from './pages/AdminEventsPage'
import { AdminEventPage } from './pages/AdminEventPage'
import { AdminJournalPage } from './pages/AdminJournalPage'
import { AdminLink, navigateAdmin, useAdminLocation } from './router'
import { AdminNotice, Field } from './components/AdminUi'
import './admin.css'

export function AdminApp() {
  const [credentials, setCredentials] = useState<AdminCredentials | null>(null)
  if (!credentials) return <AdminLogin onAuthenticated={setCredentials} />
  return <AuthenticatedAdmin credentials={credentials} onLogout={() => setCredentials(null)} />
}

function AdminLogin({ onAuthenticated }: { onAuthenticated: (credentials: AdminCredentials) => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null)
    const credentials = { username, password }
    try { await createAdminApi(credentials).events({ page: 0, size: 1 }); onAuthenticated(credentials) }
    catch (reason) {
      setError(reason instanceof ApiError && reason.status === 401 ? 'Неверное имя пользователя или пароль.' : 'Не удалось подключиться к административному API.')
    } finally { setBusy(false) }
  }
  return <main className="admin-login-page"><section className="admin-login-card"><div className="admin-login-brand"><span>SR</span><div><strong>Sports Results</strong><small>Admin</small></div></div><h1>Вход для организатора</h1><p>Используйте административную учётную запись. Пароль хранится только в памяти вкладки и не записывается в хранилище браузера.</p>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<form className="admin-form" onSubmit={submit}><Field label="Имя пользователя"><input autoFocus required autoComplete="username" value={username} onChange={(change) => setUsername(change.target.value)} /></Field><Field label="Пароль"><input required type="password" autoComplete="current-password" value={password} onChange={(change) => setPassword(change.target.value)} /></Field><button className="admin-button-primary" disabled={busy}>{busy ? 'Проверяем…' : 'Войти'}</button></form><a href="/">← Вернуться на публичный сайт</a></section></main>
}

function AuthenticatedAdmin({ credentials, onLogout }: { credentials: AdminCredentials; onLogout: () => void }) {
  const api = useMemo(() => createAdminApi(credentials), [credentials])
  const location = useAdminLocation()
  const eventMatch = location.pathname.match(/^\/admin\/events\/(\d+)\/?$/)
  let content: React.ReactNode
  if (location.pathname === '/admin' || location.pathname === '/admin/' || location.pathname === '/admin/events' || location.pathname === '/admin/events/') {
    content = <AdminEventsPage api={api} />
  } else if (location.pathname === '/admin/support/issues' || location.pathname === '/admin/support/issues/') {
    content = <AdminJournalPage api={api} />
  } else if (eventMatch) {
    content = <AdminEventPage api={api} eventId={Number(eventMatch[1])} requestedTab={location.search.get('tab')} requestedRaceId={location.search.get('raceId')} />
  } else {
    content = <section className="admin-empty"><h1>Страница не найдена</h1><button className="admin-button-primary" onClick={() => navigateAdmin('/admin/events')}>К мероприятиям</button></section>
  }
  return <main className="admin-root"><header className="admin-mobile-header"><strong>Sports Results Admin</strong></header><aside className="admin-sidebar"><AdminLink className="admin-sidebar-brand" href="/admin/events"><span>SR</span><div><strong>Sports Results</strong><small>Admin</small></div></AdminLink><nav><p>Основное</p><AdminLink className={location.pathname.startsWith('/admin/events') || location.pathname === '/admin' ? 'active' : ''} href="/admin/events">Мероприятия</AdminLink><p>Поддержка</p><AdminLink className={location.pathname.startsWith('/admin/support/issues') ? 'active' : ''} href="/admin/support/issues">Журнал обращений</AdminLink></nav><div className="admin-sidebar-footer"><span>{credentials.username}</span><button type="button" onClick={onLogout}>Выйти</button></div></aside><div className="admin-content">{content}</div></main>
}
