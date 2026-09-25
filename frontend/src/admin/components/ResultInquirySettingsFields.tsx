import { useId } from 'react'
import type { ResultInquiryConfiguration } from '../types'
import {
  MAX_RESULT_INQUIRY_WINDOW_DAYS,
  resultInquiryFieldVisibility,
} from '../resultInquirySettings'
import { Field } from './AdminUi'

export function ResultInquirySettingsFields({
  value,
  onChange,
  enabledLabel = 'Принимать обращения по результатам',
  deadlinePreview = null,
  deadlinePreviewLabel = 'Последний день подачи:',
  eventDateKnown = false,
}: {
  value: ResultInquiryConfiguration
  onChange: (value: ResultInquiryConfiguration) => void
  enabledLabel?: string
  deadlinePreview?: string | null
  deadlinePreviewLabel?: string
  eventDateKnown?: boolean
}) {
  const fieldId = useId()
  const visibility = resultInquiryFieldVisibility(value.deadlineMode)
  return <>
    <label className="admin-check"><input
      type="checkbox"
      checked={value.enabled}
      onChange={(event) => onChange({ ...value, enabled: event.target.checked })}
    /><span>{enabledLabel}</span></label>
    <fieldset className="admin-deadline-modes">
      <legend>Способ задания срока</legend>
      <div className={`admin-deadline-mode${visibility.windowDays ? ' is-selected' : ''}`}>
        <label className="admin-deadline-mode-option" htmlFor={`${fieldId}-after-event-days`}><input
          id={`${fieldId}-after-event-days`}
          type="radio"
          name={`${fieldId}-deadline-mode`}
          checked={visibility.windowDays}
          onChange={() => onChange({ ...value, deadlineMode: 'AFTER_EVENT_DAYS' })}
        /><span>N дней после окончания мероприятия</span></label>
        {visibility.windowDays && <div className="admin-deadline-mode-details">
          <Field label="Количество дней"><input
            type="number"
            min="1"
            max={MAX_RESULT_INQUIRY_WINDOW_DAYS}
            required={value.enabled}
            value={value.windowDays ?? ''}
            onChange={(event) => onChange({
              ...value,
              windowDays: event.target.value ? Number(event.target.value) : null,
            })}
          /></Field>
          <p className="admin-form-help">Обращения принимаются до конца N-го календарного дня после окончания мероприятия.</p>
          {deadlinePreview
            ? <DeadlinePreview label={deadlinePreviewLabel} value={deadlinePreview} />
            : !eventDateKnown
              ? <p className="admin-form-help">Последний день подачи будет рассчитан после указания даты мероприятия.</p>
              : <p className="admin-form-help">Укажите количество дней, чтобы рассчитать последний день подачи.</p>}
        </div>}
      </div>
      <div className={`admin-deadline-mode${visibility.fixedDate ? ' is-selected' : ''}`}>
        <label className="admin-deadline-mode-option" htmlFor={`${fieldId}-fixed-date`}><input
          id={`${fieldId}-fixed-date`}
          type="radio"
          name={`${fieldId}-deadline-mode`}
          checked={visibility.fixedDate}
          onChange={() => onChange({ ...value, deadlineMode: 'FIXED_DATE' })}
        /><span>До выбранной даты</span></label>
        {visibility.fixedDate && <div className="admin-deadline-mode-details">
          <Field label="Последний день подачи"><input
            type="date"
            required={value.enabled}
            value={value.fixedDate ?? ''}
            onChange={(event) => onChange({ ...value, fixedDate: event.target.value || null })}
          /></Field>
          <p className="admin-form-help">Обращения принимаются до конца выбранной даты включительно.</p>
          {deadlinePreview && <DeadlinePreview label={deadlinePreviewLabel} value={deadlinePreview} />}
        </div>}
      </div>
    </fieldset>
    <Field label="Email организатора"><input
      type="email"
      maxLength={320}
      required={value.enabled}
      value={value.email ?? ''}
      onChange={(event) => onChange({ ...value, email: event.target.value || null })}
    /></Field>
  </>
}

function DeadlinePreview({ label, value }: { label: string; value: string }) {
  return <p className="admin-inquiry-deadline-preview"><span>{label}</span><strong>{value}</strong></p>
}
