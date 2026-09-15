/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { EventSummary, IssueStatus, JournalDetail, JournalFilters, JournalItem, PageResponse, Race, ShareBatch } from '../types'
import { formatDuration } from '../../utils/format'
import { adminErrorMessage, bytesLabel, statusRussian } from '../utils'
import { localDateTimeToIso, toDateTimeLocal } from '../time'
import { AdminNotice, AdminPagination, ConfirmDialog, Drawer, Field, Loadable, StatusBadge } from '../components/AdminUi'

const EMPTY_PAGE: PageResponse<JournalItem> = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, sort: 'createdAt', direction: 'desc' }

export function AdminJournalPage({ api }: { api: AdminApi }) {
  const [filters, setFilters] = useState<JournalFilters>(() => filtersFromUrl())
  const [draft, setDraft] = useState<JournalFilters>(() => filtersFromUrl())
  const [page, setPage] = useState(EMPTY_PAGE)
  const [events, setEvents] = useState<EventSummary[]>([])
  const [races, setRaces] = useState<Race[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<number | null>(null)
  const [exportOpen, setExportOpen] = useState(false)
  const [shareBatch, setShareBatch] = useState<ShareBatch | null>(null)

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const [journalPage, eventPage] = await Promise.all([api.journal(filters), api.events({ size: 100 })])
      setPage(journalPage); setEvents(eventPage.content); writeFiltersToUrl(filters)
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, filters])
  useEffect(() => { void load() }, [load])
  useEffect(() => {
    if (!draft.eventId) { setRaces([]); return }
    api.races(draft.eventId)
      .then(setRaces)
      .catch((reason) => setError(adminErrorMessage(reason)))
  }, [api, draft.eventId])

  const apply = (event: FormEvent) => { event.preventDefault(); setFilters({ ...draft, page: 0, size: 50, sort: filters.sort ?? 'createdAt', direction: filters.direction ?? 'desc' }) }
  const reset = () => { const empty: JournalFilters = { queueScope: 'ALL', page: 0, size: 50, sort: 'createdAt', direction: 'desc' }; setDraft(empty); setFilters(empty) }

  return <div className="admin-stack">
    <div className="admin-page-heading"><div><p className="admin-eyebrow">Поддержка</p><h1>Журнал обращений</h1><p>Глобальная историческая выборка по всем мероприятиям, включая архив.</p></div><button className="admin-button-primary" onClick={() => setExportOpen(true)}>Выгрузить XLSX</button></div>
    <form className="admin-journal-filters" onSubmit={apply}>
      <Field label="ID обращения"><input type="number" min="1" value={draft.issueId ?? ''} onChange={(change) => setDraft({ ...draft, issueId: change.target.value ? Number(change.target.value) : undefined })} /></Field>
      <Field label="Мероприятие"><select value={draft.eventId ?? ''} onChange={(change) => setDraft({ ...draft, eventId: change.target.value ? Number(change.target.value) : undefined, raceId: undefined })}><option value="">Все мероприятия</option>{events.map((event) => <option key={event.id} value={event.id}>{event.name}</option>)}</select></Field>
      <Field label="Город"><input value={draft.location ?? ''} onChange={(change) => setDraft({ ...draft, location: change.target.value || undefined })} /></Field>
      <Field label="Дата мероприятия от"><input type="date" value={draft.eventDateFrom ?? ''} onChange={(change) => setDraft({ ...draft, eventDateFrom: change.target.value || undefined })} /></Field>
      <Field label="Дата мероприятия до"><input type="date" value={draft.eventDateTo ?? ''} onChange={(change) => setDraft({ ...draft, eventDateTo: change.target.value || undefined })} /></Field>
      <Field label="Обращение создано от"><input type="datetime-local" value={toDateTimeLocal(draft.createdFrom ?? null)} onChange={(change) => setDraft({ ...draft, createdFrom: localDateTimeToIso(change.target.value) ?? undefined })} /></Field>
      <Field label="Обращение создано до"><input type="datetime-local" value={toDateTimeLocal(draft.createdTo ?? null)} onChange={(change) => setDraft({ ...draft, createdTo: localDateTimeToIso(change.target.value) ?? undefined })} /></Field>
      <Field label="Старт"><select disabled={!draft.eventId} value={draft.raceId ?? ''} onChange={(change) => setDraft({ ...draft, raceId: change.target.value ? Number(change.target.value) : undefined })}><option value="">Все старты</option>{races.map((race) => <option key={race.id} value={race.id}>{race.name}</option>)}</select></Field>
      <Field label="Bib"><input value={draft.bib ?? ''} onChange={(change) => setDraft({ ...draft, bib: change.target.value || undefined })} /></Field>
      <Field label="Участник"><input value={draft.participant ?? ''} onChange={(change) => setDraft({ ...draft, participant: change.target.value || undefined })} /></Field>
      <Field label="Тип"><select value={draft.issueType ?? ''} onChange={(change) => setDraft({ ...draft, issueType: change.target.value as JournalFilters['issueType'] || undefined })}><option value="">Все</option><option value="MISSING_RESULT">Нет результата</option><option value="RESULT_CORRECTION">Исправление</option></select></Field>
      <Field label="Причина"><select value={draft.correctionReason ?? ''} onChange={(change) => setDraft({ ...draft, correctionReason: change.target.value || undefined })}><option value="">Все</option><option value="OFFICIAL_TIME">Официальное время</option><option value="CHIP_TIME">Чистое время</option><option value="RESULT_STATUS">Статус</option><option value="RACE_OR_FORMAT">Старт</option><option value="OTHER">Другое</option></select></Field>
      <Field label="Очередь"><select value={draft.queueScope ?? 'ALL'} onChange={(change) => setDraft({ ...draft, queueScope: change.target.value as JournalFilters['queueScope'] })}><option value="ALL">Все</option><option value="CURRENT">Текущие</option><option value="ARCHIVED">Архивные</option></select></Field>
      <fieldset className="admin-status-filter"><legend>Статус</legend>{(['NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'] as IssueStatus[]).map((status) => <label key={status}><input type="checkbox" checked={draft.status?.includes(status) ?? false} onChange={() => setDraft({ ...draft, status: toggleStatus(draft.status, status) })} />{statusRussian(status)}</label>)}</fieldset>
      <div className="admin-filter-actions"><button className="admin-button-primary">Применить</button><button className="admin-button-secondary" type="button" onClick={reset}>Сбросить</button></div>
    </form>
    {shareBatch && <AdminNotice tone={shareBatch.revokedAt ? 'warning' : 'success'}><strong>Последняя выгрузка:</strong> batch {shareBatch.batchId}. Ссылок: {shareBatch.grantCount}. {shareBatch.revokedAt ? 'Доступ отозван.' : `Доступ ${shareBatch.expiresAt ? `до ${new Date(shareBatch.expiresAt).toLocaleString('ru-RU')}` : 'без срока'}.`}</AdminNotice>}
    <div className="admin-section-toolbar"><span className="admin-count">{page.totalElements} обращений</span><Field label="Сортировка"><select value={`${filters.sort ?? 'createdAt'}:${filters.direction ?? 'desc'}`} onChange={(change) => { const [sort, direction] = change.target.value.split(':'); setFilters({ ...filters, sort, direction: direction as 'asc' | 'desc', page: 0 }); setDraft({ ...draft, sort, direction: direction as 'asc' | 'desc' }) }}><option value="createdAt:desc">Сначала новые</option><option value="createdAt:asc">Сначала старые</option><option value="eventDate:desc">По дате Event</option><option value="issueId:asc">По ID</option></select></Field></div>
    <Loadable loading={loading} error={error} empty={!page.content.length}><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>ID</th><th>Мероприятие / город</th><th>Старт на момент обращения</th><th>Bib / участник</th><th>Тип</th><th>Причина</th><th>Статус</th><th>Дата</th><th>Вложения</th><th /></tr></thead><tbody>{page.content.map((item) => <tr key={item.issueId}><td>#{item.issueId}</td><td><strong>{item.event.eventName}</strong><small>{item.event.location ?? '—'}</small></td><td>{historicalStartName(item.sportFormat.name, item.race.name)}</td><td><strong>{item.participant.bib ?? '—'}</strong><small>{item.participant.displayName}</small></td><td>{item.issueType === 'MISSING_RESULT' ? 'Нет результата' : 'Исправление'}</td><td>{item.correctionReason ?? '—'}</td><td><StatusBadge value={item.status} />{item.queueArchivedAt && <small>Архив</small>}</td><td>{new Date(item.createdAt).toLocaleString('ru-RU')}</td><td>{item.attachmentCount}</td><td><button className="admin-row-link" onClick={() => setSelected(item.issueId)}>Открыть</button></td></tr>)}</tbody></table></div><AdminPagination page={page.page} totalPages={page.totalPages} onChange={(value) => { setFilters({ ...filters, page: value }); setDraft({ ...draft, page: value }) }} /></Loadable>
    {selected && <JournalDrawer api={api} issueId={selected} onClose={() => setSelected(null)} />}
    {exportOpen && <ExportDialog api={api} filters={filters} total={page.totalElements} onClose={() => setExportOpen(false)} onBatch={setShareBatch} />}
  </div>
}

function ExportDialog({ api, filters, total, onClose, onBatch }: { api: AdminApi; filters: JournalFilters; total: number; onClose: () => void; onBatch: (batch: ShareBatch) => void }) {
  const [lifetime, setLifetime] = useState('30')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [batch, setBatch] = useState<ShareBatch | null>(null)
  const [revokeConfirm, setRevokeConfirm] = useState(false)
  const exportFile = async () => {
    setBusy(true); setError(null)
    try {
      const artifact = await api.exportJournal(filters, lifetime === 'none' ? null : Number(lifetime), lifetime === 'none')
      const url = URL.createObjectURL(artifact.blob)
      const link = document.createElement('a'); link.href = url; link.download = artifact.filename; link.click(); URL.revokeObjectURL(url)
      if (artifact.shareBatchId) { const created = await api.shareBatch(artifact.shareBatchId); setBatch(created); onBatch(created) }
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  const revoke = async () => {
    if (!batch) return; setBusy(true); setError(null)
    try { const revoked = await api.revokeShareBatch(batch.batchId); setBatch(revoked); onBatch(revoked); setRevokeConfirm(false) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  return <Drawer title="Выгрузить журнал в XLSX" onClose={onClose}><div className="admin-stack"><AdminNotice tone="info">В экспорт попадут все обращения по текущим фильтрам: <strong>{total}</strong>. Пагинация страницы на экспорт не влияет.</AdminNotice>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<fieldset className="admin-radio-list"><legend>Срок действия ссылок на вложения</legend>{[['7', '7 дней'], ['30', '30 дней'], ['90', '90 дней'], ['none', 'Без срока действия']].map(([value, label]) => <label key={value}><input type="radio" name="lifetime" checked={lifetime === value} onChange={() => setLifetime(value)} />{label}</label>)}</fieldset><button className="admin-button-primary" disabled={busy} onClick={() => void exportFile()}>{busy ? 'Формируем…' : 'Сформировать XLSX'}</button>{batch && <section className="admin-card"><h3>Файл сформирован</h3><p>Доступ к вложениям создан. Batch: <code>{batch.batchId}</code></p>{batch.revokedAt ? <AdminNotice tone="warning">Доступ уже отозван.</AdminNotice> : <button className="admin-button-danger" onClick={() => setRevokeConfirm(true)}>Отозвать доступ</button>}</section>}{revokeConfirm && <ConfirmDialog title="Отозвать доступ?" description="Все ссылки на вложения, созданные этой выгрузкой, перестанут работать." confirmLabel="Отозвать все ссылки" danger busy={busy} onConfirm={() => void revoke()} onClose={() => setRevokeConfirm(false)} />}</div></Drawer>
}

function JournalDrawer({ api, issueId, onClose }: { api: AdminApi; issueId: number; onClose: () => void }) {
  const [detail, setDetail] = useState<JournalDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const openAttachment = async (attachmentId: number) => { if (!detail) return; try { const auth = await api.authorizeAttachment(detail.eventId, detail.issueId, attachmentId); window.open(auth.downloadUrl, '_blank', 'noopener,noreferrer') } catch (reason) { setError(adminErrorMessage(reason)) } }
  useEffect(() => { api.journalDetail(issueId).then(setDetail).catch((reason) => setError(adminErrorMessage(reason))).finally(() => setLoading(false)) }, [api, issueId])
  return <Drawer title={`Журнал · обращение №${issueId}`} onClose={onClose}><Loadable loading={loading} error={error} empty={!detail}>{detail && <div className="admin-stack"><div className="admin-inline-badges"><StatusBadge value={detail.status} />{detail.queueArchivedAt && <span className="admin-badge admin-badge-neutral">Архив</span>}</div><section className="admin-detail-section"><h3>Обращение</h3><dl className="admin-detail-list"><div><dt>Контакт</dt><dd>{detail.contactEmail}</dd></div><div><dt>Сообщение</dt><dd>{detail.message}</dd></div><div><dt>Заявлено</dt><dd>Gun {formatDuration(detail.claimedGunTimeMs)} · Chip {formatDuration(detail.claimedChipTimeMs)}</dd></div></dl></section><section className="admin-detail-section historical"><h3>Состояние на момент обращения</h3><dl className="admin-detail-list"><div><dt>Мероприятие</dt><dd>{detail.historicalSnapshot.eventName}</dd></div><div><dt>Старт на момент обращения</dt><dd>{historicalStartName(detail.historicalSnapshot.sportFormatName, detail.historicalSnapshot.raceName)}</dd></div><div><dt>Bib / участник</dt><dd>{detail.historicalSnapshot.bib ?? '—'} · {detail.historicalSnapshot.displayName}</dd></div><div><dt>Факт результата</dt><dd>{statusRussian(detail.historicalSnapshot.observedResultStatus)} · Gun {formatDuration(detail.historicalSnapshot.observedGunTimeMs)} · Chip {formatDuration(detail.historicalSnapshot.observedChipTimeMs)}</dd></div></dl></section><section className="admin-detail-section current"><h3>Текущее состояние</h3>{detail.currentContext.registrationExists ? <dl className="admin-detail-list"><div><dt>Участник</dt><dd>#{detail.currentContext.registrationId}{detail.currentContext.registrationRetired ? ' · удалён из актуального набора' : ''}</dd></div><div><dt>Старт</dt><dd>{detail.currentContext.race?.name ?? '—'}</dd></div><div><dt>Категория</dt><dd>{detail.currentContext.category?.name ?? '—'}</dd></div><div><dt>Результат</dt><dd>{detail.currentContext.result ? `${statusRussian(detail.currentContext.result.status)} · Gun ${formatDuration(detail.currentContext.result.gunTimeMs)} · Chip ${formatDuration(detail.currentContext.result.chipTimeMs)}` : 'Нет'}</dd></div></dl> : <AdminNotice tone="warning">Текущий участник отсутствует.</AdminNotice>}</section><section className="admin-detail-section"><h3>История</h3><ol className="admin-history">{detail.history.map((item) => <li key={item.historyId}><StatusBadge value={item.action} /><div><strong>{item.fromStatus ? `${statusRussian(item.fromStatus)} → ${statusRussian(item.toStatus)}` : statusRussian(item.toStatus)}</strong><small>{new Date(item.createdAt).toLocaleString('ru-RU')} · {item.actor ?? 'system'} {item.reason ? `· ${item.reason}` : ''}</small></div></li>)}</ol></section><section className="admin-detail-section"><h3>Вложения ({detail.attachmentCount})</h3><ul className="admin-attachment-list">{detail.attachments.map((attachment) => <li key={attachment.attachmentId}><div><strong>{attachment.originalFileName}</strong><small>{bytesLabel(attachment.sizeBytes)} · {attachment.scanStatus}</small></div>{attachment.uploadStatus === 'UPLOADED' && attachment.scanStatus === 'CLEAN' && <button className="admin-link-button" onClick={() => void openAttachment(attachment.attachmentId)}>Открыть</button>}</li>)}</ul></section></div>}</Loadable></Drawer>
}

function toggleStatus(current: IssueStatus[] | undefined, value: IssueStatus): IssueStatus[] | undefined {
  const next = current?.includes(value) ? current.filter((item) => item !== value) : [...(current ?? []), value]
  return next.length ? next : undefined
}

function filtersFromUrl(): JournalFilters {
  const query = new URLSearchParams(window.location.search)
  const number = (name: string) => query.get(name) ? Number(query.get(name)) : undefined
  const statuses = query.getAll('status').filter((value): value is IssueStatus => ['NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'].includes(value))
  return { issueId: number('issueId'), eventId: number('eventId'), location: query.get('location') || undefined, eventDateFrom: query.get('eventDateFrom') || undefined, eventDateTo: query.get('eventDateTo') || undefined, createdFrom: query.get('createdFrom') || undefined, createdTo: query.get('createdTo') || undefined, raceId: number('raceId'), bib: query.get('bib') || undefined, participant: query.get('participant') || undefined, issueType: (query.get('issueType') as JournalFilters['issueType']) || undefined, correctionReason: query.get('correctionReason') || undefined, status: statuses.length ? statuses : undefined, queueScope: (query.get('queueScope') as JournalFilters['queueScope']) || 'ALL', page: number('page') ?? 0, size: 50, sort: query.get('sort') || 'createdAt', direction: (query.get('direction') as 'asc' | 'desc') || 'desc' }
}

function historicalStartName(formatName: string | null, raceName: string): string {
  return formatName ? `${formatName} · ${raceName}` : raceName
}

function writeFiltersToUrl(filters: JournalFilters): void {
  const query = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => { if (value === undefined || value === null || value === '') return; if (Array.isArray(value)) value.forEach((item) => query.append(key, String(item))); else query.set(key, String(value)) })
  window.history.replaceState({}, '', `/admin/support/issues?${query.toString()}`)
}
