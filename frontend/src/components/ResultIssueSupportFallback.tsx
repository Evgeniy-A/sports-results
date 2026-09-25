import { type FormEvent, useState } from 'react'
import type { ResultInquiryAvailability } from '../api/types'
import {
  copyInquiryText,
  createDobVerificationSupportMessage,
  hasMeaningfulSupportDescription,
  HERO_LEAGUE_SUPPORT_URL,
  type DobFallbackIssueKind,
  type InquirySupportMessage,
} from '../utils/resultInquiry'

interface Props {
  availability: ResultInquiryAvailability
  issueKind: DobFallbackIssueKind
  eventName: string
  startLabel: string
  bib: string
  participantName?: string | null
  onRetryVerification: () => void
}

export function ResultIssueSupportFallback({
  availability,
  issueKind,
  eventName,
  startLabel,
  bib,
  participantName = null,
  onRetryVerification,
}: Props) {
  const [description, setDescription] = useState('')
  const [preparedMessage, setPreparedMessage] = useState<InquirySupportMessage | null>(null)
  const [copyState, setCopyState] = useState<'idle' | 'success' | 'error'>('idle')

  if (availability !== 'OPEN') return null

  const prepare = (event: FormEvent) => {
    event.preventDefault()
    if (!hasMeaningfulSupportDescription(description)) return
    setCopyState('idle')
    setPreparedMessage(createDobVerificationSupportMessage({
      issueKind,
      eventName,
      startLabel,
      bib,
      participantName,
      description,
    }))
  }

  const copy = async () => {
    if (!preparedMessage) return
    setCopyState('idle')
    try {
      await copyInquiryText(preparedMessage.text)
      setCopyState('success')
    } catch {
      setCopyState('error')
    }
  }

  const retryVerification = () => {
    setDescription('')
    setPreparedMessage(null)
    setCopyState('idle')
    onRetryVerification()
  }

  return <section className="verification-support-fallback" aria-labelledby="verification-support-title">
    {!preparedMessage ? <>
      <h3 id="verification-support-title">Не получается подтвердить данные?</h3>
      <form className="support-description-form" onSubmit={prepare}>
        <label>
          <span>Опишите ситуацию</span>
          <textarea
            aria-describedby="support-description-help"
            maxLength={4000}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
          <small id="support-description-help">Кратко напишите, что именно нужно проверить. Например: неверное время, ошибка в категории, ошибка в регистрационных данных или результат отсутствует в протоколе.</small>
        </label>
        <button
          className="issue-submit support-prepare-primary"
          type="submit"
          disabled={!hasMeaningfulSupportDescription(description)}
        >Подготовить обращение</button>
        <button className="support-retry-verification" type="button" onClick={retryVerification}>Попробовать подтвердить дату ещё раз</button>
      </form>
    </> : <>
      <h3 id="verification-support-title">Обращение подготовлено</h3>
      <ol className="support-instructions">
        <li>Скопируйте подготовленный текст обращения.</li>
        <li>Перейдите на страницу поддержки.</li>
        <li>Вставьте скопированный текст в поле «Ваш вопрос».</li>
        <li>Заполните остальные обязательные поля и отправьте обращение.</li>
      </ol>
      <div className="support-message-preview"><pre>{preparedMessage.text}</pre></div>
      <div className="support-actions">
        <button className="support-copy-primary" type="button" onClick={() => void copy()}>Скопировать текст обращения</button>
        {copyState === 'success'
          ? <a className="support-link-button" href={HERO_LEAGUE_SUPPORT_URL} target="_blank" rel="noopener noreferrer">Перейти в поддержку</a>
          : <button className="support-link-button" type="button" disabled>Перейти в поддержку</button>}
        <button className="support-change-button" type="button" onClick={() => {
          setPreparedMessage(null)
          setCopyState('idle')
        }}>Изменить описание</button>
        <button className="support-retry-verification" type="button" onClick={retryVerification}>Попробовать подтвердить дату ещё раз</button>
      </div>
      {copyState === 'success' && <p className="copy-feedback" role="status">Текст скопирован. Теперь перейдите в поддержку и вставьте его в поле «Ваш вопрос».</p>}
      {copyState === 'error' && <p className="form-error" role="alert">Не удалось скопировать текст. Попробуйте ещё раз.</p>}
    </>}
  </section>
}
