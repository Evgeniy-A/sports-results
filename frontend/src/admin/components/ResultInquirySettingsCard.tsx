/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { type FormEvent, useEffect, useMemo, useState } from 'react'
import type { AdminApi } from '../api'
import type { EventSummary, ResultInquirySettings } from '../types'
import { adminErrorMessage } from '../utils'
import {
  calculateResultInquiryDeadline,
  formatResultInquiryDeadline,
  resultInquirySummary,
} from '../resultInquirySettings'
import { AdminNotice, Loadable, StatusBadge } from './AdminUi'
import { ResultInquirySettingsFields } from './ResultInquirySettingsFields'

export function ResultInquirySettingsCard({ api, event, compact = false }: {
  api: AdminApi
  event: EventSummary
  compact?: boolean
}) {
  const [savedSettings, setSavedSettings] = useState<ResultInquirySettings | null>(null)
  const [draftSettings, setDraftSettings] = useState<ResultInquirySettings | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true); setMessage(null)
    api.resultInquiry(event.id)
      .then((value) => {
        if (!cancelled) {
          setSavedSettings(value)
          setDraftSettings(value)
        }
      })
      .catch((reason) => { if (!cancelled) setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [api, event.endsAt, event.id, event.startsAt, event.timeZone])

  const calculatedDeadline = useMemo(
    () => draftSettings ? calculateResultInquiryDeadline(
      event, draftSettings.deadlineMode, draftSettings.windowDays, draftSettings.fixedDate,
    ) : null,
    [draftSettings, event],
  )
  const summary = savedSettings ? resultInquirySummary(savedSettings, event.timeZone) : null
  const dirty = Boolean(savedSettings && draftSettings
    && configurationKey(savedSettings) !== configurationKey(draftSettings))

  const persist = async (next: ResultInquirySettings) => {
    setBusy(true); setMessage(null)
    try {
      const saved = await api.updateResultInquiry(event.id, {
        enabled: next.enabled,
        deadlineMode: next.deadlineMode,
        windowDays: next.windowDays,
        fixedDate: next.fixedDate,
        email: next.email,
      })
      setSavedSettings(saved)
      setDraftSettings(saved)
      setMessage({ tone: 'success', text: 'Настройки обращений сохранены.' })
    } catch (reason) {
      setMessage({ tone: 'danger', text: adminErrorMessage(reason) })
    } finally { setBusy(false) }
  }

  const submit = (submitEvent: FormEvent) => {
    submitEvent.preventDefault()
    if (draftSettings) void persist(draftSettings)
  }

  return <section className={`admin-card admin-inquiry-settings${compact ? ' compact' : ''}`}>
    <div className="admin-card-heading"><div><h2>Обращения по результатам</h2><p>Управляет только созданием новых обращений. Уже созданные обращения остаются доступны.</p></div>{savedSettings && <StatusBadge value={savedSettings.availability} />}</div>
    {message && <AdminNotice tone={message.tone}>{message.text}</AdminNotice>}
    <Loadable loading={loading} error={null} empty={!savedSettings || !draftSettings}>
      {savedSettings && draftSettings && summary && <form className="admin-form" onSubmit={submit}>
        <dl className="admin-inquiry-summary">
          <div><dt>Статус</dt><dd>{summary.status}</dd></div>
          <div><dt>Срок</dt><dd>{summary.term}</dd></div>
          <div><dt>Принимаются до</dt><dd>{summary.deadline}</dd></div>
        </dl>
        {dirty && <AdminNotice tone="info">Изменения не сохранены. Верхняя сводка показывает действующие настройки.</AdminNotice>}
        <ResultInquirySettingsFields
          value={draftSettings}
          onChange={(next) => setDraftSettings({ ...draftSettings, ...next })}
          deadlinePreview={calculatedDeadline
            ? formatResultInquiryDeadline(calculatedDeadline, event.timeZone)
            : null}
          deadlinePreviewLabel={dirty && draftSettings.enabled
            ? 'После сохранения обращения будут приниматься до:'
            : 'Последний день подачи:'}
          eventDateKnown={Boolean(event.endsAt ?? event.startsAt)}
        />
        <p className="admin-form-help">После окончания срока участники не смогут создавать новые обращения. Уже созданные обращения останутся доступны для обработки.</p>
        {savedSettings.availability === 'CLOSED' && <AdminNotice tone="warning">Чтобы снова открыть приём, увеличьте количество дней или выберите будущую дату.</AdminNotice>}
        <div className="admin-form-actions admin-inquiry-actions">
          <button className="admin-button-primary" disabled={busy}>{busy ? 'Сохраняем…' : 'Сохранить настройки'}</button>
          {savedSettings.enabled
            ? <button className="admin-button-danger-outline admin-inquiry-manual-toggle" type="button" disabled={busy} onClick={() => void persist({ ...draftSettings, enabled: false })}>Закрыть приём</button>
            : <button className="admin-button-secondary admin-inquiry-manual-toggle" type="button" disabled={busy} onClick={() => void persist({ ...draftSettings, enabled: true })}>Включить приём</button>}
        </div>
      </form>}
    </Loadable>
  </section>
}

function configurationKey(settings: ResultInquirySettings): string {
  return JSON.stringify([
    settings.enabled,
    settings.deadlineMode,
    settings.windowDays,
    settings.fixedDate,
    settings.email,
  ])
}
