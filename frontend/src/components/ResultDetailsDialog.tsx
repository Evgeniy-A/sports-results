import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { ResultDetails, ResultInquiryLookup } from '../api/types'
import { formatDuration, formatGender, statusLabel } from '../utils/format'
import { publicCategoryName } from '../utils/publicEventView'
import { resultIssueAvailabilityMessage } from '../utils/resultIssue'
import { RankingAchievements } from './RankingAchievements'
import { ResultIssueDialog } from './ResultIssueDialog'

interface Props {
  resultId: number
  eventName: string
  eventTimeZone: string
  categoryEnabled: boolean
  onClose: () => void
}

export function ResultDetailsDialog({ resultId, eventName, eventTimeZone, categoryEnabled, onClose }: Props) {
  const dialogRef = useRef<HTMLElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  const [result, setResult] = useState<ResultDetails | null>(null)
  const [inquiry, setInquiry] = useState<ResultInquiryLookup | null>(null)
  const [error, setError] = useState(false)
  const [issueOpen, setIssueOpen] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    api.result(resultId, controller.signal).then(async (loadedResult) => {
      if (controller.signal.aborted) return
      setResult(loadedResult)
      if (!loadedResult.bib) return
      try {
        const lookup = await api.resultInquiry(loadedResult.eventId, loadedResult.bib, controller.signal)
        if (!controller.signal.aborted) setInquiry(lookup)
      } catch (reason: unknown) {
        if (!controller.signal.aborted) {
          console.error('Result issue availability request failed', reason)
        }
      }
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) {
        console.error('Result details request failed', reason)
        setError(true)
      }
    })
    return () => controller.abort()
  }, [resultId])

  useEffect(() => {
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
    closeRef.current?.focus()
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab' || !dialogRef.current) return
      const focusable = [...dialogRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), a[href]')]
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable.at(-1) ?? first
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      previouslyFocused?.focus()
    }
  }, [onClose])

  if (issueOpen && result && inquiry) {
    return <ResultIssueDialog
      kind="RESULT_CORRECTION"
      eventId={result.eventId}
      eventName={eventName}
      eventTimeZone={eventTimeZone}
      inquiry={inquiry}
      result={result}
      onClose={() => setIssueOpen(false)}
    />
  }

  const availabilityMessage = inquiry
    ? resultIssueAvailabilityMessage(inquiry.inquiryAvailability)
    : null
  const categoryName = result ? publicCategoryName(categoryEnabled, result.category) : null

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section ref={dialogRef} className="result-dialog" role="dialog" aria-modal="true" aria-labelledby="result-title" onMouseDown={(event) => event.stopPropagation()}>
        <button ref={closeRef} className="dialog-close" type="button" aria-label="Закрыть" onClick={onClose}>×</button>
        {!result && !error && <div className="state-message compact">Загружаем карточку…</div>}
        {error && <div className="state-message compact error">Не удалось загрузить карточку результата.</div>}
        {result && <>
          <p className="eyebrow">Карточка участника</p>
          <h2 id="result-title">{result.displayName}</h2>
          <div className="detail-badges"><span className="bib">№ {result.bib ?? '—'}</span><span className={`status status-${result.status}`}>{statusLabel(result.status)}</span></div>
          {result.rankingBasis !== 'NONE' && <div className="detail-ranking"><h3>Официальный зачёт</h3><RankingAchievements achievements={result.rankingAchievements} /></div>}
          <dl className="detail-grid">
            <div><dt>Дистанция</dt><dd>{result.raceName}</dd></div>
            <div><dt>Пол</dt><dd>{formatGender(result.gender)}</dd></div>
            {categoryName && <div><dt>Категория</dt><dd>{categoryName}</dd></div>}
            <div><dt>Официальное время {result.rankingBasis === 'GUN_TIME' && <span className="standing-badge">Зачёт</span>}</dt><dd className={`time ${result.rankingBasis === 'GUN_TIME' ? 'time-primary' : ''}`}>{formatDuration(result.gunTimeMs)}</dd></div>
            <div><dt>Чистое время {result.rankingBasis === 'CHIP_TIME' && <span className="standing-badge">Зачёт</span>}</dt><dd className={`time ${result.rankingBasis === 'CHIP_TIME' ? 'time-primary' : ''}`}>{formatDuration(result.chipTimeMs)}</dd></div>
          </dl>
          {result.splits.length > 0 && <div className="split-list"><h3>Контрольные точки</h3>{result.splits.map((split) => <div key={split.checkpointId}><span>{split.checkpointName}</span><strong>{formatDuration(split.elapsedTimeMs)}</strong></div>)}</div>}
          {inquiry?.inquiryAvailability === 'OPEN' && <div className="result-secondary-action">
            <button type="button" onClick={() => setIssueOpen(true)}>Сообщить об ошибке</button>
          </div>}
          {availabilityMessage && <p className="result-issue-availability" role="status">{availabilityMessage}</p>}
        </>}
      </section>
    </div>
  )
}
