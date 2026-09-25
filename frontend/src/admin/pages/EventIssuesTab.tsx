/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { AdminApi } from '../api'
import type { EventIssueItem, EventSummary, PageResponse, QueueScope } from '../types'
import { navigateAdmin } from '../router'
import { adminErrorMessage, statusRussian } from '../utils'
import { AdminPagination, Field, Loadable, StatusBadge } from '../components/AdminUi'
import { ResultInquirySettingsCard } from '../components/ResultInquirySettingsCard'

const EMPTY_PAGE: PageResponse<EventIssueItem> = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, sort: 'createdAt', direction: 'desc' }

interface IssueFilters {
  issueId: string
  bib: string
  issueType: string
  correctionReason: string
  status: string
  queueScope: QueueScope
  page: number
}

export function EventIssuesTab({ api, event }: { api: AdminApi; event: EventSummary }) {
  const [page, setPage] = useState(EMPTY_PAGE)
  const [filters, setFilters] = useState<IssueFilters>(() => filtersFromUrl())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try { setPage(await api.eventIssues(event.id, { issueIdFrom: filters.issueId ? Number(filters.issueId) : undefined, issueIdTo: filters.issueId ? Number(filters.issueId) : undefined, bib: filters.bib, issueType: filters.issueType, correctionReason: filters.correctionReason, status: filters.status, queueScope: filters.queueScope, page: filters.page, size: 50 })) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, event.id, filters])
  useEffect(() => { void load() }, [load])
  useEffect(() => { window.history.replaceState({}, '', queueUrl(event.id, filters)) }, [event.id, filters])
  const set = (field: keyof IssueFilters, value: string | number) => setFilters((current) => ({ ...current, [field]: value, page: field === 'page' ? Number(value) : 0 }))
  const openIssue = (issueId: number) => {
    const returnTo = queueUrl(event.id, filters)
    navigateAdmin(`/admin/events/${event.id}/results/issues/${issueId}?returnTo=${encodeURIComponent(returnTo)}`)
  }

  return <div className="admin-stack">
    <ResultInquirySettingsCard api={api} event={event} compact />
    <div className="admin-section-toolbar"><div><h2>Рабочая очередь обращений</h2><p>Выберите строку для работы с обращением. Архив и workflow-статус остаются независимыми.</p></div><span className="admin-count">{page.totalElements} обращений</span></div>
    <div className="admin-filter-grid"><Field label="ID"><input type="number" min="1" value={filters.issueId} onChange={(change) => set('issueId', change.target.value)} /></Field><Field label="Стартовый номер"><input value={filters.bib} onChange={(change) => set('bib', change.target.value)} /></Field><Field label="Тип"><select value={filters.issueType} onChange={(change) => set('issueType', change.target.value)}><option value="">Все</option><option value="MISSING_RESULT">Нет результата</option><option value="RESULT_CORRECTION">Исправление результата</option></select></Field><Field label="Причина"><select value={filters.correctionReason} onChange={(change) => set('correctionReason', change.target.value)}><option value="">Все</option><option value="OFFICIAL_TIME">Официальное время</option><option value="CHIP_TIME">Чистое время</option><option value="RESULT_STATUS">Статус</option><option value="RACE_OR_FORMAT">Старт</option><option value="OTHER">Другое</option></select></Field><Field label="Статус"><select value={filters.status} onChange={(change) => set('status', change.target.value)}><option value="">Все</option><option value="NEW">Новое</option><option value="IN_PROGRESS">В работе</option><option value="RESOLVED">Решено</option><option value="REJECTED">Отклонено</option></select></Field><Field label="Очередь"><select value={filters.queueScope} onChange={(change) => set('queueScope', change.target.value)}><option value="CURRENT">Текущие</option><option value="ARCHIVED">Архивные</option><option value="ALL">Все</option></select></Field></div>
    <Loadable loading={loading} error={error} empty={!page.content.length}><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>ID</th><th>Bib / участник</th><th>Старт</th><th>Тип</th><th>Причина</th><th>Статус</th><th>Дата</th><th>Вложения</th></tr></thead><tbody>{page.content.map((issue) => <IssueRow key={issue.issueId} issue={issue} onOpen={() => openIssue(issue.issueId)} />)}</tbody></table></div><AdminPagination page={page.page} totalPages={page.totalPages} onChange={(value) => set('page', value)} /></Loadable>
  </div>
}

function IssueRow({ issue, onOpen }: { issue: EventIssueItem; onOpen: () => void }) {
  return <tr className="admin-clickable-row" tabIndex={0} role="link" aria-label={`Открыть обращение №${issue.issueId}`} onClick={(event) => {
    if ((event.target as HTMLElement).closest('a, button, input, select, textarea')) return
    onOpen()
  }} onKeyDown={(event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return
    event.preventDefault(); onOpen()
  }}><td>#{issue.issueId}</td><td><strong>{issue.registration.bib ?? '—'}</strong><small>{issue.registration.displayName}</small></td><td>{issue.race.raceName}</td><td>{issue.issueType === 'MISSING_RESULT' ? 'Нет результата' : 'Исправление'}</td><td>{issue.correctionReason ? statusRussian(issue.correctionReason) : '—'}</td><td><StatusBadge value={issue.status} />{issue.queueArchivedAt && <small>В архиве</small>}</td><td>{new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(issue.createdAt))}</td><td>{issue.attachmentCount}</td></tr>
}

function filtersFromUrl(): IssueFilters {
  const params = new URLSearchParams(window.location.search)
  const queueScope = params.get('queueScope')
  return {
    issueId: params.get('issueId') ?? '',
    bib: params.get('bib') ?? '',
    issueType: params.get('issueType') ?? '',
    correctionReason: params.get('correctionReason') ?? '',
    status: params.get('status') ?? '',
    queueScope: queueScope === 'ARCHIVED' || queueScope === 'ALL' ? queueScope : 'CURRENT',
    page: Math.max(0, Number(params.get('issuePage')) || 0),
  }
}

function queueUrl(eventId: number, filters: IssueFilters): string {
  const params = new URLSearchParams({ tab: 'issues' })
  if (filters.issueId) params.set('issueId', filters.issueId)
  if (filters.bib) params.set('bib', filters.bib)
  if (filters.issueType) params.set('issueType', filters.issueType)
  if (filters.correctionReason) params.set('correctionReason', filters.correctionReason)
  if (filters.status) params.set('status', filters.status)
  if (filters.queueScope !== 'CURRENT') params.set('queueScope', filters.queueScope)
  if (filters.page) params.set('issuePage', String(filters.page))
  return `/admin/events/${eventId}?${params.toString()}`
}
