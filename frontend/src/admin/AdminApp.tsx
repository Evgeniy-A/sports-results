import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { createAdminApi } from './api'
import type { AdminCredentials, CurrentAdminUser } from './types'
import { AdminEventsPage } from './pages/AdminEventsPage'
import { AdminEventPage } from './pages/AdminEventPage'
import { AdminJournalPage } from './pages/AdminJournalPage'
import { AdminTemplatesPage } from './pages/AdminTemplatesPage'
import { AdminBulkEventsPage } from './pages/AdminBulkEventsPage'
import { AdminCreateEventPage } from './pages/AdminCreateEventPage'
import { AdminTemplatePage } from './pages/AdminTemplatePage'
import { AdminEventIssuePage } from './pages/AdminEventIssuePage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { AdminUserPage } from './pages/AdminUserPage'
import { AdminLink, navigateAdmin, useAdminLocation } from './router'
import { AdminNotice, Field } from './components/AdminUi'
import { clearAdminCredentials, loadAdminCredentials, saveAdminCredentials } from './adminAuthSession'
import './admin.css'

export function AdminApp() {
  const [credentials, setCredentials] = useState<AdminCredentials | null>(() => loadAdminCredentials())
  const authenticated = useCallback((next: AdminCredentials) => { saveAdminCredentials(next); setCredentials(next) }, [])
  const logout = useCallback(() => { clearAdminCredentials(); setCredentials(null) }, [])
  if (!credentials) return <AdminLogin onAuthenticated={authenticated} />
  return <AuthenticatedAdmin credentials={credentials} onLogout={logout} />
}

function AdminLogin({ onAuthenticated }: { onAuthenticated: (credentials: AdminCredentials) => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null)
    const credentials = { username, password }
    try { await createAdminApi(credentials).me(); onAuthenticated(credentials) }
    catch (reason) {
      setError(reason instanceof ApiError && reason.status === 401 ? 'Неверное имя пользователя или пароль.' : 'Не удалось подключиться к административному API.')
    } finally { setBusy(false) }
  }
  return <main className="admin-login-page"><section className="admin-login-card"><div className="admin-login-brand"><span>SR</span><div><strong>Sports Results</strong><small>Admin</small></div></div><h1>Вход для организатора</h1><p>Используйте административную учётную запись. Данные входа сохраняются только до закрытия текущей вкладки браузера.</p>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<form className="admin-form" onSubmit={submit}><Field label="Имя пользователя"><input autoFocus required autoComplete="username" value={username} onChange={(change) => setUsername(change.target.value)} /></Field><Field label="Пароль"><input required type="password" autoComplete="current-password" value={password} onChange={(change) => setPassword(change.target.value)} /></Field><button className="admin-button-primary" disabled={busy}>{busy ? 'Проверяем…' : 'Войти'}</button></form><a href="/">← Вернуться на публичный сайт</a></section></main>
}

function AuthenticatedAdmin({ credentials, onLogout }: { credentials: AdminCredentials; onLogout: () => void }) {
  const location = useAdminLocation()
  const [forbidden, setForbidden] = useState(false)
  const [currentUser, setCurrentUser] = useState<CurrentAdminUser | null>(null)
  const [identityError, setIdentityError] = useState<string | null>(null)
  const api = useMemo(() => createAdminApi(credentials, onLogout, () => setForbidden(true)), [credentials, onLogout])
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const menuButtonRef = useRef<HTMLButtonElement>(null)
  const sidebarRef = useRef<HTMLElement>(null)
  const closeSidebar = useCallback((restoreFocus = false) => {
    setSidebarOpen(false)
    if (restoreFocus) window.requestAnimationFrame(() => menuButtonRef.current?.focus())
  }, [])

  useEffect(() => {
    let cancelled = false
    api.me().then((user) => { if (!cancelled) setCurrentUser(user) })
      .catch((reason) => { if (!cancelled) setIdentityError(adminErrorText(reason)) })
    return () => { cancelled = true }
  }, [api])
  useEffect(() => {
    const clearForbidden = () => setForbidden(false)
    window.addEventListener('sportsresults:admin-navigation', clearForbidden)
    window.addEventListener('popstate', clearForbidden)
    return () => {
      window.removeEventListener('sportsresults:admin-navigation', clearForbidden)
      window.removeEventListener('popstate', clearForbidden)
    }
  }, [])

  useEffect(() => {
    if (!sidebarOpen) return
    sidebarRef.current?.querySelector<HTMLAnchorElement>('a[href]')?.focus()
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      closeSidebar(true)
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [closeSidebar, sidebarOpen])
  const eventMatch = location.pathname.match(/^\/admin\/events\/(\d+)\/?$/)
  const eventIssueMatch = location.pathname.match(/^\/admin\/events\/(\d+)\/results\/issues\/(\d+)\/?$/)
  const templateMatch = location.pathname.match(/^\/admin\/templates\/(\d+)\/?$/)
  const userMatch = location.pathname.match(/^\/admin\/users\/(\d+)\/?$/)
  if (!currentUser) return <main className="admin-root"><div className="admin-content"><section className="admin-empty"><h1>{identityError ? 'Не удалось загрузить профиль' : 'Загружаем профиль…'}</h1>{identityError && <p>{identityError}</p>}</section></div></main>
  const organizer = currentUser.role === 'ORGANIZER'
  const organizerOnlyRoute = location.pathname.startsWith('/admin/templates')
    || location.pathname.startsWith('/admin/users')
    || location.pathname.startsWith('/admin/support')
    || location.pathname === '/admin/events/new' || location.pathname === '/admin/events/new/'
    || location.pathname === '/admin/events/bulk' || location.pathname === '/admin/events/bulk/'
  let content: React.ReactNode
  if (forbidden || (!organizer && organizerOnlyRoute)) {
    content = <AccessDeniedPage timekeeper={!organizer} />
  } else if (location.pathname === '/admin' || location.pathname === '/admin/' || location.pathname === '/admin/events' || location.pathname === '/admin/events/') {
    content = <AdminEventsPage api={api} currentUser={currentUser} />
  } else if (location.pathname === '/admin/events/new' || location.pathname === '/admin/events/new/') {
    content = <AdminCreateEventPage api={api} />
  } else if (location.pathname === '/admin/events/bulk' || location.pathname === '/admin/events/bulk/') {
    content = <AdminBulkEventsPage api={api} />
  } else if (location.pathname === '/admin/templates' || location.pathname === '/admin/templates/') {
    content = <AdminTemplatesPage api={api} />
  } else if (templateMatch) {
    content = <AdminTemplatePage api={api} templateId={Number(templateMatch[1])} />
  } else if (location.pathname === '/admin/support/issues' || location.pathname === '/admin/support/issues/') {
    content = <AdminJournalPage api={api} />
  } else if (location.pathname === '/admin/users' || location.pathname === '/admin/users/') {
    content = <AdminUsersPage api={api} />
  } else if (userMatch) {
    content = <AdminUserPage api={api} userId={Number(userMatch[1])} />
  } else if (eventIssueMatch) {
    content = <AdminEventIssuePage api={api} eventId={Number(eventIssueMatch[1])} issueId={Number(eventIssueMatch[2])} returnTo={location.search.get('returnTo')} />
  } else if (eventMatch) {
    content = <AdminEventPage api={api} eventId={Number(eventMatch[1])} requestedTab={location.search.get('tab')} requestedRaceId={location.search.get('raceId')} currentUser={currentUser} />
  } else {
    content = <section className="admin-empty"><h1>Страница не найдена</h1><button className="admin-button-primary" onClick={() => navigateAdmin('/admin/events')}>К мероприятиям</button></section>
  }
  return <main className="admin-root">
    <header className="admin-mobile-header">
      <button ref={menuButtonRef} className="admin-menu-button" type="button" aria-label={sidebarOpen ? 'Закрыть меню' : 'Открыть меню'} aria-expanded={sidebarOpen} aria-controls="admin-navigation" onClick={() => sidebarOpen ? closeSidebar() : setSidebarOpen(true)}>☰</button>
      <strong>Sports Results Admin</strong>
    </header>
    {sidebarOpen && <>
      <button className="admin-sidebar-scrim" type="button" aria-label="Закрыть меню" onClick={() => closeSidebar(true)} />
      <aside ref={sidebarRef} id="admin-navigation" className="admin-sidebar" aria-label="Административная навигация">
        <AdminLink className="admin-sidebar-brand" href="/admin/events" onClick={() => closeSidebar()}><span>SR</span><div><strong>Sports Results</strong><small>Admin</small></div></AdminLink>
        <nav><p>Основное</p><AdminLink className={(location.pathname.startsWith('/admin/events') || location.pathname === '/admin') ? 'active' : ''} href="/admin/events" onClick={() => closeSidebar()}>{organizer ? 'Мероприятия' : 'Мои мероприятия'}</AdminLink>{organizer && <><AdminLink className={location.pathname.startsWith('/admin/templates') ? 'active' : ''} href="/admin/templates" onClick={() => closeSidebar()}>Шаблоны</AdminLink><AdminLink className={location.pathname.startsWith('/admin/users') ? 'active' : ''} href="/admin/users" onClick={() => closeSidebar()}>Пользователи</AdminLink><p>Поддержка</p><AdminLink className={location.pathname.startsWith('/admin/support/issues') ? 'active' : ''} href="/admin/support/issues" onClick={() => closeSidebar()}>Журнал обращений</AdminLink></>}</nav>
        <div className="admin-sidebar-footer"><span>{currentUser.displayName || currentUser.username}</span><small>{organizer ? 'Организатор' : 'Хронометражист'}</small><button type="button" onClick={onLogout}>Выйти</button></div>
      </aside>
    </>}
    <div className="admin-content">{content}</div>
  </main>
}

function AccessDeniedPage({ timekeeper }: { timekeeper: boolean }) {
  return <section className="admin-empty"><p className="admin-eyebrow">403</p><h1>Нет доступа</h1><p>У вашей учётной записи нет доступа к этому разделу или мероприятию.</p><button className="admin-button-primary" type="button" onClick={() => navigateAdmin('/admin/events')}>{timekeeper ? 'К моим мероприятиям' : 'К мероприятиям'}</button></section>
}

function adminErrorText(reason: unknown): string {
  return reason instanceof Error ? reason.message : 'Административный API недоступен.'
}
