/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { AdminUser, AdminUserRole, EventSummary, PageResponse } from '../types'
import { AdminLink } from '../router'
import { adminErrorMessage, adminUserRoleLabel } from '../utils'
import { AdminNotice, Field, Loadable } from '../components/AdminUi'

const EMPTY_EVENTS: PageResponse<EventSummary> = {
  content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, sort: 'startsAt', direction: 'desc',
}

export function AdminUserPage({ api, userId }: { api: AdminApi; userId: number }) {
  const [user, setUser] = useState<AdminUser | null>(null)
  const [displayName, setDisplayName] = useState('')
  const [username, setUsername] = useState('')
  const [role, setRole] = useState<AdminUserRole>('TIMEKEEPER')
  const [active, setActive] = useState(true)
  const [selectedEvents, setSelectedEvents] = useState<Set<number>>(new Set())
  const [events, setEvents] = useState(EMPTY_EVENTS)
  const [eventSearch, setEventSearch] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const loadedUser = await api.user(userId)
      const [access, eventPage] = await Promise.all([
        loadedUser.role === 'TIMEKEEPER' ? api.userEvents(userId) : Promise.resolve({ userId, eventIds: [] }),
        api.events({ page: 0, size: 100 }),
      ])
      setUser(loadedUser); setDisplayName(loadedUser.displayName); setUsername(loadedUser.username)
      setRole(loadedUser.role); setActive(loadedUser.active); setSelectedEvents(new Set(access.eventIds)); setEvents(eventPage)
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, userId])
  useEffect(() => { void load() }, [load])

  const searchEvents = async (event: FormEvent) => {
    event.preventDefault(); setError(null)
    try { setEvents(await api.events({ name: eventSearch, page: 0, size: 100 })) }
    catch (reason) { setError(adminErrorMessage(reason)) }
  }
  const saveProfile = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null); setNotice(null)
    try {
      const updated = await api.updateUser(userId, { displayName, username, role, active })
      setUser(updated); setNotice('Пользователь сохранён.')
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  const saveAssignments = async () => {
    setBusy(true); setError(null); setNotice(null)
    try { await api.replaceUserEvents(userId, [...selectedEvents]); setNotice('Доступ к мероприятиям сохранён.') }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  const savePassword = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null); setNotice(null)
    try { await api.updateUserPassword(userId, password); setPassword(''); setNotice('Пароль изменён.') }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  return <>
    <div className="admin-event-heading"><div><AdminLink href="/admin/users">← Пользователи</AdminLink><p className="admin-eyebrow">Пользователь</p><h1>{user?.displayName ?? 'Пользователь'}</h1><p>{user ? `${adminUserRoleLabel(user.role)} · ${user.username}` : 'Загрузка данных'}</p></div></div>
    <Loadable loading={loading} error={error} empty={!user}>
      {user && <div className="admin-content-grid">
        {notice && <AdminNotice tone="success">{notice}</AdminNotice>}
        {error && <AdminNotice tone="danger">{error}</AdminNotice>}
        <form className="admin-card admin-form" onSubmit={saveProfile}><div className="admin-card-heading"><div><h2>Учётная запись</h2><p>Основные данные, роль и возможность входа.</p></div></div>
          <Field label="Имя"><input required maxLength={255} value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></Field>
          <Field label="Логин"><input required maxLength={160} value={username} onChange={(event) => setUsername(event.target.value)} /></Field>
          <Field label="Роль"><select value={role} onChange={(event) => setRole(event.target.value as AdminUserRole)}><option value="ORGANIZER">Организатор</option><option value="TIMEKEEPER">Хронометражист</option></select></Field>
          <label className="admin-check"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} /><span>Учётная запись активна</span></label>
          <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy}>Сохранить пользователя</button></div>
        </form>
        {role === 'ORGANIZER' ? <section className="admin-card"><h2>Доступ к мероприятиям</h2><AdminNotice tone="info">Организатор имеет доступ ко всем мероприятиям.</AdminNotice></section> : <section className="admin-card admin-form"><div className="admin-card-heading"><div><h2>Доступ к мероприятиям</h2><p>Найдите мероприятия и назначьте доступ хронометражисту.</p></div></div>
          <form className="admin-user-event-search" onSubmit={searchEvents}><Field label="Поиск мероприятий"><input value={eventSearch} onChange={(event) => setEventSearch(event.target.value)} placeholder="Название мероприятия" /></Field><button className="admin-button-secondary">Найти</button></form>
          <div className="admin-user-event-options">{events.content.map((event) => <label className="admin-check" key={event.id}><input type="checkbox" checked={selectedEvents.has(event.id)} onChange={(change) => setSelectedEvents((current) => {
            const next = new Set(current); if (change.target.checked) next.add(event.id); else next.delete(event.id); return next
          })} /><span><strong>{event.name}</strong><small>{event.location ?? 'Место не указано'}</small></span></label>)}</div>
          <p className="admin-form-help">Назначено мероприятий: {selectedEvents.size}. Поиск не сбрасывает уже выбранные назначения.</p>
          <div className="admin-form-actions"><button className="admin-button-primary" type="button" disabled={busy} onClick={() => void saveAssignments()}>Сохранить доступ</button></div>
        </section>}
        <form className="admin-card admin-form" onSubmit={savePassword}><div className="admin-card-heading"><div><h2>Новый пароль</h2><p>Пароль не отображается и не возвращается API.</p></div></div><Field label="Новый пароль" hint="Минимум 10 символов"><input required minLength={10} maxLength={200} type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} /></Field><div className="admin-form-actions"><button className="admin-button-secondary" disabled={busy || !password}>Изменить пароль</button></div></form>
      </div>}
    </Loadable>
  </>
}
