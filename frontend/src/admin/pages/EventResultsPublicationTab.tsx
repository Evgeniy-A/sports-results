/* oxlint-disable react/set-state-in-effect -- remote result counts are loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { AdminApi } from '../api'
import type { EventSummary, Race } from '../types'
import { adminErrorMessage } from '../utils'
import { AdminNotice, ConfirmDialog, StatusBadge } from '../components/AdminUi'

interface Props {
  api: AdminApi
  event: EventSummary
  races: Race[]
  onChanged: () => Promise<void>
}

export function EventResultsPublicationTab({ api, event, races, onChanged }: Props) {
  const [counts, setCounts] = useState<Record<number, number>>({})
  const [loadingCounts, setLoadingCounts] = useState(true)
  const [confirmRace, setConfirmRace] = useState<Race | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)

  const loadCounts = useCallback(async () => {
    setLoadingCounts(true)
    try {
      const pages = await Promise.all(races.map((race) => api.results(event.id, {
        raceId: race.id, page: 0, size: 1,
      })))
      setCounts(Object.fromEntries(races.map((race, index) => [race.id, pages[index].totalElements])))
    } catch (reason) {
      setMessage({ tone: 'danger', text: adminErrorMessage(reason) })
    } finally {
      setLoadingCounts(false)
    }
  }, [api, event.id, races])

  useEffect(() => { void loadCounts() }, [loadCounts])

  const changePublication = async () => {
    if (!confirmRace) return
    setBusy(true); setMessage(null)
    try {
      if (confirmRace.resultsPublicationStatus === 'PUBLISHED') {
        await api.draftRace(event.id, confirmRace.id, 'Изменено через вкладку публикации результатов')
      } else {
        await api.publishRace(event.id, confirmRace.id)
      }
      setMessage({
        tone: 'success',
        text: confirmRace.resultsPublicationStatus === 'PUBLISHED'
          ? 'Результаты возвращены в черновик.'
          : 'Результаты опубликованы.',
      })
      setConfirmRace(null)
      await onChanged()
    } catch (reason) {
      setMessage({ tone: 'danger', text: adminErrorMessage(reason) })
    } finally {
      setBusy(false)
    }
  }

  return <div className="admin-stack">
    <div className="admin-section-toolbar"><div><h2>Публикация результатов</h2><p>Управляйте доступностью протокола каждого старта. Публикация самого мероприятия не изменяется.</p></div></div>
    {message && <AdminNotice tone={message.tone}>{message.text}</AdminNotice>}
    {races.length ? <div className="admin-publication-list">{races.map((race) => <article key={race.id}>
      <div><strong>{race.name}</strong><p>{loadingCounts ? 'Считаем результаты…' : `${(counts[race.id] ?? 0).toLocaleString('ru-RU')} результатов`}</p></div>
      <StatusBadge value={race.resultsPublicationStatus} />
      <div className="admin-row-actions"><button className={race.resultsPublicationStatus === 'PUBLISHED' ? 'admin-button-secondary admin-button-compact' : 'admin-button-primary admin-button-compact'} type="button" onClick={() => setConfirmRace(race)}>{race.resultsPublicationStatus === 'PUBLISHED' ? 'Вернуть результаты в черновик' : 'Опубликовать результаты'}</button></div>
    </article>)}</div> : <div className="admin-empty compact">У мероприятия пока нет стартов.</div>}
    {confirmRace && <ConfirmDialog
      title={confirmRace.resultsPublicationStatus === 'PUBLISHED' ? 'Вернуть результаты в черновик?' : 'Опубликовать результаты?'}
      description={confirmRace.resultsPublicationStatus === 'PUBLISHED' ? 'Публичный протокол этого старта станет недоступен. Мероприятие и данные результатов не изменятся.' : 'Публичным станет протокол только этого старта. Статус мероприятия не изменится.'}
      confirmLabel={confirmRace.resultsPublicationStatus === 'PUBLISHED' ? 'Вернуть в черновик' : 'Опубликовать результаты'}
      danger={confirmRace.resultsPublicationStatus === 'PUBLISHED'}
      busy={busy}
      onConfirm={() => void changePublication()}
      onClose={() => setConfirmRace(null)}
    />}
  </div>
}
