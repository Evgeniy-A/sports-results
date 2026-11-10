import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'
import type { EventDetails, PublicAppealEvent, ResultDetails, ResultInquiryLookup } from '../api/types'
import { ResultInquiryPanel } from '../components/ResultInquiryPanel'
import { ResultIssueDialog } from '../components/ResultIssueDialog'

export function AppealPage() {
  const [events, setEvents] = useState<PublicAppealEvent[]>([])
  const [selectedKey, setSelectedKey] = useState('')
  const [selectedSlug, setSelectedSlug] = useState('')
  const [event, setEvent] = useState<EventDetails | null>(null)
  const [bib, setBib] = useState('')
  const [inquiry, setInquiry] = useState<ResultInquiryLookup | null>(null)
  const [result, setResult] = useState<ResultDetails | null>(null)
  const [correctionOpen, setCorrectionOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const selectedGroup = useMemo(
    () => events.find((candidate) => candidate.key === selectedKey) ?? null,
    [events, selectedKey],
  )

  useEffect(() => {
    const controller = new AbortController()
    api.appealEvents(controller.signal)
      .then(setEvents)
      .catch(() => setError('Не удалось загрузить мероприятия. Попробуйте ещё раз позже.'))
      .finally(() => setLoading(false))
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!selectedSlug) return
    const controller = new AbortController()
    api.eventBySlug(selectedSlug, controller.signal)
      .then(setEvent)
      .catch(() => setError('Не удалось открыть выбранное мероприятие.'))
    return () => controller.abort()
  }, [selectedSlug])

  const chooseGroup = (key: string) => {
    setSelectedKey(key); setEvent(null); setInquiry(null); setResult(null); setError(null)
    const group = events.find((candidate) => candidate.key === key)
    setSelectedSlug(group?.cities.length === 1 ? group.cities[0].eventSlug : '')
  }

  const runLookup = async (raceId?: number) => {
    if (!event || !bib.trim()) return
    setSearching(true); setError(null); setInquiry(null); setResult(null)
    try {
      const found = await api.resultInquiry(event.id, bib.trim(), raceId)
      setInquiry(found)
      if (found.lookupState === 'RESULT_PUBLIC' && found.publicResultId) {
        setResult(await api.result(found.publicResultId))
      }
    } catch { setError('Не удалось найти участника. Проверьте подключение и попробуйте ещё раз.') }
    finally { setSearching(false) }
  }

  const lookup = async (submit: FormEvent) => {
    submit.preventDefault()
    await runLookup()
  }

  return <main className="appeal-page">
    <header className="appeal-header"><a href="/">Sports Results</a><span>Обращение по результатам</span></header>
    <section className="appeal-card">
      <p className="eyebrow">Поддержка участников</p>
      <h1>Обращение по результатам</h1>
      <p>Выберите мероприятие и город, затем найдите участника по стартовому номеру.</p>
      {loading && <p role="status">Загружаем мероприятия…</p>}
      {!loading && events.length === 0 && <div className="state-message">Сейчас нет мероприятий с открытым приёмом обращений.</div>}
      {events.length > 0 && <div className="appeal-steps">
        <label><span>1. Мероприятие</span><select value={selectedKey} onChange={(change) => chooseGroup(change.target.value)}><option value="">Выберите мероприятие</option>{events.map((candidate) => <option key={candidate.key} value={candidate.key}>{candidate.title}</option>)}</select></label>
        {selectedGroup && selectedGroup.cities.length > 1 && <label><span>2. Город</span><select value={selectedSlug} onChange={(change) => { setSelectedSlug(change.target.value); setEvent(null); setInquiry(null); setResult(null); setError(null) }}><option value="">Выберите город</option>{selectedGroup.cities.map((city) => <option key={city.eventSlug} value={city.eventSlug}>{city.city}</option>)}</select></label>}
        {event && <form className="appeal-bib-form" onSubmit={lookup}><label><span>{selectedGroup?.cities.length === 1 ? '2' : '3'}. Стартовый номер</span><input required maxLength={64} value={bib} onChange={(change) => setBib(change.target.value)} /></label><button type="submit" disabled={searching}>{searching ? 'Ищем…' : 'Найти участника'}</button></form>}
      </div>}
      {error && <p className="form-error" role="alert">{error}</p>}
      {event && inquiry?.lookupState === 'RESULT_PUBLIC' && result && <section className="inquiry-followup-card"><div><strong>{inquiry.participantDisplayName}</strong><p>{inquiry.startDisplayName ?? inquiry.raceDisplayName} · стартовый номер {inquiry.bib}</p></div><button type="button" onClick={() => setCorrectionOpen(true)}>Сообщить об ошибке</button></section>}
      {event && inquiry && inquiry.lookupState !== 'RESULT_PUBLIC' && <ResultInquiryPanel
        eventId={event.id}
        eventName={event.name}
        eventTimeZone={event.timeZone}
        inquiry={inquiry}
        selectingRace={searching}
        onSelectRace={runLookup}
      />}
    </section>
    {event && inquiry && result && correctionOpen && <ResultIssueDialog
      kind="RESULT_CORRECTION"
      eventId={event.id}
      eventName={event.name}
      eventTimeZone={event.timeZone}
      inquiry={inquiry}
      result={result}
      onClose={() => setCorrectionOpen(false)}
    />}
  </main>
}
