/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { AdminApi } from '../api'
import type { EventIssueDetail, EventSummary, IssueStatus, Race } from '../types'
import { formatDuration } from '../../utils/format'
import { AdminLink } from '../router'
import { adminErrorMessage, bytesLabel, statusRussian } from '../utils'
import { AdminResultEditor } from '../components/AdminResultEditor'
import { AdminNotice, ConfirmDialog, Field, Loadable, StatusBadge } from '../components/AdminUi'
import { authorizeAndOpenAttachment } from '../attachmentOpening'

export function AdminEventIssuePage({ api, eventId, issueId, returnTo }: {
  api: AdminApi
  eventId: number
  issueId: number
  returnTo: string | null
}) {
  const [event, setEvent] = useState<EventSummary | null>(null)
  const [races, setRaces] = useState<Race[]>([])
  const [detail, setDetail] = useState<EventIssueDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [comment, setComment] = useState('')
  const [archiveConfirm, setArchiveConfirm] = useState(false)
  const backHref = safeReturnTo(eventId, returnTo)

  const load = useCallback(async (showLoading = true) => {
    if (showLoading) setLoading(true)
    setError(null)
    try {
      const [eventValue, raceValues, issueValue] = await Promise.all([
        api.event(eventId),
        api.races(eventId),
        api.eventIssue(eventId, issueId),
      ])
      setEvent(eventValue); setRaces(raceValues); setDetail(issueValue)
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { if (showLoading) setLoading(false) }
  }, [api, eventId, issueId])
  useEffect(() => { void load() }, [load])

  const changeStatus = async (next: IssueStatus) => {
    if (!detail) return
    setBusy(true); setError(null)
    try {
      await api.updateIssueStatus(eventId, issueId, detail.status, next, comment)
      setComment('')
      await load(false)
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  const archive = async () => {
    setBusy(true); setError(null)
    try { await api.archiveIssue(eventId, issueId, 'MANUAL'); setArchiveConfirm(false); await load(false) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  const openAttachment = async (attachmentId: number) => {
    setBusy(true); setError(null)
    try {
      await authorizeAndOpenAttachment(
        () => api.authorizeAttachment(eventId, issueId, attachmentId),
      )
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  return <Loadable loading={loading} error={detail && event ? null : error} empty={!detail || !event}>{detail && event && <div className="admin-issue-page">
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    <header className="admin-event-heading admin-issue-page-heading">
      <div><AdminLink href={backHref}>← Назад к обращениям</AdminLink><p className="admin-eyebrow">{event.name} · обращение №{detail.issueId}</p><h1>{detail.registration?.displayName ?? detail.snapshot.displayName}</h1><p>Стартовый номер {detail.registration?.bib ?? detail.snapshot.bib ?? '—'} · {detail.registration?.raceName ?? detail.snapshot.raceName}</p></div>
      <div className="admin-heading-badges"><StatusBadge value={detail.status} /><span>{issueTypeLabel(detail)}</span><time>{formatDateTime(detail.createdAt)}</time></div>
    </header>

    <div className="admin-issue-workspace">
      <aside className="admin-issue-context admin-stack">
        <section className="admin-detail-section"><h3>Обращение участника</h3><dl className="admin-detail-list"><div><dt>Причина</dt><dd>{detail.correctionReason ? statusRussian(detail.correctionReason) : issueTypeLabel(detail)}</dd></div><div><dt>Сообщение</dt><dd>{detail.message}</dd></div><div><dt>Контакт</dt><dd><a href={`mailto:${detail.contactEmail}`}>{detail.contactEmail}</a></dd></div><div><dt>Заявленное значение</dt><dd>Официальное время: {formatDuration(detail.claimedGunTimeMs)}<br />Чистое время: {formatDuration(detail.claimedChipTimeMs)}</dd></div>{(detail.estimatedStartAt || detail.estimatedFinishAt) && <div><dt>Заявленный интервал</dt><dd>{detail.estimatedStartAt ? formatDateTime(detail.estimatedStartAt) : '—'} — {detail.estimatedFinishAt ? formatDateTime(detail.estimatedFinishAt) : '—'}</dd></div>}<div><dt>Создано</dt><dd>{formatDateTime(detail.createdAt)}</dd></div></dl></section>

        <section className="admin-detail-section"><h3>Вложения ({detail.attachments.length})</h3>{detail.attachments.length ? <ul className="admin-attachment-list">{detail.attachments.map((attachment) => <li key={attachment.attachmentId}><div><strong>{attachment.originalFileName}</strong><small>{bytesLabel(attachment.sizeBytes)} · {attachment.contentType ?? 'неизвестный тип'}</small></div><div><StatusBadge value={attachment.uploadStatus} /><StatusBadge value={attachment.scanStatus} />{attachment.uploadStatus === 'UPLOADED' && attachment.scanStatus === 'CLEAN' && <button className="admin-link-button" type="button" disabled={busy} onClick={() => void openAttachment(attachment.attachmentId)}>Открыть</button>}</div></li>)}</ul> : <p className="admin-muted">Вложения: нет</p>}</section>

        <section className="admin-detail-section historical"><h3>Состояние на момент обращения</h3><p className="admin-muted">Неизменяемый снимок — источник для сравнения, но не для сохранения.</p><dl className="admin-detail-list"><div><dt>Мероприятие</dt><dd>{detail.snapshot.eventName} · {detail.snapshot.eventLocation ?? '—'}</dd></div><div><dt>Старт</dt><dd>{detail.snapshot.raceName}</dd></div><div><dt>Стартовый номер / участник</dt><dd>{detail.snapshot.bib ?? '—'} · {detail.snapshot.displayName}</dd></div><div><dt>Категория</dt><dd>{detail.snapshot.effectiveCategoryName ?? '—'}<small>Исходная: {detail.snapshot.sourceCategory ?? '—'}</small></dd></div><div><dt>Статус и время</dt><dd>{statusRussian(detail.snapshot.observedResultStatus ?? 'UNKNOWN')}<br />Официальное: {formatDuration(detail.snapshot.observedGunTimeMs)}<br />Чистое: {formatDuration(detail.snapshot.observedChipTimeMs)}</dd></div></dl></section>

        <section className="admin-detail-section"><h3>История</h3>{detail.history.length ? <ol className="admin-history">{detail.history.map((item) => <li key={item.historyId}><StatusBadge value={item.action} /><div><strong>{historyLabel(item.action, item.fromStatus, item.toStatus)}</strong><small>{formatDateTime(item.createdAt)} · {item.actor ?? 'система'}{item.reason ? ` · ${item.reason}` : ''}</small></div></li>)}</ol> : <p className="admin-muted">История действий пока пуста.</p>}</section>
      </aside>

      <main className="admin-issue-current admin-stack">
        {detail.result ? <AdminResultEditor api={api} resultId={detail.result.resultId} races={races} hideBirthDate issueWorkspace resultSaveLabel="Сохранить изменения" issueFocus={{ reason: detail.correctionReason, claimedGunTimeMs: detail.claimedGunTimeMs, claimedChipTimeMs: detail.claimedChipTimeMs, snapshotGunTimeMs: detail.snapshot.observedGunTimeMs, snapshotChipTimeMs: detail.snapshot.observedChipTimeMs }} onSaved={() => load(false)} /> : <section className="admin-card"><h3>Текущий результат</h3><AdminNotice tone="warning">У текущей регистрации нет результата. Существующий редактор изменяет только уже созданные результаты; создание нового результата в эту задачу не входит.</AdminNotice>{detail.registration && <dl className="admin-detail-list"><div><dt>Участник</dt><dd>{detail.registration.displayName}</dd></div><div><dt>Стартовый номер</dt><dd>{detail.registration.bib ?? '—'}</dd></div><div><dt>Старт</dt><dd>{detail.registration.raceName}</dd></div></dl>}</section>}

        <section className="admin-card admin-issue-workflow"><h3>Решение по обращению</h3><p className="admin-muted">Сохранение результата не закрывает обращение. Статус меняется только кнопками ниже.</p>{['NEW', 'IN_PROGRESS'].includes(detail.status) ? <><Field label="Комментарий специалиста" hint="Комментарий сохранится в истории вместе с выбранным действием."><textarea maxLength={1000} value={comment} onChange={(change) => setComment(change.target.value)} /></Field><div className="admin-form-actions">{detail.status === 'NEW' && <button className="admin-button-primary" type="button" disabled={busy} onClick={() => void changeStatus('IN_PROGRESS')}>Взять в работу</button>}<button className="admin-button-secondary" type="button" disabled={busy} onClick={() => void changeStatus('REJECTED')}>Отклонить</button><button className="admin-button-primary" type="button" disabled={busy} onClick={() => void changeStatus('RESOLVED')}>Решить обращение</button></div></> : <AdminNotice tone="info">Статус обращения: {statusRussian(detail.status)}.</AdminNotice>}{!detail.archive.queueArchivedAt ? <button className="admin-link-button danger" type="button" disabled={busy} onClick={() => setArchiveConfirm(true)}>Архивировать в очереди</button> : <AdminNotice tone="info">Архивировано: {statusRussian(detail.archive.queueArchiveReason ?? 'MANUAL')} · {detail.archive.queueArchivedBy}</AdminNotice>}</section>
      </main>
    </div>
    {archiveConfirm && <ConfirmDialog title="Архивировать обращение?" description="Архивирование удалит обращение из рабочей очереди, но не изменит его статус и не удалит историю." confirmLabel="Архивировать" danger busy={busy} onConfirm={() => void archive()} onClose={() => setArchiveConfirm(false)} />}
  </div>}</Loadable>
}

function safeReturnTo(eventId: number, value: string | null): string {
  if (!value) return `/admin/events/${eventId}?tab=issues`
  return value.startsWith(`/admin/events/${eventId}?`) ? value : `/admin/events/${eventId}?tab=issues`
}

function issueTypeLabel(detail: EventIssueDetail): string {
  return detail.issueType === 'MISSING_RESULT' ? 'Результат отсутствует' : 'Исправление результата'
}

function historyLabel(action: string, from: IssueStatus | null, to: IssueStatus | null): string {
  if (action === 'CREATED') return 'Обращение создано'
  if (action === 'QUEUE_ARCHIVED') return 'Перенесено в архив очереди'
  if (from && to) return `${statusRussian(from)} → ${statusRussian(to)}`
  return statusRussian(action)
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
