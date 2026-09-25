/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { EventSeries, EventSummary, PageResponse, ResultInquiryConfiguration } from '../types'
import { AdminLink, navigateAdmin } from '../router'
import { adminErrorMessage } from '../utils'
import { localDateTimeToIso } from '../time'
import { AdminNotice, AdminPagination, Drawer, Field, Loadable, StatusBadge } from '../components/AdminUi'
import { InlineTemplateCreator } from '../components/InlineTemplateCreator'
import { TimeZoneCombobox } from '../components/TimeZoneCombobox'
import { EventStartComposer } from '../components/EventStartComposer'
import { ResultInquirySettingsFields } from '../components/ResultInquirySettingsFields'
import { draftsFromTemplate, startCommands } from '../eventStartDrafts'
import type { EventStartDraft } from '../eventStartDrafts'
import {
  calculateDraftResultInquiryDeadline,
  formatResultInquiryDeadline,
} from '../resultInquirySettings'

interface Props { api: AdminApi }

const EMPTY_PAGE: PageResponse<EventSummary> = {
  content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, sort: 'startsAt', direction: 'desc',
}

export function AdminEventsPage({ api }: Props) {
  const [events, setEvents] = useState(EMPTY_PAGE)
  const [templates, setTemplates] = useState<EventSeries[]>([])
  const [name, setName] = useState('')
  const [location, setLocation] = useState('')
  const [publicationStatus, setPublicationStatus] = useState('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const [eventPage, allTemplates] = await Promise.all([
        api.events({ name, location, publicationStatus, page, size: 25 }), api.eventSeries(),
      ])
      setEvents(eventPage); setTemplates(allTemplates)
    } catch (reason) { setError(adminErrorMessage(reason)) } finally { setLoading(false) }
  }, [api, location, name, page, publicationStatus])

  useEffect(() => { void load() }, [load])
  const addTemplate = (created: EventSeries) => setTemplates((current) => [...current, created]
    .sort((left, right) => left.name.localeCompare(right.name, 'ru')))
  const applyFilters = (event: FormEvent) => { event.preventDefault(); setPage(0); void load() }

  return <>
    <div className="admin-page-heading">
      <div><p className="admin-eyebrow">Управление</p><h1>Мероприятия</h1><p>Создавайте отдельные мероприятия или несколько городов сразу на основе шаблона.</p></div>
      <div className="admin-heading-actions">
        <button className="admin-button-primary" type="button" onClick={() => setCreateOpen(true)}>+ Создать мероприятие</button>
        <AdminLink className="admin-button-secondary" href="/admin/events/bulk">Создать несколько мероприятий</AdminLink>
        <AdminLink className="admin-button-secondary" href="/admin/templates">Шаблоны</AdminLink>
      </div>
    </div>

    <form className="admin-filter-bar" onSubmit={applyFilters}>
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
        <div className="admin-empty-actions"><button className="admin-button-primary" type="button" onClick={() => setCreateOpen(true)}>+ Создать мероприятие</button><AdminLink className="admin-button-secondary" href="/admin/events/bulk">Создать несколько</AdminLink></div>
      </section>}
    </Loadable>

    {createOpen && <CreateEventDrawer api={api} templates={templates} onTemplateCreated={addTemplate} onClose={() => setCreateOpen(false)} />}
  </>
}

function CreateEventDrawer({ api, templates, onTemplateCreated, onClose }: {
  api: AdminApi; templates: EventSeries[]; onTemplateCreated: (template: EventSeries) => void; onClose: () => void
}) {
  const [name, setName] = useState('')
  const [templateId, setTemplateId] = useState(templates[0]?.id ? String(templates[0].id) : '')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [location, setLocation] = useState('')
  const [timeZone, setTimeZone] = useState('Europe/Moscow')
  const [starts, setStarts] = useState<EventStartDraft[]>([])
  const [inquiry, setInquiry] = useState<ResultInquiryConfiguration>(() =>
    templates[0]?.resultInquiryDefaults ?? emptyInquirySettings())
  const [startsLoading, setStartsLoading] = useState(false)
  const [preview, setPreview] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (name) event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [name])

  useEffect(() => {
    if (!templateId) { setStarts([]); return }
    let cancelled = false
    setStartsLoading(true); setError(null); setPreview(false)
    const selectedTemplate = templates.find((item) => String(item.id) === templateId)
    setInquiry(selectedTemplate?.resultInquiryDefaults ?? emptyInquirySettings())
    api.templateStarts(Number(templateId))
      .then((loaded) => { if (!cancelled) setStarts(draftsFromTemplate(loaded)) })
      .catch((reason) => { if (!cancelled) setError(adminErrorMessage(reason)) })
      .finally(() => { if (!cancelled) setStartsLoading(false) })
    return () => { cancelled = true }
  }, [api, templateId, templates])

  const showPreview = (event: FormEvent) => {
    event.preventDefault()
    if (!templateId) { setError('Сначала выберите или создайте шаблон.'); return }
    if (startCommands(starts).some((start) => !start.name)) { setError('У каждого включённого старта должно быть название.'); return }
    if (inquiry.enabled && (!inquiry.email
      || (inquiry.deadlineMode === 'AFTER_EVENT_DAYS' && !inquiry.windowDays)
      || (inquiry.deadlineMode === 'FIXED_DATE' && !inquiry.fixedDate))) {
      setError('Укажите срок подачи обращений и email организатора.'); return
    }
    const eventDate = (endsAt || startsAt).slice(0, 10)
    if (inquiry.enabled && inquiry.deadlineMode === 'FIXED_DATE' && inquiry.fixedDate
      && eventDate && inquiry.fixedDate < eventDate) {
      setError('Последний день подачи обращений не может быть раньше даты окончания мероприятия.'); return
    }
    setError(null); setPreview(true)
  }
  const create = async () => {
    if (!templateId) return
    setBusy(true); setError(null)
    try {
      const created = await api.createEventWithStarts({
        event: { eventSeriesId: Number(templateId), name,
          startsAt: localDateTimeToIso(startsAt, timeZone), endsAt: localDateTimeToIso(endsAt, timeZone),
          location: location || null, timeZone, publicationStatus: 'DRAFT',
          resultInquiry: inquiry },
        starts: startCommands(starts),
      })
      navigateAdmin(`/admin/events/${created.event.id}`)
    } catch (reason) { setError(adminErrorMessage(reason)); setPreview(false) }
    finally { setBusy(false) }
  }
  const templateCreated = (created: EventSeries) => { onTemplateCreated(created); setTemplateId(String(created.id)) }

  const selectedStarts = startCommands(starts)
  const inquiryDeadline = calculateDraftResultInquiryDeadline(
    startsAt, endsAt, inquiry.deadlineMode, inquiry.windowDays, inquiry.fixedDate, timeZone,
  )
  return <Drawer title="Новое мероприятие" onClose={onClose}><form className="admin-form" onSubmit={showPreview}>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    {!preview ? <>
      <Field label="Название"><input required maxLength={255} value={name} onChange={(event) => setName(event.target.value)} /></Field>
      <Field label="Шаблон" hint="Шаблон объединяет мероприятия одного бренда и может использоваться повторно."><select required value={templateId} onChange={(event) => setTemplateId(event.target.value)}><option value="">Выберите шаблон</option>{templates.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
      <InlineTemplateCreator api={api} initiallyOpen={templates.length === 0} onCreated={templateCreated} />
      <div className="admin-form-grid"><Field label="Начало"><input type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /></Field><Field label="Окончание"><input type="datetime-local" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} /></Field></div>
      <Field label="Город / место"><input maxLength={255} value={location} onChange={(event) => setLocation(event.target.value)} /></Field>
      <Field label="Часовой пояс" hint="Выберите город с подходящим местным временем"><TimeZoneCombobox value={timeZone} location={location} onChange={setTimeZone} /></Field>
      <section className="admin-form-section"><h3>Обращения по результатам</h3>
        <ResultInquirySettingsFields
          value={inquiry}
          onChange={setInquiry}
          deadlinePreview={inquiryDeadline
            ? formatResultInquiryDeadline(inquiryDeadline, timeZone)
            : null}
          eventDateKnown={Boolean(endsAt || startsAt)}
        />
        <p className="admin-form-help">После окончания срока участники не смогут создавать новые обращения. Уже созданные обращения останутся доступны для обработки.</p>
      </section>
      {startsLoading ? <p>Загружаем старты шаблона…</p> : <EventStartComposer value={starts} onChange={setStarts} />}
      <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy || startsLoading} type="submit">Продолжить</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div>
    </> : <section className="admin-event-preview"><p className="admin-eyebrow">Проверка перед созданием</p><h2>{name}</h2><p><strong>Шаблон:</strong> {templates.find((item) => String(item.id) === templateId)?.name}<br /><strong>Город / место:</strong> {location || '—'}</p><p><strong>Обращения:</strong> {inquiry.enabled ? `${inquiry.deadlineMode === 'FIXED_DATE' ? 'до выбранной даты' : `${inquiry.windowDays} дн. после мероприятия`}, ${formatResultInquiryDeadline(inquiryDeadline, timeZone)}` : 'приём выключен'}</p><h3>Будут созданы старты</h3>{selectedStarts.length ? <ol>{selectedStarts.map((start) => <li key={`${start.templateStartId}-${start.sourceCode}-${start.name}`}><strong>{start.name}</strong><span>{start.distanceMeters === null ? 'Дистанция не указана' : `${start.distanceMeters} м`} · Награждение: {start.awardPolicy ? 'настроено' : 'штатные настройки'}</span></li>)}</ol> : <p>Мероприятие будет создано без стартов.</p>}<div className="admin-form-actions"><button className="admin-button-secondary" type="button" disabled={busy} onClick={() => setPreview(false)}>Назад</button><button className="admin-button-primary" type="button" disabled={busy} onClick={() => void create()}>{busy ? 'Создаём…' : 'Создать мероприятие'}</button></div></section>}
  </form></Drawer>
}

function emptyInquirySettings(): ResultInquiryConfiguration {
  return { enabled: false, deadlineMode: 'AFTER_EVENT_DAYS', windowDays: null, fixedDate: null, email: null }
}
