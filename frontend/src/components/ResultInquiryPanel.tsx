import { useState } from 'react'
import type { ResultInquiryLookup } from '../api/types'
import { availabilityView, inquiryRaceLabel } from '../utils/resultInquiry'
import { ResultIssueDialog } from './ResultIssueDialog'

interface Props {
  eventId: number
  eventName: string
  eventTimeZone: string
  inquiry: ResultInquiryLookup
  selectingRace: boolean
  onSelectRace: (raceId: number) => Promise<void>
}

export function ResultInquiryPanel({
  eventId,
  eventName,
  eventTimeZone,
  inquiry,
  selectingRace,
  onSelectRace,
}: Props) {
  const [dialogOpen, setDialogOpen] = useState(false)

  if (inquiry.lookupState === 'RESULT_PUBLIC') return null
  if (inquiry.lookupState === 'NOT_FOUND') {
    return <div className="state-message">По этому стартовому номеру ничего не найдено.</div>
  }

  const availability = availabilityView(
    inquiry.inquiryAvailability,
    inquiry.deadline,
    eventTimeZone,
  )

  if (inquiry.lookupState === 'AMBIGUOUS') {
    const raceSelectionAvailable = inquiry.candidates.length > 1
    return <section className="verification-card" aria-labelledby="inquiry-race-title">
      <p className="eyebrow">Найдено несколько участников</p>
      <h3 id="inquiry-race-title">{raceSelectionAvailable
        ? 'Выберите дистанцию / формат'
        : 'Нужен дополнительный спортивный контекст'}</h3>
      <p>{raceSelectionAvailable
        ? `Стартовый номер ${inquiry.bib} найден в нескольких стартах.`
        : `Стартовый номер ${inquiry.bib} найден у нескольких участников одного старта. Выберите участника в опубликованном протоколе или обратитесь к организатору.`}</p>
      {raceSelectionAvailable && <div className="selection-buttons" role="group" aria-label="Дистанция или формат">
          {inquiry.candidates.map((candidate) => <button
            key={candidate.raceId}
            type="button"
            disabled={selectingRace}
            onClick={() => void onSelectRace(candidate.raceId)}
          >{candidate.startDisplayName || candidate.raceDisplayName}</button>)}
        </div>}
    </section>
  }

  const canContact = availability.canContact
  const recoveryExplanation = 'Мы нашли регистрацию с этим стартовым номером, но результат сейчас не отображается в публичном протоколе.'

  return <>
    <section className="inquiry-result-row" aria-label="Результат не установлен">
      <div><span>Стартовый номер</span><strong className="bib">{inquiry.bib}</strong></div>
      {inquiry.participantDisplayName && <div><span>Участник</span><strong>{inquiry.participantDisplayName}</strong></div>}
      <div><span>Старт</span><strong>{inquiryRaceLabel(inquiry)}</strong></div>
      <div><span>Статус</span><strong className="inquiry-status">{availability.statusText}</strong></div>
      {(availability.explanation || availability.deadlineText || canContact) && <div className="inquiry-result-actions">
        <span>{recoveryExplanation}{(availability.explanation ?? availability.deadlineText) ? ` ${availability.explanation ?? availability.deadlineText}` : ''}</span>
        {canContact && <button type="button" onClick={() => setDialogOpen(true)}>Уточнить результат</button>}
      </div>}
    </section>
    {dialogOpen && <ResultIssueDialog
      kind="MISSING_RESULT"
      eventId={eventId}
      eventName={eventName}
      eventTimeZone={eventTimeZone}
      inquiry={inquiry}
      onClose={() => setDialogOpen(false)}
    />}
  </>
}
