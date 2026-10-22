/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { BulkEventCommand, BulkEventPreview, EventSeries } from '../types'
import type { BulkLocationRow } from '../bulkEvents'
import { duplicateLocationIds } from '../bulkEvents'
import { AdminLink, navigateAdmin } from '../router'
import { LOCATION_SUGGESTIONS, suggestTimeZone, timeZoneLabel } from '../timeZones'
import { adminErrorMessage } from '../utils'
import { AdminNotice, Field, Loadable } from '../components/AdminUi'
import { AdminCreationHeader, AdminCreationSection } from '../components/AdminCreationLayout'
import { InlineTemplateCreator } from '../components/InlineTemplateCreator'
import { TimeZoneCombobox } from '../components/TimeZoneCombobox'
import { EventStartComposer } from '../components/EventStartComposer'
import { draftsFromTemplate, startCommands } from '../eventStartDrafts'
import type { EventStartDraft } from '../eventStartDrafts'

const CREATION_STEPS = [
  { number: '01', label: 'Шаблон и дата' },
  { number: '02', label: 'Места' },
  { number: '03', label: 'Старты' },
  { number: '04', label: 'Проверка' },
]

export function AdminBulkEventsPage({ api }: { api: AdminApi }) {
  const nextId = useRef(2)
  const [templates, setTemplates] = useState<EventSeries[]>([])
  const [templateId, setTemplateId] = useState('')
  const [date, setDate] = useState('')
  const [rows, setRows] = useState<BulkLocationRow[]>([{ id: 1, location: '', timeZone: '' }])
  const [starts, setStarts] = useState<EventStartDraft[]>([])
  const [startsLoading, setStartsLoading] = useState(false)
  const [preview, setPreview] = useState<BulkEventPreview | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const duplicates = useMemo(() => duplicateLocationIds(rows), [rows])

  useEffect(() => {
    api.eventSeries().then((loaded) => {
      setTemplates(loaded); setTemplateId((current) => current || (loaded[0]?.id ? String(loaded[0].id) : ''))
    }).catch((reason) => setError(adminErrorMessage(reason))).finally(() => setLoading(false))
  }, [api])

  useEffect(() => {
    if (!templateId) { setStarts([]); return }
    let cancelled = false
    setStartsLoading(true); setPreview(null); setError(null)
    api.templateStarts(Number(templateId))
      .then((loaded) => { if (!cancelled) setStarts(draftsFromTemplate(loaded)) })
      .catch((reason) => { if (!cancelled) setError(adminErrorMessage(reason)) })
      .finally(() => { if (!cancelled) setStartsLoading(false) })
    return () => { cancelled = true }
  }, [api, templateId])

  const addRow = () => setRows((current) => [...current, { id: nextId.current++, location: '', timeZone: '' }])
  const updateLocation = (id: number, location: string) => setRows((current) => current.map((row) => row.id === id
    ? { ...row, location, timeZone: suggestTimeZone(location) ?? '' }
    : row))
  const updateTimeZone = (id: number, timeZone: string) => setRows((current) => current.map((row) => row.id === id ? { ...row, timeZone } : row))
  const removeRow = (id: number) => setRows((current) => current.filter((row) => row.id !== id))
  const addTemplate = (created: EventSeries) => {
    setTemplates((current) => [...current, created].sort((left, right) => left.name.localeCompare(right.name, 'ru')))
    setTemplateId(String(created.id))
  }
  const command = (): BulkEventCommand => ({
    eventSeriesId: Number(templateId), date,
    events: rows.map((row) => ({ name: row.location.trim(), location: row.location.trim(), timeZone: row.timeZone })),
    starts: startCommands(starts),
  })
  const ready = Boolean(templateId && date && rows.length && rows.every((row) => row.location.trim() && row.timeZone)
    && !duplicates.size && !startsLoading && startCommands(starts).every((start) => start.name))

  const buildPreview = async (event: FormEvent) => {
    event.preventDefault()
    if (!ready) { setError('Заполните шаблон, дату, города и часовые пояса; удалите повторяющиеся города.'); return }
    setBusy(true); setError(null)
    try { setPreview(await api.previewBulkEvents(command())) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  const create = async () => {
    if (!preview || preview.creatableCount !== preview.requestedCount) return
    setBusy(true); setError(null)
    try { await api.createBulkEvents(command()); navigateAdmin('/admin/events') }
    catch (reason) { setError(adminErrorMessage(reason)); setPreview(null) }
    finally { setBusy(false) }
  }

  return <>
    <AdminCreationHeader
      title="Создать несколько мероприятий"
      description="Подготовьте города, настройте общие старты и проверьте мероприятия перед созданием."
      steps={CREATION_STEPS}
      activeStep={preview ? '04' : '01'}
    />
    <Loadable loading={loading} error={null}>
      {!preview ? <form className="admin-creation-form" onSubmit={buildPreview}>
        {error && <AdminNotice tone="danger">{error}</AdminNotice>}
        <AdminCreationSection number="01" title="Шаблон и дата" description="Выберите основу и общую дату для всех мероприятий.">
          <div className="admin-creation-fields"><Field label="Шаблон *"><select required value={templateId} onChange={(event) => setTemplateId(event.target.value)}><option value="">Выберите шаблон</option>{templates.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}</select></Field><InlineTemplateCreator api={api} initiallyOpen={!templates.length} onCreated={addTemplate} /><Field label="Дата"><input required type="date" value={date} onChange={(event) => setDate(event.target.value)} /></Field></div>
        </AdminCreationSection>
        <AdminCreationSection number="02" title="Города / места проведения" description="Название каждого мероприятия будет совпадать с указанным городом или местом.">
          <div className="admin-section-toolbar is-compact"><div><p>Добавьте все места, которые нужно создать одним действием.</p></div><button className="admin-button-secondary" type="button" onClick={addRow}>+ Добавить город</button></div>
          <datalist id="bulk-location-options">{LOCATION_SUGGESTIONS.map((city) => <option value={city} key={city} />)}</datalist>
          <div className="admin-bulk-rows">{rows.map((row, index) => <section className="admin-bulk-row" key={row.id}>
            <Field label={`Город ${index + 1}`}><input required list="bulk-location-options" maxLength={255} value={row.location} onChange={(event) => updateLocation(row.id, event.target.value)} placeholder="Например, Казань" />{duplicates.has(row.id) && <small className="admin-field-error">Такой город уже добавлен.</small>}</Field>
            <Field label="Часовой пояс"><TimeZoneCombobox value={row.timeZone} location={row.location} onChange={(value) => updateTimeZone(row.id, value)} />{row.location.trim() && !row.timeZone && <small className="admin-field-error">Выберите часовой пояс для этого места.</small>}</Field>
            <button className="admin-link-button danger" type="button" disabled={rows.length === 1} onClick={() => removeRow(row.id)}>Удалить</button>
          </section>)}</div>
        </AdminCreationSection>
        <AdminCreationSection number="03" title="Старты мероприятия" description="Один набор стартов будет создан для каждого указанного места.">
          {startsLoading ? <p className="admin-creation-loading">Загружаем старты шаблона…</p> : <EventStartComposer value={starts} onChange={setStarts} compactHeader />}
        </AdminCreationSection>
        <div className="admin-creation-actions"><div><strong>Следующий шаг — проверка</strong><span>Проверьте города, часовые пояса и старты.</span></div><div><AdminLink className="admin-button-secondary" href="/admin/events">Отмена</AdminLink><button className="admin-button-primary" disabled={busy || !ready}>{busy ? 'Проверяем…' : 'Продолжить'}</button></div></div>
      </form> : <BulkPreview preview={preview} busy={busy} onBack={() => setPreview(null)} onCreate={() => void create()} />}
    </Loadable>
  </>
}

function BulkPreview({ preview, busy, onBack, onCreate }: {
  preview: BulkEventPreview; busy: boolean; onBack: () => void; onCreate: () => void
}) {
  const allCreatable = preview.creatableCount === preview.requestedCount
  return <AdminCreationSection number="04" title="Проверка" description="Проверьте список перед созданием мероприятий.">
    <div className="admin-bulk-preview"><div className="admin-preview-summary"><p className="admin-eyebrow">Готово к созданию</p><h2>Создать {preview.requestedCount} мероприятий</h2><p><strong>Шаблон:</strong> {preview.templateName}<br /><strong>Дата:</strong> {new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(`${preview.date}T00:00:00Z`))}<br /><strong>Будет создано стартов:</strong> {preview.totalStartCount}</p></div>
    {!allCreatable && <AdminNotice tone="warning">Исправьте отмеченные дубли перед созданием.</AdminNotice>}
    <div className="admin-preview-list">{preview.events.map((item, index) => <article key={`${item.location}-${index}`}>
      <div><h3>{item.name}</h3><p>Часовой пояс: {item.timeZone} · {timeZoneLabel(item.timeZone)}</p>{preview.starts.length ? <ul>{preview.starts.map((start) => <li key={`${start.sourceCode}-${start.name}`}>{start.name}{start.awardConfigured ? ' · Награждение: из шаблона или настроено' : ''}</li>)}</ul> : <small>Без стартов</small>}</div>
      <strong className={item.creatable ? 'admin-preview-ok' : 'admin-preview-warning'}>{item.creatable ? '✓ можно создать' : '⚠ Такое мероприятие уже существует'}</strong>
    </article>)}</div>
    <div className="admin-creation-actions"><div><strong>Все мероприятия готовы</strong><span>После создания вы вернётесь к списку мероприятий.</span></div><div><button className="admin-button-secondary" type="button" disabled={busy} onClick={onBack}>Назад</button><button className="admin-button-primary" type="button" disabled={busy || !allCreatable} onClick={onCreate}>{busy ? 'Создаём…' : `Создать ${preview.requestedCount} мероприятий`}</button></div></div>
    </div>
  </AdminCreationSection>
}
