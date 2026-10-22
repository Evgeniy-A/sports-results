/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { EventSummary, PageResponse } from '../types'
import { AdminLink, navigateAdmin } from '../router'
import { adminErrorMessage } from '../utils'
import { AdminPagination, Field, Loadable, StatusBadge } from '../components/AdminUi'

interface Props { api: AdminApi }

const EMPTY_PAGE: PageResponse<EventSummary> = {
  content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, sort: 'startsAt', direction: 'desc',
}

export function AdminEventsPage({ api }: Props) {
  const [events, setEvents] = useState(EMPTY_PAGE)
  const [name, setName] = useState('')
  const [location, setLocation] = useState('')
  const [publicationStatus, setPublicationStatus] = useState('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try { setEvents(await api.events({ name, location, publicationStatus, page, size: 25 })) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, location, name, page, publicationStatus])

  useEffect(() => { void load() }, [load])
  const applyFilters = (event: FormEvent) => { event.preventDefault(); setPage(0); void load() }

  return <>
    <div className="admin-page-heading admin-events-heading">
      <div><p className="admin-eyebrow">Управление</p><h1>Мероприятия</h1><p>Управление мероприятиями, публикацией и стартами.</p></div>
    </div>

    <section className="admin-events-create-actions" aria-label="Создание мероприятий">
      <div><strong>Новое мероприятие</strong><span>Создайте один старт или подготовьте серию мероприятий сразу.</span></div>
      <div>
        <AdminLink className="admin-button-primary" href="/admin/events/new">+ Создать мероприятие</AdminLink>
        <AdminLink className="admin-button-secondary" href="/admin/events/bulk">Создать несколько</AdminLink>
      </div>
    </section>

    <form className="admin-filter-bar admin-events-filter" onSubmit={applyFilters}>
      <div className="admin-filter-heading"><strong>Поиск и фильтры</strong><span>Найдите мероприятие по названию, месту или статусу.</span></div>
      <Field label="Название"><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Например, M52" /></Field>
      <Field label="Город"><input value={location} onChange={(event) => setLocation(event.target.value)} placeholder="Город или место" /></Field>
      <Field label="Статус"><select value={publicationStatus} onChange={(event) => setPublicationStatus(event.target.value)}><option value="">Все</option><option value="DRAFT">Черновик</option><option value="PUBLISHED">Опубликовано</option><option value="ARCHIVED">Архив</option></select></Field>
      <button className="admin-button-secondary" type="submit">Найти</button>
    </form>

    <Loadable loading={loading} error={error}>
      {events.content.length ? <>
        <div className="admin-table-wrap admin-events-table-wrap"><table className="admin-table admin-events-table">
          <thead><tr><th>Мероприятие</th><th>Дата</th><th>Место</th><th>Шаблон</th><th>Публикация мероприятия</th></tr></thead>
          <tbody>{events.content.map((event) => <tr className="admin-event-row" key={event.id} role="link" tabIndex={0} aria-label={`Открыть мероприятие ${event.name}`} onClick={() => navigateAdmin(`/admin/events/${event.id}`)} onKeyDown={(keyEvent) => {
            if (keyEvent.key !== 'Enter' && keyEvent.key !== ' ') return
            keyEvent.preventDefault()
            navigateAdmin(`/admin/events/${event.id}`)
          }}>
            <td><strong>{event.name}</strong><small>/{event.slug}</small></td>
            <td>{event.startsAt ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeZone: event.timeZone }).format(new Date(event.startsAt)) : 'Дата не указана'}</td>
            <td>{event.location ?? '—'}</td><td>{event.eventSeriesName}</td>
            <td><StatusBadge value={event.publicationStatus} /></td>
          </tr>)}</tbody>
        </table></div>
        <AdminPagination page={events.page} totalPages={events.totalPages} onChange={setPage} />
      </> : <section className="admin-empty">
        <h2>Мероприятий пока нет</h2><p>Создайте одно мероприятие или несколько городов сразу из шаблона.</p>
        <div className="admin-empty-actions"><AdminLink className="admin-button-primary" href="/admin/events/new">+ Создать мероприятие</AdminLink><AdminLink className="admin-button-secondary" href="/admin/events/bulk">Создать несколько</AdminLink></div>
      </section>}
    </Loadable>
  </>
}
