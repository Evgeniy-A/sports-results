import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { EventFilterOptions, EventPhase, EventSummary, PageResponse } from '../api/types'
import { Pagination } from '../components/Pagination'
import { SiteHeader } from '../components/SiteHeader'
import { formatDateRange } from '../utils/format'

const EMPTY_PAGE: PageResponse<EventSummary> = {
  content: [], page: 0, size: 12, totalElements: 0, totalPages: 0, sort: 'startsAt', direction: 'desc',
}

export function EventCatalogPage() {
  const [options, setOptions] = useState<EventFilterOptions>({ years: [], eventSeries: [], cities: [] })
  const [events, setEvents] = useState(EMPTY_PAGE)
  const [year, setYear] = useState('')
  const [seriesId, setSeriesId] = useState('')
  const [city, setCity] = useState('')
  const [date, setDate] = useState('')
  const [phase, setPhase] = useState<'' | EventPhase>('')
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
      phase: phase || undefined,
      page,
      size: 12,
    }, controller.signal).then(setEvents).catch((reason: unknown) => {
      if (!controller.signal.aborted) {
        console.error('Event catalog request failed', reason)
        setError(true)
      }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [year, seriesId, city, date, phase, page])

  const update = (setter: (value: string) => void) => (value: string) => {
    setter(value)
    setPage(0)
  }

  const clearFilters = () => {
    setYear('')
    setSeriesId('')
    setCity('')
    setDate('')
    setPhase('')
    setPage(0)
  }

  return (
    <main>
      <SiteHeader />
      <section className="catalog-hero">
        <div>
          <p className="eyebrow">Единый каталог стартов</p>
          <h1>Календарь<br />мероприятий</h1>
        </div>
        <p className="hero-copy">Будущие старты, информация участнику и официальные протоколы — в одном месте.</p>
      </section>

      <section className="catalog-panel" aria-labelledby="catalog-title">
        <div className="section-heading">
          <div><p className="eyebrow">Календарь и архив</p><h2 id="catalog-title">Мероприятия</h2></div>
          <span className="result-count">Найдено: {events.totalElements.toLocaleString('ru-RU')}</span>
        </div>

        <div className="catalog-filters" aria-label="Фильтры мероприятий">
          <label><span>Период</span><select aria-label="Период" value={phase} onChange={(event) => { setPhase(event.target.value as '' | EventPhase); setPage(0) }}><option value="">Все</option><option value="UPCOMING">Предстоящие</option><option value="ONGOING">Сейчас</option><option value="PAST">Прошедшие</option></select></label>
          <label><span>Год</span><select aria-label="Год" value={year} onChange={(event) => update(setYear)(event.target.value)}><option value="">Все годы</option>{options.years.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>
          <label><span>Серия</span><select aria-label="Серия" value={seriesId} onChange={(event) => update(setSeriesId)(event.target.value)}><option value="">Все серии</option>{options.eventSeries.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}</select></label>
          <label><span>Город</span><select aria-label="Город" value={city} onChange={(event) => update(setCity)(event.target.value)}><option value="">Все города</option>{options.cities.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>
          <label><span>Точная дата</span><input aria-label="Точная дата" type="date" value={date} onChange={(event) => update(setDate)(event.target.value)} /></label>
          <button className="secondary-button" type="button" onClick={clearFilters}>Сбросить</button>
        </div>

        {error && <div className="state-message error" role="alert">Каталог временно недоступен. Попробуйте обновить страницу.</div>}
        {loading && <div className="event-grid" aria-live="polite">{[0, 1, 2].map((item) => <div className="event-card skeleton" key={item} />)}</div>}
        {!loading && !error && events.content.length === 0 && <div className="state-message"><strong>Мероприятий не найдено</strong><br />Измените или сбросьте фильтры.</div>}
        {!loading && !error && events.content.length > 0 && <div className="event-grid">{events.content.map((event) => (
          <a className="event-card" href={`/events/${event.slug}`} key={event.id}>
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
