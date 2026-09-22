import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { EventDetails } from '../api/types'
import { EventInfo } from '../components/EventInfo'
import { EventResults } from '../components/EventResults'
import { SiteHeader } from '../components/SiteHeader'
import { formatDateRange } from '../utils/format'
import { defaultPublicEventSection, type PublicEventSection } from '../utils/publicEventView'

export function PublicEventPage({ slug }: { slug: string }) {
  const [event, setEvent] = useState<EventDetails | null>(null)
  const [section, setSection] = useState<PublicEventSection | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    api.eventBySlug(slug, controller.signal).then((loadedEvent) => {
      setEvent(loadedEvent)
      setSection(defaultPublicEventSection(loadedEvent.races.some((race) => race.resultsPublished)))
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) {
        console.error('Event details request failed', reason)
        setError(true)
      }
    })
    return () => controller.abort()
  }, [slug])

  if (error) return <main><SiteHeader /><div className="state-message standalone">Мероприятие не найдено или ещё не опубликовано.</div></main>
  if (!event || !section) return <main><SiteHeader /><div className="state-message standalone">Загружаем мероприятие…</div></main>

  const resultsPublished = event.races.some((race) => race.resultsPublished)
  const resultsActive = resultsPublished && section === 'results'

  return <main>
    <SiteHeader />
    <section className={`event-hero ${resultsActive ? 'event-results-hero' : 'event-detail-hero'}`}>
      <a className="back-link" href="/">← Все мероприятия</a>
      <p className="eyebrow">{event.eventSeriesName}</p>
      <h1>{event.name}</h1>
      <div className="event-meta"><span>{formatDateRange(event.startsAt, event.endsAt, event.timeZone)}</span><span>{event.location ?? 'Место уточняется'}</span></div>
      {!resultsActive && event.participantInfo?.shortDescription && <p className="event-lead">{event.participantInfo.shortDescription}</p>}
    </section>

    {resultsPublished && <nav className="event-view-tabs" aria-label="Разделы мероприятия" role="tablist">
      <button id="results-tab" type="button" role="tab" aria-selected={section === 'results'} aria-controls="results-view" onClick={() => setSection('results')}>Результаты</button>
      <button id="info-tab" type="button" role="tab" aria-selected={section === 'info'} aria-controls="info-view" onClick={() => setSection('info')}>О мероприятии</button>
    </nav>}

    {resultsPublished && <div id="results-view" className="event-view" role="tabpanel" aria-labelledby="results-tab" hidden={section !== 'results'}>
      <EventResults key={event.id} event={event} />
    </div>}
    <div id="info-view" className="event-view" role={resultsPublished ? 'tabpanel' : undefined} aria-labelledby={resultsPublished ? 'info-tab' : undefined} hidden={resultsPublished && section !== 'info'}>
      <EventInfo event={event} />
    </div>
  </main>
}
