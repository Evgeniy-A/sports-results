/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { AdminApi } from '../api'
import type { EventSeries, EventSummary, Race, SportFormat } from '../types'
import { AdminLink, navigateAdmin } from '../router'
import { adminErrorMessage } from '../utils'
import { Loadable, StatusBadge } from '../components/AdminUi'
import { EventGeneralTab, FormatsRacesTab } from './EventCoreTabs'
import { CategoriesClustersTab } from './EventCategoriesTab'
import { EventResultsAdminTab } from './EventResultsAdminTab'
import { EventImportTab } from './EventImportTab'
import { EventAwardTab, EventPublicationTab } from './EventPolicyTabs'
import { EventIssuesTab } from './EventIssuesTab'

const TABS = [
  ['main', 'Основное'],
  ['formats', 'Форматы и старты'],
  ['categories', 'Категории и кластеры'],
  ['participants', 'Участники'],
  ['results', 'Результаты'],
  ['import', 'Импорт'],
  ['issues', 'Обращения'],
  ['award', 'Зачёт и награждение'],
  ['publication', 'Публикация'],
] as const

export function AdminEventPage({ api, eventId, requestedTab }: {
  api: AdminApi
  eventId: number
  requestedTab: string | null
}) {
  const [event, setEvent] = useState<EventSummary | null>(null)
  const [races, setRaces] = useState<Race[]>([])
  const [formats, setFormats] = useState<SportFormat[]>([])
  const [series, setSeries] = useState<EventSeries[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const tab = TABS.some(([key]) => key === requestedTab) ? requestedTab! : 'main'

  const reload = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const [eventValue, raceValues, formatValues, seriesValues] = await Promise.all([
        api.event(eventId), api.races(eventId), api.sportFormats(eventId), api.eventSeries(),
      ])
      setEvent(eventValue); setRaces(raceValues); setFormats(formatValues); setSeries(seriesValues)
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
        <div className="admin-heading-badges"><StatusBadge value={event.publicationStatus} /><span>Результаты</span><StatusBadge value={event.resultsPublicationStatus} /></div>
      </div>
      {races.some((race) => race.resultRecalculationRequired) && <button className="admin-recalc-banner" type="button" onClick={() => selectTab('award')}>
        <strong>Настройки изменены.</strong> Необходимо пересчитать категории перед публикацией. <span>Посмотреть изменения →</span>
      </button>}
      <nav className="admin-tabs" aria-label="Разделы мероприятия">
        {TABS.map(([key, label]) => <button key={key} type="button" aria-current={tab === key ? 'page' : undefined} onClick={() => selectTab(key)}>{label}</button>)}
      </nav>
      <section className="admin-tab-content">
        {tab === 'main' && <EventGeneralTab api={api} event={event} series={series} races={races} onChanged={reload} />}
        {tab === 'formats' && <FormatsRacesTab api={api} event={event} formats={formats} races={races} onChanged={reload} />}
        {tab === 'categories' && <CategoriesClustersTab api={api} event={event} races={races} />}
        {tab === 'participants' && <EventResultsAdminTab api={api} event={event} races={races} formats={formats} mode="participants" />}
        {tab === 'results' && <EventResultsAdminTab api={api} event={event} races={races} formats={formats} mode="results" />}
        {tab === 'import' && <EventImportTab api={api} event={event} races={races} />}
        {tab === 'issues' && <EventIssuesTab api={api} event={event} />}
        {tab === 'award' && <EventAwardTab api={api} event={event} races={races} onChanged={reload} />}
        {tab === 'publication' && <EventPublicationTab api={api} event={event} races={races} formats={formats} onChanged={reload} />}
      </section>
    </>}
  </Loadable>
}
