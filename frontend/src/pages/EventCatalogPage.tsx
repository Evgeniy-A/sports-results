import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { EventFilterOptions, EventSummary, PageResponse } from '../api/types'
import { Pagination } from '../components/Pagination'
import { SiteHeader } from '../components/SiteHeader'
import { formatDateRange } from '../utils/format'
import { PUBLIC_CATALOGS, type PublicCatalogMode } from '../utils/publicCatalog'

const EMPTY_PAGE: PageResponse<EventSummary> = {
  content: [], page: 0, size: 12, totalElements: 0, totalPages: 0, sort: 'startsAt', direction: 'desc',
}

export function EventCatalogPage({ mode }: { mode: PublicCatalogMode }) {
  const catalog = PUBLIC_CATALOGS[mode]
  const [options, setOptions] = useState<EventFilterOptions>({ years: [], eventSeries: [], cities: [] })
  const [events, setEvents] = useState(EMPTY_PAGE)
  const [year, setYear] = useState('')
  const [seriesId, setSeriesId] = useState('')
  const [city, setCity] = useState('')
  const [date, setDate] = useState('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    api.eventFilterOptions({}, controller.signal).then((loaded) => {
      setOptions(loaded)
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) console.error('Event filter options request failed', reason)
    })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    // oxlint-disable-next-line react/set-state-in-effect -- loading state starts with the external request.
    setLoading(true)
    setError(false)
    api.events({
      year: year ? Number(year) : undefined,
      eventSeriesId: seriesId ? Number(seriesId) : undefined,
      city: city || undefined,
      date: date || undefined,
      phase: catalog.phase,
      page,
      size: 12,
    }, controller.signal).then(setEvents).catch((reason: unknown) => {
      if (!controller.signal.aborted) {
        console.error('Event catalog request failed', reason)
        setError(true)
      }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [year, seriesId, city, date, catalog.phase, page])

  const update = (setter: (value: string) => void) => (value: string) => {
    setter(value)
    setPage(0)
  }

  const clearFilters = () => {
    setYear('')
    setSeriesId('')
    setCity('')
    setDate('')
    setPage(0)
  }

  return (
    <main>
      <SiteHeader activeSection={mode} />
      <section className="catalog-hero">
        <div>
          <p className="eyebrow">{catalog.eyebrow}</p>
          <h1>{catalog.title}</h1>
        </div>
        <p className="hero-copy">{catalog.copy}</p>
      </section>

      <section className="catalog-panel" aria-labelledby="catalog-title">
        <div className="section-heading">
          <div><p className="eyebrow">{catalog.sectionEyebrow}</p><h2 id="catalog-title">{catalog.sectionTitle}</h2></div>
          <span className="result-count">Найдено: {events.totalElements.toLocaleString('ru-RU')}</span>
        </div>

        <div className="catalog-filters" aria-label="Фильтры мероприятий">
          <label><span>Год</span><select aria-label="Год" value={year} onChange={(event) => update(setYear)(event.target.value)}><option value="">Все годы</option>{options.years.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>
          <label><span>Серия</span><select aria-label="Серия" value={seriesId} onChange={(event) => update(setSeriesId)(event.target.value)}><option value="">Все серии</option>{options.eventSeries.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}</select></label>
          <label><span>Город</span><select aria-label="Город" value={city} onChange={(event) => update(setCity)(event.target.value)}><option value="">Все города</option>{options.cities.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>
          <label><span>Точная дата</span><input aria-label="Точная дата" type="date" value={date} onChange={(event) => update(setDate)(event.target.value)} /></label>
          <button className="secondary-button" type="button" onClick={clearFilters}>Сбросить</button>
        </div>

        {error && <div className="state-message error" role="alert">Каталог временно недоступен. Попробуйте обновить страницу.</div>}
        {loading && <div className="event-grid" aria-live="polite">{[0, 1, 2].map((item) => <div className="event-card skeleton" key={item} />)}</div>}
        {!loading && !error && events.content.length === 0 && <div className="state-message"><strong>{catalog.emptyTitle}</strong><br />{catalog.emptyCopy}</div>}
        {!loading && !error && events.content.length > 0 && <div className="event-grid">{events.content.map((event) => (
          <a className="event-card" href={mode === 'results' ? `/events/${event.slug}/results` : `/events/${event.slug}`} key={event.id}>
            <div className="event-card-top"><span>{event.eventSeriesName}</span><i>{event.phase === 'UPCOMING' ? 'Предстоит' : event.phase === 'ONGOING' ? 'Сейчас' : 'Архив'} ↗</i></div>
            <div><time>{formatDateRange(event.startsAt, event.endsAt, event.timeZone)}</time><h3>{event.name}</h3>{event.shortDescription && <small>{event.shortDescription}</small>}</div>
            <p><span className="location-dot" />{event.venueName ?? event.location ?? 'Место уточняется'}{event.resultsPublished && <b>Результаты</b>}</p>
          </a>
        ))}</div>}
        <Pagination page={events.page} totalPages={events.totalPages} onChange={setPage} />
      </section>
    </main>
  )
}
