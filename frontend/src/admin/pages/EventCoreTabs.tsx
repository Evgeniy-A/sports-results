/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { EventSeries, EventSummary, ParticipantInfo, Race, ResultInquirySettings, SportFormat } from '../types'
import { adminErrorMessage, slugify } from '../utils'
import { localDateTimeToIso, toDateTimeLocal } from '../time'
import { AdminNotice, ConfirmDialog, Drawer, Field, StatusBadge } from '../components/AdminUi'
import { TimeZoneCombobox } from '../components/TimeZoneCombobox'
import { EventContentManager } from './EventContentManager'

export function EventGeneralTab({ api, event, series, races, onChanged }: {
  api: AdminApi
  event: EventSummary
  series: EventSeries[]
  races: Race[]
  onChanged: () => Promise<void>
}) {
  const [form, setForm] = useState(() => eventForm(event))
  const [inquiry, setInquiry] = useState<ResultInquirySettings | null>(null)
  const [info, setInfo] = useState<ParticipantInfo | null>(null)
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)

  useEffect(() => {
    setForm(eventForm(event)); setDirty(false)
    Promise.all([api.resultInquiry(event.id), api.participantInfo(event.id)])
      .then(([inquiryValue, infoValue]) => { setInquiry(inquiryValue); setInfo(infoValue) })
      .catch((reason) => setMessage({ tone: 'danger', text: adminErrorMessage(reason) }))
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
        eventSeriesId: Number(form.eventSeriesId), name: form.name, slug: form.slug,
        startsAt: localDateTimeToIso(form.startsAt, form.timeZone), endsAt: localDateTimeToIso(form.endsAt, form.timeZone),
        location: form.location || null, timeZone: form.timeZone,
        publicationStatus: event.publicationStatus,
      })
      setDirty(false); setMessage({ tone: 'success', text: 'Изменения сохранены.' }); await onChanged()
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(null) }
  }

  const saveInquiry = async (submit: FormEvent) => {
    submit.preventDefault(); if (!inquiry) return; setBusy('inquiry'); setMessage(null)
    try {
      const saved = await api.updateResultInquiry(event.id, inquiry)
      setInquiry(saved); setMessage({ tone: 'success', text: 'Настройки обращений сохранены.' })
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(null) }
  }

  const saveInfo = async (submit: FormEvent) => {
    submit.preventDefault(); if (!info) return; setBusy('info'); setMessage(null)
    try {
      const saved = await api.updateParticipantInfo(event.id, {
        shortDescription: info.shortDescription, venueName: info.venueName,
        venueAddress: info.venueAddress, locationDescription: info.locationDescription,
        latitude: info.latitude, longitude: info.longitude, additionalInfo: info.additionalInfo,
      })
      setInfo(saved); setMessage({ tone: 'success', text: 'Информация для участников сохранена.' })
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(null) }
  }

  return <div className="admin-stack">
    {message && <AdminNotice tone={message.tone}>{message.text}</AdminNotice>}
    <section className="admin-card"><div className="admin-card-heading"><div><h2>Основные данные</h2><p>Название, даты и место мероприятия.</p></div></div>
      <form className="admin-form" onSubmit={saveEvent}>
        <div className="admin-form-grid"><Field label="Название"><input required value={form.name} onChange={(change) => set('name', change.target.value)} /></Field><Field label="Адрес в URL"><input required pattern="[a-z0-9]+(?:-[a-z0-9]+)*" value={form.slug} onChange={(change) => set('slug', change.target.value)} /></Field></div>
        <div className="admin-form-grid"><Field label="Начало"><input type="datetime-local" value={form.startsAt} onChange={(change) => set('startsAt', change.target.value)} /></Field><Field label="Окончание"><input type="datetime-local" value={form.endsAt} onChange={(change) => set('endsAt', change.target.value)} /></Field></div>
        <div className="admin-form-grid"><Field label="Место"><input value={form.location} onChange={(change) => set('location', change.target.value)} /></Field><Field label="Часовой пояс" hint="Выберите город с подходящим местным временем"><TimeZoneCombobox value={form.timeZone} location={form.location} onChange={(value) => set('timeZone', value)} /></Field></div>
        <Field label="EventSeries"><select value={form.eventSeriesId} onChange={(change) => set('eventSeriesId', change.target.value)}>{series.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
        {resultAffecting && <AdminNotice tone="warning"><strong>Настройка влияет на итоговый протокол.</strong>{publishedRace && <> Чтобы изменить дату или часовой пояс, сначала верните опубликованные Race в черновик.</>}</AdminNotice>}
        <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy === 'event' || !dirty}>Сохранить</button></div>
      </form>
    </section>

    {info && <section className="admin-card"><div className="admin-card-heading"><div><h2>Информация для участников</h2><p>Публичное описание и сведения о площадке.</p></div></div><form className="admin-form" onSubmit={saveInfo}>
      <Field label="Краткое описание"><textarea value={info.shortDescription ?? ''} onChange={(change) => setInfo({ ...info, shortDescription: change.target.value || null })} /></Field>
      <div className="admin-form-grid"><Field label="Площадка"><input value={info.venueName ?? ''} onChange={(change) => setInfo({ ...info, venueName: change.target.value || null })} /></Field><Field label="Адрес площадки"><input value={info.venueAddress ?? ''} onChange={(change) => setInfo({ ...info, venueAddress: change.target.value || null })} /></Field></div>
      <Field label="Как добраться"><textarea value={info.locationDescription ?? ''} onChange={(change) => setInfo({ ...info, locationDescription: change.target.value || null })} /></Field>
      <Field label="Дополнительная информация"><textarea value={info.additionalInfo ?? ''} onChange={(change) => setInfo({ ...info, additionalInfo: change.target.value || null })} /></Field>
      <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy === 'info'}>Сохранить информацию</button></div>
    </form></section>}

    {inquiry && <section className="admin-card"><div className="admin-card-heading"><div><h2>Обращения по результатам</h2><p>Публичная форма работает только в рассчитанное backend окно.</p></div></div><form className="admin-form" onSubmit={saveInquiry}>
      <label className="admin-check"><input type="checkbox" checked={inquiry.enabled} onChange={(change) => setInquiry({ ...inquiry, enabled: change.target.checked })} /><span>Разрешить обращения</span></label>
      <div className="admin-form-grid"><Field label="Окно после окончания, дней"><input type="number" min="1" required={inquiry.enabled} value={inquiry.windowDays ?? ''} onChange={(change) => setInquiry({ ...inquiry, windowDays: change.target.value ? Number(change.target.value) : null })} /></Field><Field label="Email организатора"><input type="email" required={inquiry.enabled} value={inquiry.email ?? ''} onChange={(change) => setInquiry({ ...inquiry, email: change.target.value || null })} /></Field></div>
      <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy === 'inquiry'}>Сохранить настройки</button></div>
    </form></section>}
    <EventContentManager api={api} eventId={event.id} />
  </div>
}

function eventForm(event: EventSummary) {
  return {
    eventSeriesId: String(event.eventSeriesId), name: event.name, slug: event.slug,
    startsAt: toDateTimeLocal(event.startsAt, event.timeZone), endsAt: toDateTimeLocal(event.endsAt, event.timeZone),
    location: event.location ?? '', timeZone: event.timeZone,
  }
}

export function FormatsRacesTab({ api, event, formats, races, onChanged }: {
  api: AdminApi
  event: EventSummary
  formats: SportFormat[]
  races: Race[]
  onChanged: () => Promise<void>
}) {
  const [formatEditor, setFormatEditor] = useState<SportFormat | 'new' | null>(null)
  const [raceEditor, setRaceEditor] = useState<Race | 'new' | null>(null)
  const [confirm, setConfirm] = useState<{ kind: 'format' | 'draft' | 'publish'; id: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)
  const raceGroups = useMemo(() => new Map(formats.map((format) => [format.id, races.filter((race) => race.sportFormatId === format.id)])), [formats, races])

  const runConfirm = async () => {
    if (!confirm) return; setBusy(true); setMessage(null)
    try {
      if (confirm.kind === 'format') await api.deleteSportFormat(event.id, confirm.id)
      if (confirm.kind === 'draft') await api.draftRace(event.id, confirm.id, 'Изменено через Admin UI')
      if (confirm.kind === 'publish') await api.publishRace(event.id, confirm.id)
      setMessage({ tone: 'success', text: confirm.kind === 'publish' ? 'Race опубликован.' : confirm.kind === 'draft' ? 'Race возвращён в черновик.' : 'Формат удалён.' })
      setConfirm(null); await onChanged()
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(false) }
  }

  return <div className="admin-stack">
    {message && <AdminNotice tone={message.tone}>{message.text}</AdminNotice>}
    <div className="admin-section-toolbar"><div><h2>Спортивные форматы и Race</h2><p>Data-driven иерархия мероприятия. Формат не является уровнем зачёта.</p></div><div><button className="admin-button-secondary" onClick={() => setFormatEditor('new')}>Добавить формат</button><button className="admin-button-primary" onClick={() => setRaceEditor('new')} disabled={!formats.length}>Добавить Race</button></div></div>
    {!formats.length && <div className="admin-empty">Сначала создайте спортивный формат.</div>}
    {formats.map((format) => <section className="admin-card" key={format.id}>
      <div className="admin-card-heading"><div><h3>{format.displayName}</h3><p>{format.code ?? 'Без кода'} · {format.publicVisible ? 'публичный' : 'скрыт публично'}</p></div><div className="admin-row-actions"><StatusBadge value={format.publicVisible ? 'PUBLISHED' : 'DRAFT'} /><button className="admin-link-button" onClick={() => setFormatEditor(format)}>Изменить</button><button className="admin-link-button danger" onClick={() => setConfirm({ kind: 'format', id: format.id })}>Удалить</button></div></div>
      {(raceGroups.get(format.id) ?? []).length ? <div className="admin-race-grid">{(raceGroups.get(format.id) ?? []).map((race) => <article className="admin-race-card" key={race.id}>
        <div><p className="admin-eyebrow">{race.sourceCode}</p><h4>{race.name}</h4><p>{race.distanceMeters === null ? 'Без дистанции' : `${race.distanceMeters} м`} · {race.entryMode}</p></div>
        <div className="admin-inline-badges"><StatusBadge value={race.resultsPublicationStatus} />{!race.publicVisible && <span className="admin-badge admin-badge-neutral">Скрыт</span>}{race.resultRecalculationRequired && <span className="admin-badge admin-badge-warning">Нужен пересчёт</span>}</div>
        <div className="admin-row-actions"><button className="admin-link-button" onClick={() => setRaceEditor(race)}>Настройки</button>{race.resultsPublicationStatus === 'PUBLISHED' ? <button className="admin-link-button danger" onClick={() => setConfirm({ kind: 'draft', id: race.id })}>Вернуть в черновик</button> : <button className="admin-link-button" onClick={() => setConfirm({ kind: 'publish', id: race.id })}>Опубликовать</button>}</div>
      </article>)}</div> : <div className="admin-empty compact">В формате пока нет Race.</div>}
    </section>)}
    {formatEditor && <FormatDrawer api={api} eventId={event.id} value={formatEditor === 'new' ? null : formatEditor} onClose={() => setFormatEditor(null)} onSaved={async () => { setFormatEditor(null); await onChanged() }} />}
    {raceEditor && <RaceDrawer api={api} eventId={event.id} timeZone={event.timeZone} formats={formats} value={raceEditor === 'new' ? null : raceEditor} onClose={() => setRaceEditor(null)} onSaved={async () => { setRaceEditor(null); await onChanged() }} />}
    {confirm && <ConfirmDialog title={confirm.kind === 'format' ? 'Удалить формат?' : confirm.kind === 'draft' ? 'Вернуть результаты в черновик?' : 'Опубликовать результаты?'} description={confirm.kind === 'format' ? 'Формат можно удалить только если в нём нет Race.' : confirm.kind === 'draft' ? 'Публичный протокол этого Race станет недоступен. Данные не удаляются.' : 'Backend проверит готовность протокола и необходимость пересчёта.'} confirmLabel={confirm.kind === 'publish' ? 'Опубликовать' : confirm.kind === 'draft' ? 'Вернуть в черновик' : 'Удалить'} danger={confirm.kind !== 'publish'} busy={busy} onConfirm={() => void runConfirm()} onClose={() => setConfirm(null)} />}
  </div>
}

function FormatDrawer({ api, eventId, value, onClose, onSaved }: { api: AdminApi; eventId: number; value: SportFormat | null; onClose: () => void; onSaved: () => Promise<void> }) {
  const [form, setForm] = useState({ code: value?.code ?? '', sourceName: value?.sourceName ?? '', displayName: value?.displayName ?? '', displayOrder: value?.displayOrder ?? 0, publicVisible: value?.publicVisible ?? true })
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); setError(null); try { const body = { ...form, code: form.code || null, sourceName: form.sourceName || null }; if (value) await api.updateSportFormat(eventId, value.id, body); else await api.createSportFormat(eventId, body); await onSaved() } catch (reason) { setError(adminErrorMessage(reason)) } finally { setBusy(false) } }
  return <Drawer title={value ? 'Изменить формат' : 'Новый спортивный формат'} onClose={onClose}><form className="admin-form" onSubmit={submit}>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<Field label="Название"><input required value={form.displayName} onChange={(change) => setForm({ ...form, displayName: change.target.value })} /></Field><div className="admin-form-grid"><Field label="Код"><input value={form.code} onChange={(change) => setForm({ ...form, code: change.target.value })} /></Field><Field label="Исходное название"><input value={form.sourceName} onChange={(change) => setForm({ ...form, sourceName: change.target.value })} /></Field></div><Field label="Порядок"><input type="number" min="0" value={form.displayOrder} onChange={(change) => setForm({ ...form, displayOrder: Number(change.target.value) })} /></Field><label className="admin-check"><input type="checkbox" checked={form.publicVisible} onChange={(change) => setForm({ ...form, publicVisible: change.target.checked })} /><span>Показывать публично</span></label><div className="admin-form-actions"><button className="admin-button-primary" disabled={busy}>Сохранить</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div></form></Drawer>
}

function RaceDrawer({ api, eventId, timeZone, formats, value, onClose, onSaved }: { api: AdminApi; eventId: number; timeZone: string; formats: SportFormat[]; value: Race | null; onClose: () => void; onSaved: () => Promise<void> }) {
  const [form, setForm] = useState({ sourceCode: value?.sourceCode ?? '', name: value?.name ?? '', slug: value?.slug ?? '', distanceMeters: value?.distanceMeters === null || value?.distanceMeters === undefined ? '' : String(value.distanceMeters), startsAt: toDateTimeLocal(value?.startsAt ?? null, timeZone), entryMode: value?.entryMode ?? 'UNKNOWN', displayOrder: value?.displayOrder ?? 0, sportFormatId: String(value?.sportFormatId ?? formats[0]?.id ?? ''), publicVisible: value?.publicVisible ?? true })
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); setError(null); try { const body = { ...form, distanceMeters: form.distanceMeters === '' ? null : Number(form.distanceMeters), startsAt: localDateTimeToIso(form.startsAt, timeZone), sportFormatId: Number(form.sportFormatId) }; if (value) await api.updateRace(eventId, value.id, body); else await api.createRace(eventId, body); await onSaved() } catch (reason) { setError(adminErrorMessage(reason)) } finally { setBusy(false) } }
  return <Drawer title={value ? 'Настройки Race' : 'Новый Race'} onClose={onClose}><form className="admin-form" onSubmit={submit}>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<Field label="Название"><input required value={form.name} onChange={(change) => setForm({ ...form, name: change.target.value, slug: form.slug || slugify(change.target.value) })} /></Field><div className="admin-form-grid"><Field label="Source code"><input required value={form.sourceCode} onChange={(change) => setForm({ ...form, sourceCode: change.target.value })} /></Field><Field label="Адрес в URL"><input required pattern="[a-z0-9]+(?:-[a-z0-9]+)*" value={form.slug} onChange={(change) => setForm({ ...form, slug: change.target.value })} /></Field></div><Field label="Спортивный формат"><select required value={form.sportFormatId} onChange={(change) => setForm({ ...form, sportFormatId: change.target.value })}>{formats.map((format) => <option key={format.id} value={format.id}>{format.displayName}</option>)}</select></Field><div className="admin-form-grid"><Field label="Дистанция, м"><input type="number" min="0" step="0.01" value={form.distanceMeters} onChange={(change) => setForm({ ...form, distanceMeters: change.target.value })} /></Field><Field label="Время старта"><input type="datetime-local" value={form.startsAt} onChange={(change) => setForm({ ...form, startsAt: change.target.value })} /></Field></div><div className="admin-form-grid"><Field label="Тип участия"><select value={form.entryMode} onChange={(change) => setForm({ ...form, entryMode: change.target.value as Race['entryMode'] })}><option value="INDIVIDUAL">Индивидуальный</option><option value="TEAM">Командный</option><option value="MIXED">Смешанный</option><option value="UNKNOWN">Не задан</option></select></Field><Field label="Порядок"><input type="number" min="0" value={form.displayOrder} onChange={(change) => setForm({ ...form, displayOrder: Number(change.target.value) })} /></Field></div><label className="admin-check"><input type="checkbox" checked={form.publicVisible} onChange={(change) => setForm({ ...form, publicVisible: change.target.checked })} /><span>Показывать Race публично</span></label><div className="admin-form-actions"><button className="admin-button-primary" disabled={busy}>Сохранить</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div></form></Drawer>
}
