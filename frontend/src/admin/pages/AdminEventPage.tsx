/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { AdminApi } from '../api'
import type { EventSeries, EventSummary, Race } from '../types'
import { AdminLink, navigateAdmin } from '../router'
import { adminErrorMessage } from '../utils'
import { Loadable, StatusBadge } from '../components/AdminUi'
import { EventGeneralTab, EventInformationTab, StartsTab } from './EventCoreTabs'
import { EventResultsAdminTab } from './EventResultsAdminTab'
import { EventImportTab } from './EventImportTab'
import { EventAwardTab } from './EventPolicyTabs'
import { EventIssuesTab } from './EventIssuesTab'
import { EventResultsPublicationTab } from './EventResultsPublicationTab'

const EVENT_TABS = [
  ['main', 'Основное'],
  ['starts', 'Старты'],
  ['award', 'Награждение и категории'],
  ['info', 'Информация участникам'],
] as const

const RESULT_TABS = [
  ['import', 'Загрузка'],
  ['participants', 'Участники'],
  ['results', 'Результаты'],
  ['publication', 'Публикация результатов'],
  ['issues', 'Обращения'],
] as const

const TABS = [...EVENT_TABS, ...RESULT_TABS] as const

export function AdminEventPage({ api, eventId, requestedTab, requestedRaceId }: {
  api: AdminApi
  eventId: number
  requestedTab: string | null
  requestedRaceId: string | null
}) {
  const [event, setEvent] = useState<EventSummary | null>(null)
  const [races, setRaces] = useState<Race[]>([])
  const [series, setSeries] = useState<EventSeries[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const tab = TABS.some(([key]) => key === requestedTab) ? requestedTab! : 'main'
  const section = RESULT_TABS.some(([key]) => key === tab) ? 'results' : 'event'
  const publishedRaceCount = races.filter((race) => race.resultsPublicationStatus === 'PUBLISHED').length
  const resultsStatusLabel = !races.length
    ? 'Старты не созданы'
    : publishedRaceCount === 0
      ? 'Не опубликованы'
      : `${publishedRaceCount} из ${races.length} стартов опубликованы`

  const reload = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const [eventValue, raceValues, seriesValues] = await Promise.all([
        api.event(eventId), api.races(eventId), api.eventSeries(),
      ])
      setEvent(eventValue); setRaces(raceValues); setSeries(seriesValues)
    } catch (reason) {
      setError(adminErrorMessage(reason))
    } finally { setLoading(false) }
  }, [api, eventId])

  useEffect(() => { void reload() }, [reload])

  const selectTab = (next: string) => navigateAdmin(`/admin/events/${eventId}?tab=${next}`)

  return <Loadable loading={loading} error={error} empty={!event}>
    {event && <>
      <div className="admin-event-heading">
        <div><AdminLink href="/admin/events">← Все мероприятия</AdminLink><p className="admin-eyebrow">{event.eventSeriesName}</p><h1>{event.name}</h1><p>{event.location ?? 'Место не указано'} · {event.startsAt ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long', timeZone: event.timeZone }).format(new Date(event.startsAt)) : 'Дата не указана'}</p></div>
        <div className="admin-heading-badges"><span>Мероприятие</span><StatusBadge value={event.publicationStatus} /><span>Результаты</span><strong>{resultsStatusLabel}</strong></div>
      </div>
      {races.some((race) => race.resultRecalculationRequired) && <button className="admin-recalc-banner" type="button" onClick={() => selectTab('award')}>
        <strong>Настройки изменены.</strong> Необходимо пересчитать категории перед публикацией. <span>Посмотреть изменения →</span>
      </button>}
      <nav className="admin-event-sections" aria-label="Основные разделы">
        <button type="button" aria-current={section === 'event' ? 'page' : undefined} onClick={() => selectTab('main')}>Мероприятие</button>
        <button type="button" aria-current={section === 'results' ? 'page' : undefined} onClick={() => selectTab('import')}>Результаты</button>
      </nav>
      <nav className="admin-tabs" aria-label={section === 'event' ? 'Настройки мероприятия' : 'Работа с результатами'}>
        {(section === 'event' ? EVENT_TABS : RESULT_TABS).map(([key, label]) => <button key={key} type="button" aria-current={tab === key ? 'page' : undefined} onClick={() => selectTab(key)}>{label}</button>)}
      </nav>
      <section className="admin-tab-content">
        {tab === 'main' && <EventGeneralTab api={api} event={event} series={series} races={races} onChanged={reload} />}
        {tab === 'starts' && <StartsTab api={api} event={event} races={races} onChanged={reload} onOpen={(raceId) => navigateAdmin(`/admin/events/${eventId}?tab=award&raceId=${raceId}`)} />}
        {tab === 'info' && <EventInformationTab api={api} event={event} />}
        {tab === 'participants' && <EventResultsAdminTab api={api} event={event} races={races} mode="participants" />}
        {tab === 'results' && <EventResultsAdminTab api={api} event={event} races={races} mode="results" />}
        {tab === 'publication' && <EventResultsPublicationTab api={api} event={event} races={races} onChanged={reload} />}
        {tab === 'import' && <EventImportTab api={api} event={event} races={races} />}
        {tab === 'issues' && <EventIssuesTab api={api} event={event} />}
        {tab === 'award' && <EventAwardTab api={api} event={event} races={races} initialRaceId={Number(requestedRaceId) || undefined} onChanged={reload} />}
      </section>
    </>}
  </Loadable>
}
