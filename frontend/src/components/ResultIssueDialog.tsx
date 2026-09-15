import { type FormEvent, useEffect, useRef, useState } from 'react'
import { ApiError, api } from '../api/client'
import type {
  ResultIssueAttachmentCapabilities,
  ResultIssueAttachmentUpload,
  ResultCorrectionReason,
  ResultDetails,
  ResultInquiryLookup,
  ResultIssueCreated,
} from '../api/types'
import { DirectUploadError, uploadFileDirectly } from '../api/directUpload'
import { formatDuration, statusLabel } from '../utils/format'
import { formatDateInput, formatDurationInput } from '../utils/inputFormatting'
import { parseRussianBirthDate } from '../utils/resultInquiry'
import {
  decideResultIssueVerification,
  localDateTimeToInstant,
  parseClaimedTime,
  PUBLIC_CORRECTION_REASONS,
  resultIssueAvailabilityMessage,
} from '../utils/resultIssue'
import {
  type PreparedAttachment,
  ResultIssueAttachmentField,
} from './ResultIssueAttachmentField'
import { DigitAutoformatInput } from './DigitAutoformatInput'

interface CommonProps {
  eventId: number
  eventName: string
  eventTimeZone: string
  inquiry: ResultInquiryLookup
  initialVerifiedBirthDate?: string | null
  supportUrl?: string | null
  onClose: () => void
}

interface MissingProps extends CommonProps {
  kind: 'MISSING_RESULT'
}

interface CorrectionProps extends CommonProps {
  kind: 'RESULT_CORRECTION'
  result: ResultDetails
}

type Props = MissingProps | CorrectionProps
type DialogState = 'VERIFY' | 'FORM' | 'UPLOADS' | 'SUCCESS' | 'DUPLICATE'

interface LocalAttachment extends PreparedAttachment {
  file: File
  authorization?: ResultIssueAttachmentUpload
}

const VERIFICATION_FAILED_MESSAGE = `Не удалось подтвердить данные участника.

Введённая дата рождения не совпадает с данными регистрации.

Если вы считаете, что дата рождения или другие регистрационные данные указаны неверно, обратитесь в службу поддержки организатора.`

function technicalErrorMessage(reason: unknown): string {
  if (reason instanceof ApiError) {
    if (reason.code === 'RESULT_ISSUES_CLOSED') {
      return 'Срок подачи обращений по результатам завершён.'
    }
    if (reason.code === 'RESULT_ISSUES_NOT_OPEN') {
      return 'Подача обращений по результатам ещё не началась.'
    }
    if (reason.code === 'RESULT_ISSUES_DISABLED') {
      return 'Обращения по результатам для этого мероприятия недоступны.'
    }
    if (reason.code === 'VALIDATION_FAILED' || reason.code === 'MALFORMED_REQUEST') {
      return 'Проверьте заполненные поля и попробуйте ещё раз.'
    }
    if (reason.status >= 500) return 'Сервис временно недоступен. Попробуйте ещё раз позже.'
  }
  return 'Не удалось отправить обращение. Проверьте подключение и попробуйте ещё раз.'
}

function attachmentErrorMessage(reason: unknown): string {
  if (reason instanceof ApiError) {
    if (reason.code === 'ATTACHMENT_TOO_LARGE') return 'Файл превышает допустимый размер.'
    if (reason.code === 'ATTACHMENT_TYPE_NOT_ALLOWED') return 'Этот тип файла нельзя прикрепить.'
    if (reason.code === 'ATTACHMENT_LIMIT_REACHED') return 'Достигнут лимит вложений.'
    if (reason.code === 'ATTACHMENT_OBJECT_MISMATCH') return 'Размер загруженного файла не совпал. Повторите загрузку.'
    if (reason.code === 'ATTACHMENT_OBJECT_NOT_FOUND') return 'Хранилище не подтвердило файл. Повторите загрузку.'
  }
  if (reason instanceof DirectUploadError) {
    return reason.status === 403
      ? 'Ссылка на загрузку недействительна или истекла. Нажмите «Повторить».'
      : 'Не удалось загрузить файл в хранилище. Проверьте подключение и повторите.'
  }
  return 'Не удалось загрузить файл. Повторите попытку.'
}

export function ResultIssueDialog(props: Props) {
  const {
    eventId,
    eventName,
    eventTimeZone,
    inquiry,
    initialVerifiedBirthDate = null,
    supportUrl = null,
    onClose,
  } = props
  const dialogRef = useRef<HTMLElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  const submitLockRef = useRef(false)
  const initialActiveIssueId = initialVerifiedBirthDate ? inquiry.activeIssue?.issueId ?? null : null
  const [dialogState, setDialogState] = useState<DialogState>(
    initialActiveIssueId !== null ? 'DUPLICATE' : initialVerifiedBirthDate ? 'FORM' : 'VERIFY',
  )
  const [duplicateIssueId, setDuplicateIssueId] = useState<number | null>(initialActiveIssueId)
  const [verifiedBirthDate, setVerifiedBirthDate] = useState<string | null>(initialVerifiedBirthDate)
  const [birthDate, setBirthDate] = useState('')
  const [birthDateError, setBirthDateError] = useState<string | null>(null)
  const [verificationFailed, setVerificationFailed] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [contactEmail, setContactEmail] = useState('')
  const [message, setMessage] = useState('')
  const [estimatedStartAt, setEstimatedStartAt] = useState('')
  const [estimatedFinishAt, setEstimatedFinishAt] = useState('')
  const [correctionReason, setCorrectionReason] = useState<Exclude<ResultCorrectionReason, 'PARTICIPANT_DATA'>>('OFFICIAL_TIME')
  const [claimedTime, setClaimedTime] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [createdIssue, setCreatedIssue] = useState<ResultIssueCreated | null>(null)
  const [attachmentCapabilities, setAttachmentCapabilities] = useState<ResultIssueAttachmentCapabilities | null>(null)
  const [attachments, setAttachments] = useState<LocalAttachment[]>([])

  useEffect(() => {
    if (dialogState !== 'FORM') return
    const controller = new AbortController()
    api.resultIssueAttachmentCapabilities(controller.signal)
      .then(setAttachmentCapabilities)
      .catch((reason: unknown) => {
        if (!(reason instanceof DOMException && reason.name === 'AbortError')) {
          console.error('Attachment capabilities could not be loaded', reason)
        }
      })
    return () => controller.abort()
  }, [dialogState])

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
      const focusable = [...dialogRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled])',
      )]
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
      submitLockRef.current = false
      previouslyFocused?.focus()
    }
  }, [onClose])

  const closeDialog = () => {
    setBirthDate('')
    setVerifiedBirthDate(null)
    onClose()
  }

  const updateAttachment = (localId: string, changes: Partial<LocalAttachment>) => {
    setAttachments((current) => current.map((attachment) => attachment.localId === localId
      ? { ...attachment, ...changes }
      : attachment))
  }

  const selectAttachments = (files: FileList) => {
    if (!attachmentCapabilities?.directUploadAvailable) return
    const availableSlots = Math.max(0, attachmentCapabilities.maxAttachmentsPerIssue - attachments.length)
    const selectedFiles = Array.from(files)
    if (selectedFiles.length > availableSlots) {
      setFormError(`Можно прикрепить не более ${attachmentCapabilities.maxAttachmentsPerIssue} файлов.`)
    } else {
      setFormError(null)
    }
    const next = selectedFiles.slice(0, availableSlots).map<LocalAttachment>((file) => {
      const tooLarge = file.size > attachmentCapabilities.maxFileSizeBytes
      return {
        localId: crypto.randomUUID(),
        file,
        fileName: file.name,
        sizeBytes: file.size,
        progress: null,
        state: tooLarge ? 'ERROR' : 'SELECTED',
        errorMessage: tooLarge ? 'Файл превышает допустимый размер.' : null,
        retryable: false,
      }
    })
    setAttachments((current) => [...current, ...next])
  }

  const uploadAttachment = async (
    issue: ResultIssueCreated,
    attachment: LocalAttachment,
    refreshAuthorization: boolean,
  ): Promise<boolean> => {
    updateAttachment(attachment.localId, {
      state: 'UPLOADING', progress: 0, errorMessage: null, retryable: false,
    })
    let authorization = attachment.authorization
    try {
      if (authorization && refreshAuthorization) {
        authorization = await api.refreshResultIssueAttachmentUpload(
          issue.issueId, authorization.attachmentId, issue.attachmentUploadToken,
        )
      } else if (!authorization) {
        authorization = await api.createResultIssueAttachment(
          issue.issueId,
          issue.attachmentUploadToken,
          {
            originalFileName: attachment.fileName,
            contentType: attachment.file.type || null,
            sizeBytes: attachment.sizeBytes,
          },
        )
      }
      updateAttachment(attachment.localId, { authorization })
      await uploadFileDirectly({
        url: authorization.uploadUrl,
        method: authorization.uploadMethod,
        requiredHeaders: authorization.requiredHeaders,
        file: attachment.file,
        onProgress: (progress) => updateAttachment(attachment.localId, { progress }),
      })
      await api.confirmResultIssueAttachment(
        issue.issueId, authorization.attachmentId, issue.attachmentUploadToken,
      )
      updateAttachment(attachment.localId, {
        authorization, state: 'UPLOADED', progress: 100, errorMessage: null, retryable: false,
      })
      return true
    } catch (reason: unknown) {
      console.error('Result issue attachment upload failed', reason)
      updateAttachment(attachment.localId, {
        authorization, state: 'ERROR', progress: null,
        errorMessage: attachmentErrorMessage(reason), retryable: true,
      })
      return false
    }
  }

  const retryAttachment = async (localId: string) => {
    if (!createdIssue) return
    const attachment = attachments.find((candidate) => candidate.localId === localId)
    if (!attachment || attachment.state === 'UPLOADING') return
    await uploadAttachment(createdIssue, attachment, Boolean(attachment.authorization))
  }

  const submitVerification = async (event: FormEvent) => {
    event.preventDefault()
    const apiBirthDate = parseRussianBirthDate(birthDate)
    if (!apiBirthDate) {
      setBirthDateError('Введите корректную дату в формате ДД.ММ.ГГГГ.')
      return
    }
    setVerifying(true)
    setBirthDateError(null)
    setVerificationFailed(false)
    try {
      const verified = await api.verifyResultInquiry(eventId, { bib: inquiry.bib, birthDate: apiBirthDate })
      const expectedResultId = props.kind === 'RESULT_CORRECTION' ? props.result.resultId : null
      const verifiedForFlow = props.kind === 'MISSING_RESULT'
        ? verified.lookupState === 'RESULT_NOT_PUBLIC'
        : verified.lookupState === 'RESULT_PUBLIC' && verified.publicResultId === expectedResultId
      const decision = decideResultIssueVerification(verifiedForFlow, verified.activeIssue)
      if (decision.state === 'VERIFICATION_FAILED') {
        setVerificationFailed(true)
        return
      }
      setVerifiedBirthDate(apiBirthDate)
      if (decision.state === 'DUPLICATE') {
        setDuplicateIssueId(decision.issueId)
        setVerifiedBirthDate(null)
        setDialogState('DUPLICATE')
        return
      }
      setDialogState('FORM')
    } catch (reason: unknown) {
      console.error('Result issue identity verification failed', reason)
      setFormError('Не удалось выполнить проверку. Проверьте подключение и попробуйте ещё раз.')
    } finally {
      setBirthDate('')
      setVerifying(false)
    }
  }

  const submitIssue = async (event: FormEvent) => {
    event.preventDefault()
    if (submitLockRef.current || submitting || !verifiedBirthDate) return
    if (attachments.some((attachment) => attachment.state === 'ERROR' && !attachment.retryable)) {
      setFormError('Уберите файлы, которые превышают допустимый размер.')
      return
    }

    let claimedTimeMs: number | null = null
    if (props.kind === 'RESULT_CORRECTION'
      && (correctionReason === 'OFFICIAL_TIME' || correctionReason === 'CHIP_TIME')) {
      claimedTimeMs = parseClaimedTime(claimedTime)
      if (claimedTimeMs === null) {
        setFormError('Введите время в формате ЧЧ:ММ:СС или ЧЧ:ММ:СС.ммм.')
        return
      }
    }
    const startAt = localDateTimeToInstant(estimatedStartAt)
    const finishAt = localDateTimeToInstant(estimatedFinishAt)
    if (props.kind === 'MISSING_RESULT' && startAt && finishAt && finishAt < startAt) {
      setFormError('Примерное время финиша не может быть раньше времени старта.')
      return
    }

    submitLockRef.current = true
    setSubmitting(true)
    setFormError(null)
    try {
      const created = props.kind === 'MISSING_RESULT'
        ? await api.createMissingResultIssue(eventId, {
          bib: inquiry.bib,
          birthDate: verifiedBirthDate,
          contactEmail,
          message,
          estimatedStartAt: startAt,
          estimatedFinishAt: finishAt,
        })
        : await api.createResultCorrectionIssue(eventId, props.result.resultId, {
          birthDate: verifiedBirthDate,
          correctionReason,
          claimedGunTimeMs: correctionReason === 'OFFICIAL_TIME' ? claimedTimeMs : null,
          claimedChipTimeMs: correctionReason === 'CHIP_TIME' ? claimedTimeMs : null,
          contactEmail,
          message,
        })
      setCreatedIssue(created)
      setVerifiedBirthDate(null)
      const selectedAttachments = attachments.filter((attachment) => attachment.state === 'SELECTED')
      if (selectedAttachments.length === 0) {
        setDialogState('SUCCESS')
      } else {
        setDialogState('UPLOADS')
        await Promise.all(selectedAttachments.map((attachment) => uploadAttachment(created, attachment, false)))
      }
    } catch (reason: unknown) {
      if (reason instanceof ApiError && reason.code === 'ACTIVE_RESULT_ISSUE_EXISTS') {
        setDuplicateIssueId(null)
        setVerifiedBirthDate(null)
        setDialogState('DUPLICATE')
      } else if (reason instanceof ApiError && reason.code === 'IDENTITY_VERIFICATION_FAILED') {
        setVerifiedBirthDate(null)
        setVerificationFailed(true)
        setDialogState('VERIFY')
      } else {
        console.error('Result issue request failed', reason)
        setFormError(technicalErrorMessage(reason))
      }
    } finally {
      submitLockRef.current = false
      setSubmitting(false)
    }
  }

  const availabilityMessage = resultIssueAvailabilityMessage(inquiry.inquiryAvailability)
  const contextRace = inquiry.startDisplayName ?? inquiry.raceDisplayName

  return <div className="modal-backdrop" role="presentation" onMouseDown={closeDialog}>
    <section
      ref={dialogRef}
      className="result-dialog issue-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="result-issue-title"
      onMouseDown={(event) => event.stopPropagation()}
    >
      <button ref={closeRef} className="dialog-close" type="button" aria-label="Закрыть" onClick={closeDialog}>×</button>
      <p className="eyebrow">Обращение по результату</p>
      <h2 id="result-issue-title">{props.kind === 'MISSING_RESULT' ? 'Уточнить результат' : 'Сообщить об ошибке'}</h2>

      {availabilityMessage && <div className="issue-notice" role="status">{availabilityMessage}</div>}

      {!availabilityMessage && dialogState === 'VERIFY' && <>
        <p className="issue-lead">Подтвердите дату рождения участника. Она используется только для проверки и не добавляется в обращение.</p>
        <form className="issue-form compact-form" onSubmit={submitVerification}>
          <label>
            <span>Дата рождения</span>
            <DigitAutoformatInput
              aria-label="Дата рождения"
              aria-describedby={birthDateError ? 'issue-birth-date-error' : undefined}
              autoComplete="off"
              formatter={formatDateInput}
              maxLength={10}
              placeholder="ДД.ММ.ГГГГ"
              value={birthDate}
              onValueChange={(value) => {
                setBirthDate(value)
                setBirthDateError(null)
                setVerificationFailed(false)
                setFormError(null)
              }}
            />
          </label>
          <button className="issue-submit" type="submit" disabled={verifying}>{verifying ? 'Проверяем…' : 'Продолжить'}</button>
        </form>
        {birthDateError && <p id="issue-birth-date-error" className="form-error" role="alert">{birthDateError}</p>}
        {verificationFailed && <div className="verification-help" role="alert">
          <p>{VERIFICATION_FAILED_MESSAGE}</p>
          {supportUrl && <a href={supportUrl}>Обратиться в службу поддержки</a>}
        </div>}
        {formError && <p className="form-error" role="alert">{formError}</p>}
      </>}

      {!availabilityMessage && dialogState === 'FORM' && <form className="issue-form" onSubmit={submitIssue}>
        <dl className="issue-context">
          <div><dt>Мероприятие</dt><dd>{eventName}</dd></div>
          <div><dt>Старт</dt><dd>{contextRace || '—'}</dd></div>
          <div><dt>Стартовый номер</dt><dd>№ {inquiry.bib}</dd></div>
          <div><dt>Участник</dt><dd>{inquiry.participantDisplayName ?? (props.kind === 'RESULT_CORRECTION' ? props.result.displayName : '—')}</dd></div>
        </dl>

        {props.kind === 'RESULT_CORRECTION' && <>
          <dl className="issue-current-result">
            <div><dt>Официальное время</dt><dd>{formatDuration(props.result.gunTimeMs)}</dd></div>
            <div><dt>Чистое время</dt><dd>{formatDuration(props.result.chipTimeMs)}</dd></div>
            <div><dt>Текущий статус</dt><dd>{statusLabel(props.result.status)}</dd></div>
          </dl>
          <label>
            <span>Что именно неверно?</span>
            <select value={correctionReason} onChange={(changeEvent) => {
              setCorrectionReason(changeEvent.target.value as Exclude<ResultCorrectionReason, 'PARTICIPANT_DATA'>)
              setClaimedTime('')
              setFormError(null)
            }}>
              {PUBLIC_CORRECTION_REASONS.map((reason) => <option key={reason.value} value={reason.value}>{reason.label}</option>)}
            </select>
          </label>
          {(correctionReason === 'OFFICIAL_TIME' || correctionReason === 'CHIP_TIME') && <label>
            <span>{correctionReason === 'OFFICIAL_TIME'
              ? 'Какое официальное время вы считаете правильным?'
              : 'Какое чистое время вы считаете правильным?'}</span>
            <DigitAutoformatInput
              aria-label="Заявленное время"
              formatter={formatDurationInput}
              maxLength={13}
              placeholder="ЧЧ:ММ:СС.ммм"
              value={claimedTime}
              onValueChange={(value) => { setClaimedTime(value); setFormError(null) }}
              required
            />
          </label>}
        </>}

        {props.kind === 'MISSING_RESULT' && <div className="issue-form-grid">
          <label><span>Примерное время старта <small>необязательно</small></span><input type="datetime-local" value={estimatedStartAt} onChange={(changeEvent) => setEstimatedStartAt(changeEvent.target.value)} /></label>
          <label><span>Примерное время финиша <small>необязательно</small></span><input type="datetime-local" value={estimatedFinishAt} onChange={(changeEvent) => setEstimatedFinishAt(changeEvent.target.value)} /></label>
          <p>Время будет передано с учётом часового пояса вашего устройства. Часовой пояс мероприятия: {eventTimeZone}.</p>
        </div>}

        <label>
          <span>Контактная почта</span>
          <input type="email" autoComplete="email" maxLength={320} value={contactEmail} onChange={(changeEvent) => setContactEmail(changeEvent.target.value)} required />
          <small>Укажите почту, по которой с вами можно связаться, если хронометражисту потребуется уточнение.</small>
        </label>
        <label>
          <span>{props.kind === 'RESULT_CORRECTION' ? 'Комментарий' : 'Что известно о результате?'}</span>
          <textarea maxLength={4000} rows={5} value={message} onChange={(changeEvent) => setMessage(changeEvent.target.value)} required />
        </label>

        <ResultIssueAttachmentField
          enabled={attachmentCapabilities?.directUploadAvailable === true}
          attachments={attachments}
          onSelect={selectAttachments}
          onRemove={(localId) => setAttachments((current) => current.filter((item) => item.localId !== localId))}
          maxFileSizeBytes={attachmentCapabilities?.maxFileSizeBytes}
          maxAttachments={attachmentCapabilities?.maxAttachmentsPerIssue}
        />

        {formError && <p className="form-error" role="alert">{formError}</p>}
        <button className="issue-submit" type="submit" disabled={submitting}>{submitting ? 'Отправляем…' : 'Отправить обращение'}</button>
      </form>}

      {dialogState === 'UPLOADS' && createdIssue && <div className="issue-outcome issue-upload-outcome" role="status">
        <span aria-hidden="true">✓</span>
        <h3>Обращение №{createdIssue.issueId} принято</h3>
        <p>Обращение уже сохранено. Дождитесь загрузки файлов; при ошибке её можно повторить без нового обращения.</p>
        <ResultIssueAttachmentField
          enabled
          disabled
          attachments={attachments}
          onSelect={() => undefined}
          onRetry={retryAttachment}
        />
        <button
          type="button"
          disabled={attachments.some((attachment) => attachment.state === 'UPLOADING')}
          onClick={closeDialog}
        >Готово</button>
      </div>}

      {dialogState === 'SUCCESS' && createdIssue && <div className="issue-outcome" role="status">
        <span aria-hidden="true">✓</span>
        <h3>Обращение №{createdIssue.issueId} принято</h3>
        <p>Хронометражист проверит информацию. Повторно отправлять обращение по этому результату не нужно.</p>
        <button type="button" onClick={closeDialog}>Готово</button>
      </div>}

      {dialogState === 'DUPLICATE' && <div className="issue-outcome issue-duplicate" role="status">
        <h3>Обращение уже отправлено</h3>
        <p>{duplicateIssueId !== null
          ? <>По этому результату уже есть активное обращение №{duplicateIssueId}. Повторное обращение можно отправить после завершения его рассмотрения.</>
          : <>По этому результату уже есть активное обращение. Повторное обращение можно отправить после завершения его рассмотрения.</>}</p>
        <button type="button" onClick={closeDialog}>Закрыть</button>
      </div>}
    </section>
  </div>
}
