/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { EventSeries, ResultInquiryConfiguration } from '../types'
import { AdminLink, navigateAdmin } from '../router'
import { setAdminNavigationBlocker } from '../navigationGuard'
import { adminErrorMessage } from '../utils'
import { localDateTimeToIso } from '../time'
import { AdminNotice, Field, Loadable } from '../components/AdminUi'
import { AdminCreationHeader, AdminCreationSection } from '../components/AdminCreationLayout'
import { InlineTemplateCreator } from '../components/InlineTemplateCreator'
import { TimeZoneCombobox } from '../components/TimeZoneCombobox'
import { EventStartComposer } from '../components/EventStartComposer'
import { ResultInquirySettingsFields } from '../components/ResultInquirySettingsFields'
import { draftsFromTemplate, startCommands } from '../eventStartDrafts'
import type { EventStartDraft } from '../eventStartDrafts'
import {
  calculateDraftResultInquiryDeadline,
  formatResultInquiryDeadline,
  mergeResultInquiryDraft,
} from '../resultInquirySettings'

const CREATION_STEPS = [
  { number: '01', label: 'Основное' },
  { number: '02', label: 'Старты' },
  { number: '03', label: 'Обращения' },
  { number: '04', label: 'Проверка' },
]
const LEAVE_EVENT_CREATION_MESSAGE = 'Есть несохранённые данные мероприятия. Покинуть страницу без сохранения?'

export function AdminCreateEventPage({ api }: { api: AdminApi }) {
  const [templates, setTemplates] = useState<EventSeries[]>([])
  const [name, setName] = useState('')
  const [templateId, setTemplateId] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [location, setLocation] = useState('')
  const [timeZone, setTimeZone] = useState('Europe/Moscow')
  const [starts, setStarts] = useState<EventStartDraft[]>([])
  const [inquiry, setInquiry] = useState<ResultInquiryConfiguration>(emptyInquirySettings)
  const [startsLoading, setStartsLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [preview, setPreview] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [userModified, setUserModified] = useState(false)
  const allowNavigation = useRef(false)

  useEffect(() => {
    api.eventSeries().then((loaded) => {
      setTemplates(loaded)
      const initial = loaded[0]
      if (initial) {
        setTemplateId(String(initial.id))
        setInquiry(initial.resultInquiryDefaults ?? emptyInquirySettings())
      }
    }).catch((reason) => setError(adminErrorMessage(reason))).finally(() => setLoading(false))
  }, [api])

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

  const dirty = Boolean(name || startsAt || endsAt || location || userModified)
  useEffect(() => {
    if (!dirty) return
    return setAdminNavigationBlocker(() => allowNavigation.current || window.confirm(LEAVE_EVENT_CREATION_MESSAGE))
  }, [dirty])
  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

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
      allowNavigation.current = true
      navigateAdmin(`/admin/events/${created.event.id}`)
    } catch (reason) { setError(adminErrorMessage(reason)); setPreview(false) }
    finally { setBusy(false) }
  }
  const templateCreated = (created: EventSeries) => {
    setTemplates((current) => [...current, created].sort((left, right) => left.name.localeCompare(right.name, 'ru')))
    setTemplateId(String(created.id)); setUserModified(true)
  }
  const changeTemplate = (value: string) => { setTemplateId(value); setUserModified(true) }
  const changeStarts = (value: EventStartDraft[]) => { setStarts(value); setUserModified(true) }
  const changeInquiry = (patch: Partial<ResultInquiryConfiguration>) => {
    setInquiry((current) => mergeResultInquiryDraft(current, patch)); setUserModified(true)
  }
  const changeTimeZone = (value: string) => { setTimeZone(value); setUserModified(true) }

  const selectedStarts = startCommands(starts)
  const inquiryDeadline = calculateDraftResultInquiryDeadline(
    startsAt, endsAt, inquiry.deadlineMode, inquiry.windowDays, inquiry.fixedDate, timeZone,
  )

  return <>
    <AdminCreationHeader
      title="Новое мероприятие"
      description="Заполните основные данные, настройте старты и проверьте мероприятие перед созданием."
      steps={CREATION_STEPS}
      activeStep={preview ? '04' : '01'}
    />
    <Loadable loading={loading} error={null}>
      <form className="admin-creation-form" onSubmit={showPreview}>
        {error && <AdminNotice tone="danger">{error}</AdminNotice>}
        {!preview ? <>
          <AdminCreationSection number="01" title="Основные данные" description="Название, шаблон, даты и место проведения.">
            <div className="admin-creation-fields">
              <Field label="Название"><input required maxLength={255} value={name} onChange={(event) => setName(event.target.value)} /></Field>
              <Field label="Шаблон" hint="Шаблон объединяет мероприятия одного бренда и может использоваться повторно."><select required value={templateId} onChange={(event) => changeTemplate(event.target.value)}><option value="">Выберите шаблон</option>{templates.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
              <InlineTemplateCreator api={api} initiallyOpen={templates.length === 0} onCreated={templateCreated} />
              <div className="admin-form-grid"><Field label="Начало"><input type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /></Field><Field label="Окончание"><input type="datetime-local" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} /></Field></div>
              <Field label="Город / место"><input maxLength={255} value={location} onChange={(event) => setLocation(event.target.value)} /></Field>
              <Field label="Часовой пояс" hint="Выберите город с подходящим местным временем"><TimeZoneCombobox value={timeZone} location={location} onChange={changeTimeZone} /></Field>
            </div>
          </AdminCreationSection>
          <AdminCreationSection number="02" title="Старты" description="Старты будут созданы вместе с мероприятием.">
            {startsLoading ? <p className="admin-creation-loading">Загружаем старты шаблона…</p> : <EventStartComposer value={starts} onChange={changeStarts} compactHeader />}
          </AdminCreationSection>
          <AdminCreationSection number="03" title="Обращения по результатам" description="Укажите, смогут ли участники обращаться по опубликованным результатам." muted={!inquiry.enabled}>
            <div className="admin-inquiry-create-fields">
              <ResultInquirySettingsFields
                value={inquiry}
                onChange={changeInquiry}
                deadlinePreview={inquiryDeadline ? formatResultInquiryDeadline(inquiryDeadline, timeZone) : null}
                eventDateKnown={Boolean(endsAt || startsAt)}
              />
              <p className="admin-form-help">После окончания срока участники не смогут создавать новые обращения. Уже созданные обращения останутся доступны для обработки.</p>
            </div>
          </AdminCreationSection>
          <div className="admin-creation-actions"><div><strong>Следующий шаг — проверка</strong><span>Убедитесь, что обязательные поля заполнены.</span></div><div><AdminLink className="admin-button-secondary" href="/admin/events">Отмена</AdminLink><button className="admin-button-primary" disabled={busy || startsLoading} type="submit">Продолжить</button></div></div>
        </> : <AdminCreationSection number="04" title="Проверка" description="Проверьте данные перед созданием мероприятия.">
          <section className="admin-event-preview">
            <div className="admin-preview-summary"><p className="admin-eyebrow">Готово к созданию</p><h2>{name}</h2><dl><div><dt>Шаблон</dt><dd>{templates.find((item) => String(item.id) === templateId)?.name}</dd></div><div><dt>Город / место</dt><dd>{location || '—'}</dd></div><div><dt>Обращения</dt><dd>{inquiry.enabled ? `${inquiry.deadlineMode === 'FIXED_DATE' ? 'до выбранной даты' : `${inquiry.windowDays} дн. после мероприятия`}, ${formatResultInquiryDeadline(inquiryDeadline, timeZone)}` : 'приём выключен'}</dd></div></dl></div>
            <div><h3>Будут созданы старты</h3>{selectedStarts.length ? <ol>{selectedStarts.map((start) => <li key={`${start.templateStartId}-${start.sourceCode}-${start.name}`}><strong>{start.name}</strong><span>{start.distanceMeters === null ? 'Дистанция не указана' : `${start.distanceMeters} м`} · Награждение: {start.awardPolicy ? 'настроено' : 'штатные настройки'}</span></li>)}</ol> : <p>Мероприятие будет создано без стартов.</p>}</div>
            <div className="admin-creation-actions"><div><strong>Все данные готовы</strong><span>После создания откроется карточка мероприятия.</span></div><div><button className="admin-button-secondary" type="button" disabled={busy} onClick={() => setPreview(false)}>Назад</button><button className="admin-button-primary" type="button" disabled={busy} onClick={() => void create()}>{busy ? 'Создаём…' : 'Создать мероприятие'}</button></div></div>
          </section>
        </AdminCreationSection>}
      </form>
    </Loadable>
  </>
}

function emptyInquirySettings(): ResultInquiryConfiguration {
  return { enabled: false, deadlineMode: 'AFTER_EVENT_DAYS', windowDays: null, fixedDate: null, email: null }
}
