import { type FormEvent, useState } from 'react'
import type { ResultInquiryLookup } from '../api/types'
import {
  availabilityView,
  inquiryRaceLabel,
  parseRussianBirthDate,
} from '../utils/resultInquiry'
import { formatDateInput } from '../utils/inputFormatting'
import { DigitAutoformatInput } from './DigitAutoformatInput'
import { ResultIssueDialog } from './ResultIssueDialog'
import { ResultIssueSupportFallback } from './ResultIssueSupportFallback'

interface Props {
  eventId: number
  eventName: string
  eventTimeZone: string
  inquiry: ResultInquiryLookup
  verifiedBirthDate: string | null
  verifying: boolean
  verificationError: string | null
  onVerify: (birthDate: string) => Promise<void>
  onClearVerification: () => void
}

export function ResultInquiryPanel({
  eventId,
  eventName,
  eventTimeZone,
  inquiry,
  verifiedBirthDate,
  verifying,
  verificationError,
  onVerify,
  onClearVerification,
}: Props) {
  const [birthDate, setBirthDate] = useState('')
  const [birthDateError, setBirthDateError] = useState<string | null>(null)
  const [retrying, setRetrying] = useState(inquiry.lookupState !== 'VERIFICATION_FAILED')
  const [verificationStarted, setVerificationStarted] = useState(false)
  const [dialogOpen, setDialogOpen] = useState(
    inquiry.lookupState === 'RESULT_NOT_PUBLIC' && verifiedBirthDate !== null,
  )

  if (inquiry.lookupState === 'RESULT_PUBLIC') return null
  if (inquiry.lookupState === 'NOT_FOUND') {
    return <div className="state-message">По этому стартовому номеру ничего не найдено.</div>
  }

  const availability = availabilityView(
    inquiry.inquiryAvailability,
    inquiry.deadline,
    eventTimeZone,
  )

  if (!availability.canContact && (inquiry.lookupState === 'NEEDS_VERIFICATION'
    || inquiry.lookupState === 'VERIFICATION_FAILED')) {
    return <div className="state-message" role="status">
      {availability.explanation ?? 'Приём обращений сейчас недоступен.'}
    </div>
  }

  const submitVerification = async (event: FormEvent) => {
    event.preventDefault()
    const apiBirthDate = parseRussianBirthDate(birthDate)
    if (!apiBirthDate) {
      setBirthDateError('Введите корректную дату в формате ДД.ММ.ГГГГ.')
      return
    }
    setBirthDate('')
    setBirthDateError(null)
    await onVerify(apiBirthDate)
    setRetrying(false)
  }

  const retryBirthDateVerification = () => {
    setBirthDate('')
    setBirthDateError(null)
    setRetrying(true)
  }

  if (inquiry.lookupState === 'NEEDS_VERIFICATION' && !verificationStarted) {
    return <section className="inquiry-followup-card" aria-label="Результат не установлен">
      <div><strong>Результат не установлен</strong><p>Мы нашли регистрацию с этим стартовым номером, но результат сейчас не отображается в публичном протоколе.</p></div>
      <button type="button" onClick={() => setVerificationStarted(true)}>Уточнить результат</button>
    </section>
  }

  if (inquiry.lookupState === 'NEEDS_VERIFICATION'
    || (inquiry.lookupState === 'VERIFICATION_FAILED' && retrying)) {
    return <section className="verification-card" aria-labelledby="verification-title">
      <p className="eyebrow">Дополнительная проверка</p>
      <h3 id="verification-title">Уточните данные участника.</h3>
      <form onSubmit={submitVerification}>
        <label>
          <span>Дата рождения</span>
          <DigitAutoformatInput
            aria-label="Дата рождения"
            autoComplete="off"
            formatter={formatDateInput}
            maxLength={10}
            placeholder="ДД.ММ.ГГГГ"
            value={birthDate}
            onValueChange={(value) => { setBirthDate(value); setBirthDateError(null) }}
          />
        </label>
        <button type="submit" disabled={verifying}>{verifying ? 'Проверяем…' : 'Продолжить'}</button>
      </form>
      {birthDateError && <p className="form-error" role="alert">{birthDateError}</p>}
      {verificationError && <p className="form-error" role="alert">{verificationError}</p>}
    </section>
  }

  if (inquiry.lookupState === 'VERIFICATION_FAILED') {
    return <section className="verification-card verification-failed" role="alert">
      <div>
        <h3>Не удалось подтвердить данные участника.</h3>
        <p>Дата рождения не совпала с данными регистрации. Проверьте введённую дату и попробуйте ещё раз.</p>
        <ResultIssueSupportFallback
          availability={inquiry.inquiryAvailability}
          issueKind="MISSING_RESULT"
          eventName={eventName}
          startLabel={inquiryRaceLabel(inquiry)}
          bib={inquiry.bib}
          participantName={inquiry.participantDisplayName}
          onRetryVerification={retryBirthDateVerification}
        />
      </div>
    </section>
  }

  const canContact = availability.canContact
  const recoveryExplanation = 'Мы нашли регистрацию с этим стартовым номером, но результат сейчас не отображается в публичном протоколе.'

  return <>
    <section className="inquiry-result-row" aria-label="Результат не установлен">
      <div><span>Стартовый номер</span><strong className="bib">{inquiry.bib}</strong></div>
      <div><span>Участник</span><strong>{inquiry.participantDisplayName}</strong></div>
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
      initialVerifiedBirthDate={verifiedBirthDate}
      onClose={() => { setDialogOpen(false); onClearVerification() }}
    />}
  </>
}
