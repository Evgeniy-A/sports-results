/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { EventSeries, EventSummary, PageResponse } from '../types'
import { AdminLink, navigateAdmin } from '../router'
import { adminErrorMessage } from '../utils'
import { localDateTimeToIso } from '../time'
import { AdminNotice, AdminPagination, Drawer, Field, Loadable, StatusBadge } from '../components/AdminUi'
import { TimeZoneCombobox } from '../components/TimeZoneCombobox'

interface Props {
  api: AdminApi
}

const EMPTY_PAGE: PageResponse<EventSummary> = {
  content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, sort: 'startsAt', direction: 'desc',
}

export function AdminEventsPage({ api }: Props) {
  const [events, setEvents] = useState(EMPTY_PAGE)
  const [series, setSeries] = useState<EventSeries[]>([])
  const [name, setName] = useState('')
  const [location, setLocation] = useState('')
  const [publicationStatus, setPublicationStatus] = useState('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [eventPage, allSeries] = await Promise.all([
        api.events({ name, location, publicationStatus, page, size: 25 }),
        api.eventSeries(),
      ])
      setEvents(eventPage)
      setSeries(allSeries)
    } catch (reason) {
      setError(adminErrorMessage(reason))
    } finally {
      setLoading(false)
    }
  }, [api, location, name, page, publicationStatus])

  useEffect(() => { void load() }, [load])

  const applyFilters = (event: FormEvent) => {
    event.preventDefault()
    setPage(0)
    void load()
  }

  return <>
    <div className="admin-page-heading">
      <div><p className="admin-eyebrow">Управление</p><h1>Мероприятия</h1><p>Создание, настройки и публикация спортивных событий.</p></div>
      <button className="admin-button-primary" type="button" onClick={() => setCreateOpen(true)}>Создать мероприятие</button>
    </div>

    <form className="admin-filter-bar" onSubmit={applyFilters}>
      <Field label="Название"><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Например, M52" /></Field>
      <Field label="Город"><input value={location} onChange={(event) => setLocation(event.target.value)} placeholder="Город или место" /></Field>
      <Field label="Статус">
        <select value={publicationStatus} onChange={(event) => setPublicationStatus(event.target.value)}>
          <option value="">Все</option><option value="DRAFT">Черновик</option><option value="PUBLISHED">Опубликовано</option><option value="ARCHIVED">Архив</option>
        </select>
      </Field>
      <button className="admin-button-secondary" type="submit">Найти</button>
    </form>

    <Loadable loading={loading} error={error} empty={!events.content.length}>
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead><tr><th>Мероприятие</th><th>Дата</th><th>Место</th><th>Серия</th><th>Event</th><th>Результаты</th><th /></tr></thead>
          <tbody>{events.content.map((event) => <tr key={event.id}>
            <td><strong>{event.name}</strong><small>/{event.slug}</small></td>
            <td>{event.startsAt ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeZone: event.timeZone }).format(new Date(event.startsAt)) : 'Дата не указана'}</td>
            <td>{event.location ?? '—'}</td>
            <td>{event.eventSeriesName}</td>
            <td><StatusBadge value={event.publicationStatus} /></td>
            <td><StatusBadge value={event.resultsPublicationStatus} /></td>
            <td><AdminLink className="admin-row-link" href={`/admin/events/${event.id}`}>Открыть</AdminLink></td>
          </tr>)}</tbody>
        </table>
      </div>
      <AdminPagination page={events.page} totalPages={events.totalPages} onChange={setPage} />
    </Loadable>

    {createOpen && <CreateEventDrawer
      api={api}
      series={series}
      onSeriesCreated={(created) => setSeries((current) => [...current, created]
        .sort((left, right) => left.name.localeCompare(right.name, 'ru')))}
      onClose={() => setCreateOpen(false)}
    />}
  </>
}

function CreateEventDrawer({ api, series, onSeriesCreated, onClose }: {
  api: AdminApi
  series: EventSeries[]
  onSeriesCreated: (series: EventSeries) => void
  onClose: () => void
}) {
  const [name, setName] = useState('')
  const [seriesId, setSeriesId] = useState(series[0]?.id ? String(series[0].id) : '')
  const [seriesCreatorOpen, setSeriesCreatorOpen] = useState(series.length === 0)
  const [newSeriesName, setNewSeriesName] = useState('')
  const [seriesBusy, setSeriesBusy] = useState(false)
  const [seriesError, setSeriesError] = useState<string | null>(null)
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [location, setLocation] = useState('')
  const [timeZone, setTimeZone] = useState('Europe/Moscow')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (name) event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [name])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!seriesId) { setError('Сначала выберите или создайте серию мероприятий.'); return }
    setBusy(true); setError(null)
    try {
      const created = await api.createEvent({
        eventSeriesId: Number(seriesId), name,
        startsAt: localDateTimeToIso(startsAt, timeZone), endsAt: localDateTimeToIso(endsAt, timeZone),
        location: location || null, timeZone, publicationStatus: 'DRAFT',
      })
      navigateAdmin(`/admin/events/${created.id}`)
    } catch (reason) {
      setError(adminErrorMessage(reason))
    } finally {
      setBusy(false)
    }
  }

  const createSeries = async () => {
    if (!newSeriesName.trim()) { setSeriesError('Укажите название серии.'); return }
    setSeriesBusy(true); setSeriesError(null)
    try {
      const created = await api.createEventSeries({
        name: newSeriesName.trim(), description: null, active: true,
      })
      onSeriesCreated(created)
      setSeriesId(String(created.id))
      setNewSeriesName('')
      setSeriesCreatorOpen(false)
    } catch (reason) {
      setSeriesError(adminErrorMessage(reason))
    } finally {
      setSeriesBusy(false)
    }
  }

  return <Drawer title="Новое мероприятие" onClose={onClose}>
    <form className="admin-form" onSubmit={submit}>
      {error && <AdminNotice tone="danger">{error}</AdminNotice>}
      <Field label="Название"><input required maxLength={255} value={name} onChange={(event) => setName(event.target.value)} /></Field>
      <Field label="Серия мероприятий" hint="Выберите бренд или цикл, к которому относится мероприятие."><select required value={seriesId} onChange={(event) => setSeriesId(event.target.value)}><option value="">Выберите серию</option>{series.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
      <button
        type="button"
        className="admin-link-button admin-create-series-toggle"
        aria-expanded={seriesCreatorOpen}
        onClick={() => { setSeriesCreatorOpen((open) => !open); setSeriesError(null) }}
      >+ Создать новую серию</button>
      {seriesCreatorOpen && <section className="admin-inline-create" aria-label="Создание серии мероприятий">
        <Field label="Название серии"><input
          maxLength={255}
          value={newSeriesName}
          onChange={(event) => setNewSeriesName(event.target.value)}
          onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void createSeries() } }}
          placeholder="Например, Гонка Героев"
        /></Field>
        {seriesError && <AdminNotice tone="danger">{seriesError}</AdminNotice>}
        <div className="admin-form-actions">
          <button className="admin-button-secondary" type="button" disabled={seriesBusy || !newSeriesName.trim()} onClick={() => void createSeries()}>{seriesBusy ? 'Создаём…' : 'Создать серию'}</button>
          <button className="admin-link-button" type="button" disabled={seriesBusy} onClick={() => { setSeriesCreatorOpen(false); setSeriesError(null) }}>Отмена</button>
        </div>
      </section>}
      <div className="admin-form-grid"><Field label="Начало"><input type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /></Field><Field label="Окончание"><input type="datetime-local" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} /></Field></div>
      <Field label="Город / место"><input maxLength={255} value={location} onChange={(event) => setLocation(event.target.value)} /></Field>
      <Field label="Часовой пояс" hint="Выберите город с подходящим местным временем">
        <TimeZoneCombobox value={timeZone} location={location} onChange={setTimeZone} />
      </Field>
      <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy || seriesBusy} type="submit">{busy ? 'Создаём…' : 'Создать'}</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div>
    </form>
  </Drawer>
}
