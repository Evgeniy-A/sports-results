/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { EventSeries, EventSummary, ParticipantInfo, Race } from '../types'
import { adminErrorMessage } from '../utils'
import { localDateTimeToIso, toDateTimeLocal } from '../time'
import { AdminNotice, ConfirmDialog, Drawer, Field, StatusBadge } from '../components/AdminUi'
import { TimeZoneCombobox } from '../components/TimeZoneCombobox'
import { EventContentManager } from './EventContentManager'
import { ResultInquirySettingsCard } from '../components/ResultInquirySettingsCard'

export function EventGeneralTab({ api, event, series, races, onChanged }: {
  api: AdminApi
  event: EventSummary
  series: EventSeries[]
  races: Race[]
  onChanged: () => Promise<void>
}) {
  const [form, setForm] = useState(() => eventForm(event))
  const [publicationStatus, setPublicationStatus] = useState(event.publicationStatus)
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)

  useEffect(() => {
    setForm(eventForm(event)); setPublicationStatus(event.publicationStatus); setDirty(false)
  }, [api, event])

  useEffect(() => {
    const warn = (navigation: BeforeUnloadEvent) => { if (dirty) navigation.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const set = (field: keyof typeof form, value: string) => { setForm((current) => ({ ...current, [field]: value })); setDirty(true) }
  const resultAffecting = form.startsAt !== toDateTimeLocal(event.startsAt) || form.timeZone !== event.timeZone
  const publishedRace = races.some((race) => race.resultsPublicationStatus === 'PUBLISHED')

  const saveEvent = async (submit: FormEvent) => {
    submit.preventDefault(); setBusy('event'); setMessage(null)
    try {
      await api.updateEvent(event.id, {
        eventSeriesId: Number(form.eventSeriesId), name: form.name,
        startsAt: localDateTimeToIso(form.startsAt, form.timeZone), endsAt: localDateTimeToIso(form.endsAt, form.timeZone),
        location: form.location || null, timeZone: form.timeZone,
        publicationStatus: event.publicationStatus,
      })
      setDirty(false); setMessage({ tone: 'success', text: 'Изменения сохранены.' }); await onChanged()
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(null) }
  }

  const savePublication = async () => {
    setBusy('publication'); setMessage(null)
    try {
      await api.updateEventPublication(event.id, {
        eventPublicationStatus: publicationStatus,
        resultsPublicationStatus: event.resultsPublicationStatus,
      })
      setMessage({ tone: 'success', text: 'Публикация информации о мероприятии обновлена. Статусы результатов стартов не изменены.' })
      await onChanged()
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(null) }
  }

  return <div className="admin-stack">
    {message && <AdminNotice tone={message.tone}>{message.text}</AdminNotice>}
    <section className="admin-card"><div className="admin-card-heading"><div><h2>Основные данные</h2><p>Название, даты и место мероприятия.</p></div></div>
      <form className="admin-form" onSubmit={saveEvent}>
        <Field label="Название"><input required value={form.name} onChange={(change) => set('name', change.target.value)} /></Field>
        <div className="admin-form-grid"><Field label="Начало"><input type="datetime-local" value={form.startsAt} onChange={(change) => set('startsAt', change.target.value)} /></Field><Field label="Окончание"><input type="datetime-local" value={form.endsAt} onChange={(change) => set('endsAt', change.target.value)} /></Field></div>
        <div className="admin-form-grid"><Field label="Место"><input value={form.location} onChange={(change) => set('location', change.target.value)} /></Field><Field label="Часовой пояс" hint="Выберите город с подходящим местным временем"><TimeZoneCombobox value={form.timeZone} location={form.location} onChange={(value) => set('timeZone', value)} /></Field></div>
        <Field label="Шаблон"><select value={form.eventSeriesId} onChange={(change) => set('eventSeriesId', change.target.value)}>{series.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
        {resultAffecting && <AdminNotice tone="warning"><strong>Настройка влияет на итоговый протокол.</strong>{publishedRace && <> Чтобы изменить дату или часовой пояс, сначала верните опубликованные старты в черновик.</>}</AdminNotice>}
        <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy === 'event' || !dirty}>Сохранить</button></div>
      </form>
    </section>

    <ResultInquirySettingsCard api={api} event={event} />

    <section className="admin-card"><div className="admin-card-heading"><div><h2>Публикация мероприятия</h2><p>Управляет только публичной информацией о мероприятии. Результаты публикуются отдельно для каждого старта.</p></div><StatusBadge value={event.publicationStatus} /></div>
      <div className="admin-form-grid"><Field label="Статус мероприятия"><select value={publicationStatus} onChange={(change) => setPublicationStatus(change.target.value as EventSummary['publicationStatus'])}><option value="DRAFT">Черновик</option><option value="PUBLISHED">Опубликовано</option><option value="ARCHIVED">Архив</option></select></Field></div>
      <div className="admin-form-actions"><button className="admin-button-primary" type="button" disabled={busy === 'publication' || publicationStatus === event.publicationStatus} onClick={() => void savePublication()}>Сохранить публикацию</button></div>
    </section>
  </div>
}

export function EventInformationTab({ api, event }: { api: AdminApi; event: EventSummary }) {
  const [info, setInfo] = useState<ParticipantInfo | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)

  useEffect(() => {
    api.participantInfo(event.id)
      .then((infoValue) => { setInfo(infoValue) })
      .catch((reason) => setMessage({ tone: 'danger', text: adminErrorMessage(reason) }))
  }, [api, event.id])
  const saveInfo = async (submit: FormEvent) => {
    submit.preventDefault(); if (!info) return; setBusy('info'); setMessage(null)
    try {
      setInfo(await api.updateParticipantInfo(event.id, {
        shortDescription: info.shortDescription, venueName: info.venueName,
        venueAddress: info.venueAddress, locationDescription: info.locationDescription,
        latitude: info.latitude, longitude: info.longitude, additionalInfo: info.additionalInfo,
      }))
      setMessage({ tone: 'success', text: 'Информация для участников сохранена.' })
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(null) }
  }

  return <div className="admin-stack">
    {message && <AdminNotice tone={message.tone}>{message.text}</AdminNotice>}
    {info && <section className="admin-card"><div className="admin-card-heading"><div><h2>Информация для участников</h2><p>Публичное описание и сведения о площадке.</p></div></div><form className="admin-form" onSubmit={saveInfo}>
      <Field label="Краткое описание"><textarea value={info.shortDescription ?? ''} onChange={(change) => setInfo({ ...info, shortDescription: change.target.value || null })} /></Field>
      <div className="admin-form-grid"><Field label="Площадка"><input value={info.venueName ?? ''} onChange={(change) => setInfo({ ...info, venueName: change.target.value || null })} /></Field><Field label="Адрес площадки"><input value={info.venueAddress ?? ''} onChange={(change) => setInfo({ ...info, venueAddress: change.target.value || null })} /></Field></div>
      <Field label="Как добраться"><textarea value={info.locationDescription ?? ''} onChange={(change) => setInfo({ ...info, locationDescription: change.target.value || null })} /></Field>
      <Field label="Дополнительная информация"><textarea value={info.additionalInfo ?? ''} onChange={(change) => setInfo({ ...info, additionalInfo: change.target.value || null })} /></Field>
      <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy === 'info'}>Сохранить информацию</button></div>
    </form></section>}
    <EventContentManager api={api} eventId={event.id} />
  </div>
}

function eventForm(event: EventSummary) {
  return {
    eventSeriesId: String(event.eventSeriesId), name: event.name,
    startsAt: toDateTimeLocal(event.startsAt, event.timeZone), endsAt: toDateTimeLocal(event.endsAt, event.timeZone),
    location: event.location ?? '', timeZone: event.timeZone,
  }
}

export function StartsTab({ api, event, races, onChanged, onOpen }: {
  api: AdminApi
  event: EventSummary
  races: Race[]
  onChanged: () => Promise<void>
  onOpen: (raceId: number) => void
}) {
  const [raceEditor, setRaceEditor] = useState<Race | 'new' | null>(null)
  const [confirm, setConfirm] = useState<{ kind: 'draft' | 'publish' | 'delete'; id: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)
  const confirmedRace = confirm ? races.find((race) => race.id === confirm.id) : null

  const runConfirm = async () => {
    if (!confirm) return; setBusy(true); setMessage(null)
    try {
      if (confirm.kind === 'draft') await api.draftRace(event.id, confirm.id, 'Изменено через Admin UI')
      if (confirm.kind === 'publish') await api.publishRace(event.id, confirm.id)
      if (confirm.kind === 'delete') await api.deleteRace(event.id, confirm.id)
      setMessage({ tone: 'success', text: confirm.kind === 'publish' ? 'Старт опубликован.' : confirm.kind === 'delete' ? 'Пустой старт удалён.' : 'Старт возвращён в черновик.' })
      setConfirm(null); await onChanged()
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(false) }
  }

  return <div className="admin-stack">
    {message && <AdminNotice tone={message.tone}>{message.text}</AdminNotice>}
    <div className="admin-section-toolbar"><div><h2>Старты</h2><p>Дистанция и дата старта необязательны. Категории, результаты и публикация настраиваются отдельно для каждого старта.</p></div><button className="admin-button-primary" onClick={() => setRaceEditor('new')}>Добавить старт</button></div>
    {races.length ? <div className="admin-race-grid">{races.map((race) => <article className="admin-race-card" key={race.id}>
      <div><h4>{race.name}</h4><p>Дистанция: {formatDistance(race.distanceMeters)}</p><p>Начало: {formatStart(race.startsAt, event.timeZone)}</p></div>
      <div className="admin-inline-badges"><StatusBadge value={race.resultsPublicationStatus} />{!race.publicVisible && <span className="admin-badge admin-badge-neutral">Скрыт</span>}{race.resultRecalculationRequired && <span className="admin-badge admin-badge-warning">Нужен пересчёт</span>}</div>
      <div className="admin-row-actions admin-race-actions"><button className="admin-button-primary admin-button-compact" onClick={() => onOpen(race.id)}>Открыть</button><button className="admin-button-secondary admin-button-compact" onClick={() => setRaceEditor(race)}>Редактировать</button>{race.resultsPublicationStatus === 'PUBLISHED' ? <button className="admin-button-subtle admin-button-compact" onClick={() => setConfirm({ kind: 'draft', id: race.id })}>Вернуть в черновик</button> : <button className="admin-button-subtle admin-button-compact" onClick={() => setConfirm({ kind: 'publish', id: race.id })}>Опубликовать</button>}<button className="admin-button-danger-outline admin-button-compact" disabled={race.resultsPublicationStatus === 'PUBLISHED'} title={race.resultsPublicationStatus === 'PUBLISHED' ? 'Сначала верните результаты в черновик' : undefined} onClick={() => setConfirm({ kind: 'delete', id: race.id })}>Удалить</button></div>
    </article>)}</div> : <div className="admin-empty">У мероприятия пока нет стартов.</div>}
    {raceEditor && <RaceDrawer api={api} eventId={event.id} timeZone={event.timeZone} nextDisplayOrder={nextDisplayOrder(races)} value={raceEditor === 'new' ? null : raceEditor} onClose={() => setRaceEditor(null)} onSaved={async () => { setRaceEditor(null); await onChanged() }} />}
    {confirm && <ConfirmDialog title={confirm.kind === 'draft' ? 'Вернуть результаты в черновик?' : confirm.kind === 'delete' ? `Удалить старт «${confirmedRace?.name ?? ''}»?` : 'Опубликовать результаты?'} description={confirm.kind === 'draft' ? 'Публичный протокол этого старта станет недоступен. Данные не удаляются.' : confirm.kind === 'delete' ? 'Старт будет удалён только из этого мероприятия. Шаблон и другие мероприятия не изменятся.' : 'Backend проверит готовность протокола и необходимость пересчёта.'} confirmLabel={confirm.kind === 'publish' ? 'Опубликовать' : confirm.kind === 'delete' ? 'Удалить старт' : 'Вернуть в черновик'} danger={confirm.kind !== 'publish'} busy={busy} onConfirm={() => void runConfirm()} onClose={() => setConfirm(null)} />}
  </div>
}

function RaceDrawer({ api, eventId, timeZone, nextDisplayOrder: displayOrder, value, onClose, onSaved }: { api: AdminApi; eventId: number; timeZone: string; nextDisplayOrder: number; value: Race | null; onClose: () => void; onSaved: () => Promise<void> }) {
  const [form, setForm] = useState({ name: value?.name ?? '', distanceMeters: value?.distanceMeters === null || value?.distanceMeters === undefined ? '' : String(value.distanceMeters), startsAt: toDateTimeLocal(value?.startsAt ?? null, timeZone), publicVisible: value?.publicVisible ?? true })
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); setError(null); try { const body = { ...form, distanceMeters: form.distanceMeters === '' ? null : Number(form.distanceMeters), startsAt: localDateTimeToIso(form.startsAt, timeZone), displayOrder: value?.displayOrder ?? displayOrder }; if (value) await api.updateRace(eventId, value.id, body); else await api.createRace(eventId, body); await onSaved() } catch (reason) { setError(adminErrorMessage(reason)) } finally { setBusy(false) } }
  return <Drawer title={value ? 'Редактировать старт' : 'Добавить старт'} onClose={onClose}><form className="admin-form" onSubmit={submit}>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<Field label="Название"><input required value={form.name} onChange={(change) => setForm({ ...form, name: change.target.value })} /></Field><div className="admin-form-grid"><Field label="Дистанция, м" hint="Необязательно"><input type="number" min="0" step="0.01" value={form.distanceMeters} onChange={(change) => setForm({ ...form, distanceMeters: change.target.value })} /></Field><Field label="Дата и время старта" hint="Необязательно"><input type="datetime-local" value={form.startsAt} onChange={(change) => setForm({ ...form, startsAt: change.target.value })} /></Field></div><label className="admin-check"><input type="checkbox" checked={form.publicVisible} onChange={(change) => setForm({ ...form, publicVisible: change.target.checked })} /><span>Показывать публично</span></label><div className="admin-form-actions"><button className="admin-button-primary" disabled={busy}>{value ? 'Сохранить' : 'Создать старт'}</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div></form></Drawer>
}

function nextDisplayOrder(races: Race[]): number {
  return races.length ? Math.max(...races.map((race) => race.displayOrder)) + 1 : 0
}

function formatDistance(distanceMeters: number | null): string {
  if (distanceMeters === null) return 'не указана'
  return distanceMeters >= 1000 && distanceMeters % 1000 === 0 ? `${distanceMeters / 1000} км` : `${distanceMeters} м`
}

function formatStart(startsAt: string | null, timeZone: string): string {
  if (!startsAt) return 'не указано'
  return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(new Date(startsAt))
}
