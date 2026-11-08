/* oxlint-disable react/set-state-in-effect -- remote workspace state is loaded from effects */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import type { AdminApi } from '../api'
import type {
  AdminUser,
  EventSummary,
  IssueStatus,
  JournalDetail,
  JournalFilters,
  JournalSyncSummary,
  JournalWorkspaceRow,
  JournalWorkspaceSheet,
  JournalWorkItem,
  JournalWorkItemResponse,
  Race,
  ShareBatch,
} from '../types'
import { formatDuration } from '../../utils/format'
import { adminErrorMessage, bytesLabel, statusRussian } from '../utils'
import { authorizeAndOpenAttachment } from '../attachmentOpening'
import { parseResultTime, formatResultTime } from '../resultTime'
import { groupJournalRowsByCity } from '../journalGrouping'
import { AdminNotice, AdminPagination, ConfirmDialog, Drawer, Field, Loadable, StatusBadge } from '../components/AdminUi'

const PAGE_SIZE = 100
const RESULT_STATUSES = ['finished', 'running', 'notstarted', 'disqualified', 'quarantine']

export function AdminJournalPage({ api }: { api: AdminApi }) {
  const [filters, setFilters] = useState<JournalFilters>(() => filtersFromUrl())
  const [draft, setDraft] = useState<JournalFilters>(() => filtersFromUrl())
  const [sheets, setSheets] = useState<JournalWorkspaceSheet[]>([])
  const [selectedSheet, setSelectedSheet] = useState<string | null>(null)
  const [rows, setRows] = useState<JournalWorkspaceRow[]>([])
  const [events, setEvents] = useState<EventSummary[]>([])
  const [races, setRaces] = useState<Race[]>([])
  const [raceOptionsByEvent, setRaceOptionsByEvent] = useState<Record<number, Race[]>>({})
  const [users, setUsers] = useState<AdminUser[]>([])
  const [collapsedCities, setCollapsedCities] = useState<Set<string>>(new Set())
  const [pageNumber, setPageNumber] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busyIssue, setBusyIssue] = useState<number | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [selected, setSelected] = useState<number | null>(null)
  const [exportOpen, setExportOpen] = useState(false)
  const [shareBatch, setShareBatch] = useState<ShareBatch | null>(null)

  const selectedSheetData = useMemo(
    () => sheets.find((sheet) => sheetKey(sheet) === selectedSheet) ?? null,
    [selectedSheet, sheets],
  )

  const loadSheets = useCallback(async () => {
    const next = await api.journalSheets()
    setSheets(next)
    setSelectedSheet((current) => current && next.some((sheet) => sheetKey(sheet) === current)
      ? current
      : next[0] ? sheetKey(next[0]) : null)
  }, [api])

  const loadRows = useCallback(async () => {
    if (!selectedSheetData) { setRows([]); return }
    setLoading(true); setError(null)
    try {
      const next = await api.journalWorkspaceRows(selectedSheetData.seriesId, selectedSheetData.year)
      setRows(next)
      const eventIds = [...new Set(next.map((row) => row.eventId))]
      const options = await Promise.all(eventIds.map(async (eventId) => [eventId, await api.races(eventId)] as const))
      setRaceOptionsByEvent(Object.fromEntries(options))
      const groups = cityGroups(next)
      setCollapsedCities(new Set(groups
        .filter((group) => !group.rows.some(isActiveRow))
        .map((group) => group.city)))
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, selectedSheetData])

  useEffect(() => {
    Promise.all([loadSheets(), api.events({ size: 100 }), api.users()])
      .then(([, eventPage, adminUsers]) => { setEvents(eventPage.content); setUsers(adminUsers.filter((user) => user.active)) })
      .catch((reason) => setError(adminErrorMessage(reason)))
  }, [api, loadSheets])
  useEffect(() => { void loadRows() }, [loadRows])
  useEffect(() => { writeFiltersToUrl(filters); setPageNumber(0) }, [filters])
  useEffect(() => {
    if (!draft.eventId) { setRaces([]); return }
    api.races(draft.eventId).then(setRaces).catch((reason) => setError(adminErrorMessage(reason)))
  }, [api, draft.eventId])

  const filteredRows = useMemo(() => rows.filter((row) => matchesFilters(row, filters)), [filters, rows])
  const totalPages = Math.ceil(filteredRows.length / PAGE_SIZE)
  const visibleRows = filteredRows.slice(pageNumber * PAGE_SIZE, (pageNumber + 1) * PAGE_SIZE)
  const groups = cityGroups(visibleRows)

  const apply = (event: FormEvent) => {
    event.preventDefault()
    setFilters({ ...draft, page: 0, size: PAGE_SIZE })
  }
  const resetFilters = () => {
    const empty: JournalFilters = { queueScope: 'ALL', page: 0, size: PAGE_SIZE }
    setDraft(empty); setFilters(empty)
  }

  const saveWork = async (row: JournalWorkspaceRow, patch: Partial<WorkDraft>) => {
    const current = workDraft(row)
    setBusyIssue(row.issueId); setError(null); setNotice(null)
    try {
      const response = await api.updateJournalWorkItem(row.issueId, {
        expectedVersion: row.workItem?.version ?? 0,
        desiredResultStatus: current.desiredResultStatus,
        desiredGunTimeMs: current.desiredGunTimeMs,
        desiredChipTimeMs: current.desiredChipTimeMs,
        desiredDisplayName: current.desiredDisplayName,
        desiredGender: current.desiredGender,
        desiredBirthDate: current.desiredBirthDate,
        desiredBib: current.desiredBib,
        desiredRaceId: current.desiredRaceId,
        assigneeUserId: current.assigneeUserId,
        internalComment: current.internalComment,
        ...patch,
      })
      setRows((currentRows) => currentRows.map((candidate) => candidate.issueId === row.issueId
        ? { ...candidate, workItem: workFromResponse(response), updatedAt: response.updatedAt ?? candidate.updatedAt }
        : candidate))
      await loadSheets()
    } catch (reason) {
      setError(adminErrorMessage(reason))
      throw reason
    } finally { setBusyIssue(null) }
  }

  const saveIssueStatus = async (row: JournalWorkspaceRow, status: IssueStatus) => {
    if (status === row.issueStatus) return
    setBusyIssue(row.issueId); setError(null)
    try {
      await api.updateIssueStatus(row.eventId, row.issueId, row.issueStatus, status)
      setRows((current) => current.map((candidate) => candidate.issueId === row.issueId
        ? { ...candidate, issueStatus: status }
        : candidate))
      await loadSheets()
    } catch (reason) { setError(adminErrorMessage(reason)); throw reason }
    finally { setBusyIssue(null) }
  }

  const resetWork = async (row: JournalWorkspaceRow) => {
    if (!row.workItem) return
    setBusyIssue(row.issueId); setError(null)
    try {
      const response = await api.resetJournalWorkItem(row.issueId, row.workItem.version)
      setRows((current) => current.map((candidate) => candidate.issueId === row.issueId
        ? { ...candidate, workItem: workFromResponse(response), updatedAt: response.updatedAt ?? candidate.updatedAt }
        : candidate))
      await loadSheets()
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusyIssue(null) }
  }

  const syncSheet = async () => {
    if (!selectedSheetData) return
    setSyncing(true); setError(null); setNotice(null)
    try {
      const summary = await api.syncJournalSheet(selectedSheetData.seriesId, selectedSheetData.year)
      setNotice(syncNotice(summary))
      await Promise.all([loadSheets(), loadRows()])
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setSyncing(false) }
  }

  return <div className="admin-stack admin-journal-workspace">
    <div className="admin-page-heading"><div><p className="admin-eyebrow">Поддержка</p><h1>Журнал обращений</h1><p>Рабочая таблица обращений и ожидающих синхронизации исправлений.</p></div><div className="admin-heading-actions"><button className="admin-button-secondary" type="button" onClick={() => void loadRows()}>Обновить</button><button className="admin-button-secondary" type="button" onClick={() => setExportOpen(true)}>Выгрузить XLSX</button></div></div>
    <form className="admin-journal-filters" onSubmit={apply}>
      <Field label="ID обращения"><input type="number" min="1" value={draft.issueId ?? ''} onChange={(change) => setDraft({ ...draft, issueId: change.target.value ? Number(change.target.value) : undefined })} /></Field>
      <Field label="Мероприятие"><select value={draft.eventId ?? ''} onChange={(change) => setDraft({ ...draft, eventId: change.target.value ? Number(change.target.value) : undefined, raceId: undefined })}><option value="">Все мероприятия</option>{events.map((event) => <option key={event.id} value={event.id}>{event.name}</option>)}</select></Field>
      <Field label="Город"><input value={draft.location ?? ''} onChange={(change) => setDraft({ ...draft, location: change.target.value || undefined })} /></Field>
      <Field label="Старт"><select disabled={!draft.eventId} value={draft.raceId ?? ''} onChange={(change) => setDraft({ ...draft, raceId: change.target.value ? Number(change.target.value) : undefined })}><option value="">Все старты</option>{races.map((race) => <option key={race.id} value={race.id}>{race.name}</option>)}</select></Field>
      <Field label="Стартовый номер"><input value={draft.bib ?? ''} onChange={(change) => setDraft({ ...draft, bib: change.target.value || undefined })} /></Field>
      <Field label="Участник"><input value={draft.participant ?? ''} onChange={(change) => setDraft({ ...draft, participant: change.target.value || undefined })} /></Field>
      <Field label="Тип"><select value={draft.issueType ?? ''} onChange={(change) => setDraft({ ...draft, issueType: change.target.value as JournalFilters['issueType'] || undefined })}><option value="">Все</option><option value="MISSING_RESULT">Нет результата</option><option value="RESULT_CORRECTION">Исправление</option></select></Field>
      <Field label="Причина"><select value={draft.correctionReason ?? ''} onChange={(change) => setDraft({ ...draft, correctionReason: change.target.value || undefined })}><option value="">Все</option><option value="OFFICIAL_TIME">Официальное время</option><option value="CHIP_TIME">Чистое время</option><option value="RESULT_STATUS">Статус</option><option value="RACE_OR_FORMAT">Старт</option><option value="OTHER">Другое</option></select></Field>
      <fieldset className="admin-status-filter"><legend>Статус</legend>{(['NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'] as IssueStatus[]).map((status) => <label key={status}><input type="checkbox" checked={draft.status?.includes(status) ?? false} onChange={() => setDraft({ ...draft, status: toggleStatus(draft.status, status) })} />{statusRussian(status)}</label>)}</fieldset>
      <div className="admin-filter-actions"><button className="admin-button-primary">Применить</button><button className="admin-button-secondary" type="button" onClick={resetFilters}>Сбросить</button></div>
    </form>
    {shareBatch && <AdminNotice tone={shareBatch.revokedAt ? 'warning' : 'success'}><strong>Последняя выгрузка:</strong> batch {shareBatch.batchId}. Ссылок: {shareBatch.grantCount}.</AdminNotice>}
    {notice && <AdminNotice tone="success">{notice}</AdminNotice>}
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    <section className="admin-journal-syncbar">
      <div><strong>Несинхронизированных изменений: {selectedSheetData?.pendingSyncCount ?? 0}</strong><small>Последняя синхронизация: {selectedSheetData?.lastSyncedAt ? new Date(selectedSheetData.lastSyncedAt).toLocaleString('ru-RU') : 'ещё не выполнялась'}</small></div>
      <button className="admin-button-primary" type="button" disabled={syncing || !selectedSheetData?.pendingSyncCount} onClick={() => void syncSheet()}>{syncing ? 'Синхронизируем…' : 'Синхронизировать сейчас'}</button>
    </section>
    <Loadable loading={loading} error={null} empty={!visibleRows.length}>
      <div className="admin-table-wrap admin-journal-table-wrap"><table className="admin-table admin-journal-table"><thead><tr><th>№</th><th>Город</th><th>ФИО</th><th>Пол</th><th>Дата рождения</th><th>Bib</th><th>Формат / дистанция</th><th>Текущий Gun</th><th>Корректировка Gun</th><th>Текущий Chip</th><th>Корректировка Chip</th><th>Категория</th><th>Result Status</th><th>Причина</th><th>Статус обращения</th><th>Ответственный</th><th>Внутренний комментарий</th><th>Синхр.</th><th>Изменено</th><th /></tr></thead><tbody>{groups.map((group) => <CityGroup key={group.city} group={group} collapsed={collapsedCities.has(group.city)} toggle={() => setCollapsedCities((current) => toggleSet(current, group.city))} users={users} raceOptionsByEvent={raceOptionsByEvent} busyIssue={busyIssue} onSaveWork={saveWork} onSaveIssueStatus={saveIssueStatus} onReset={resetWork} onOpen={setSelected} />)}</tbody></table></div>
      <AdminPagination page={pageNumber} totalPages={totalPages} onChange={setPageNumber} />
    </Loadable>
    <nav className="admin-journal-sheets" aria-label="Листы журнала">{sheets.map((sheet) => <button key={sheetKey(sheet)} className={selectedSheet === sheetKey(sheet) ? 'active' : ''} type="button" onClick={() => { setSelectedSheet(sheetKey(sheet)); setPageNumber(0) }}>{sheet.seriesName} {sheet.year}<small>{sheet.issueCount} · ожидают {sheet.pendingSyncCount}</small></button>)}</nav>
    {selected && <JournalDrawer api={api} issueId={selected} onClose={() => setSelected(null)} />}
    {exportOpen && <ExportDialog api={api} filters={filters} total={filteredRows.length} onClose={() => setExportOpen(false)} onBatch={setShareBatch} />}
  </div>
}

function CityGroup({ group, collapsed, toggle, users, raceOptionsByEvent, busyIssue, onSaveWork, onSaveIssueStatus, onReset, onOpen }: {
  group: CityGroupData
  collapsed: boolean
  toggle: () => void
  users: AdminUser[]
  raceOptionsByEvent: Record<number, Race[]>
  busyIssue: number | null
  onSaveWork: (row: JournalWorkspaceRow, patch: Partial<WorkDraft>) => Promise<void>
  onSaveIssueStatus: (row: JournalWorkspaceRow, status: IssueStatus) => Promise<void>
  onReset: (row: JournalWorkspaceRow) => Promise<void>
  onOpen: (issueId: number) => void
}) {
  return <><tr className="admin-journal-city-row"><td colSpan={20}><button type="button" onClick={toggle}>{collapsed ? '▶' : '▼'} {group.city} — {group.rows.length} обращений</button></td></tr>{!collapsed && group.rows.map((row) => <tr key={row.issueId} className={row.workItem?.syncStatus === 'PENDING' ? 'admin-journal-pending-row' : undefined}>
    <td className="admin-journal-sticky-number"><button className="admin-row-link" type="button" onClick={() => onOpen(row.issueId)}>#{row.issueId}</button>{row.attachmentCount > 0 && <small>📎 {row.attachmentCount}</small>}</td>
    <td className="admin-journal-sticky-city">{row.location ?? '—'}</td>
    <td className="admin-journal-sticky-name"><TextFactCell label={`ФИО ${row.issueId}`} value={row.workItem?.desiredDisplayName ?? row.currentDisplayName} current={row.currentDisplayName} maxLength={320} required disabled={busyIssue === row.issueId} onSave={(value) => onSaveWork(row, { desiredDisplayName: value })} /></td>
    <td><GenderFactCell value={row.workItem?.desiredGender ?? row.currentGender} current={row.currentGender} disabled={busyIssue === row.issueId} onSave={(value) => onSaveWork(row, { desiredGender: value })} /></td>
    <td><DateFactCell value={row.workItem?.desiredBirthDate ?? row.currentBirthDate} current={row.currentBirthDate} disabled={busyIssue === row.issueId} onSave={(value) => onSaveWork(row, { desiredBirthDate: value })} /></td>
    <td><TextFactCell label={`Bib ${row.issueId}`} value={row.workItem?.desiredBib ?? row.currentBib} current={row.currentBib} maxLength={64} disabled={busyIssue === row.issueId} onSave={(value) => onSaveWork(row, { desiredBib: value || null })} /></td>
    <td><RaceFactCell value={row.workItem?.desiredRaceId ?? row.currentRaceId} current={row.currentRaceId} options={raceOptionsByEvent[row.eventId] ?? []} disabled={busyIssue === row.issueId} onSave={(value) => onSaveWork(row, { desiredRaceId: value })} /></td>
    <td className="admin-current-fact">{formatResultTime(row.currentGunTimeMs) || '—'}</td>
    <td>{row.currentResultId ? <TimeCorrectionCell value={row.workItem?.desiredGunTimeMs ?? row.currentGunTimeMs} current={row.currentGunTimeMs} disabled={busyIssue === row.issueId} onSave={(value) => onSaveWork(row, { desiredGunTimeMs: value })} /> : <MissingResult />}</td>
    <td className="admin-current-fact">{formatResultTime(row.currentChipTimeMs) || '—'}</td>
    <td>{row.currentResultId ? <TimeCorrectionCell value={row.workItem?.desiredChipTimeMs ?? row.currentChipTimeMs} current={row.currentChipTimeMs} disabled={busyIssue === row.issueId} onSave={(value) => onSaveWork(row, { desiredChipTimeMs: value })} /> : <MissingResult />}</td>
    <td>{row.currentCategoryName ?? '—'}</td>
    <td>{row.currentResultId ? <ResultStatusCell value={row.workItem?.desiredResultStatus ?? row.currentResultStatus ?? ''} current={row.currentResultStatus} disabled={busyIssue === row.issueId} onSave={(value) => onSaveWork(row, { desiredResultStatus: value })} /> : <MissingResult />}</td>
    <td>{statusRussian(row.correctionReason)}</td>
    <td><select aria-label={`Статус обращения ${row.issueId}`} disabled={busyIssue === row.issueId} value={row.issueStatus} onChange={(change) => { void onSaveIssueStatus(row, change.target.value as IssueStatus).catch(() => undefined) }}>{(['NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'] as IssueStatus[]).map((status) => <option key={status} value={status}>{statusRussian(status)}</option>)}</select></td>
    <td><select aria-label={`Ответственный ${row.issueId}`} disabled={busyIssue === row.issueId} value={row.workItem?.assigneeUserId ?? ''} onChange={(change) => { void onSaveWork(row, { assigneeUserId: change.target.value ? Number(change.target.value) : null }).catch(() => undefined) }}><option value="">Не назначен</option>{users.map((user) => <option key={user.id} value={user.id}>{user.displayName}</option>)}</select></td>
    <td><CommentCell value={row.workItem?.internalComment ?? ''} disabled={busyIssue === row.issueId} onSave={(value) => onSaveWork(row, { internalComment: value || null })} /></td>
    <td><SyncState work={row.workItem} />{row.workItem && ['PENDING', 'CONFLICT', 'FAILED'].includes(row.workItem.syncStatus) && <button className="admin-link-button" type="button" disabled={busyIssue === row.issueId} onClick={() => void onReset(row)}>Сбросить изменения</button>}</td>
    <td>{new Date(row.workItem?.updatedAt ?? row.updatedAt).toLocaleString('ru-RU')}<small>{row.workItem?.updatedBy ?? ''}</small></td>
    <td><button className="admin-row-link" type="button" onClick={() => onOpen(row.issueId)}>Открыть</button></td>
  </tr>)}</>
}

function TimeCorrectionCell({ value, current, disabled, onSave }: { value: number | null; current: number | null; disabled: boolean; onSave: (value: number | null) => Promise<void> }) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(() => value === current ? '' : formatResultTime(value))
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  useEffect(() => { if (!editing) setDraft(value === current ? '' : formatResultTime(value)) }, [current, editing, value])
  useEffect(() => { if (editing) { inputRef.current?.focus(); inputRef.current?.select() } }, [editing])
  const start = () => { if (!disabled) { setDraft(value === current ? '' : formatResultTime(value)); setError(null); setEditing(true) } }
  const save = async () => {
    const parsed = parseResultTime(draft)
    if (!parsed.valid) { setError(parsed.error); return }
    try { await onSave(parsed.milliseconds); setEditing(false); setError(null) } catch { /* row notice is shown */ }
  }
  const keyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') { event.preventDefault(); void save() }
    if (event.key === 'Escape') { event.preventDefault(); setEditing(false); setError(null); setDraft(value === current ? '' : formatResultTime(value)) }
  }
  if (editing) return <div className="admin-inline-editor"><input ref={inputRef} value={draft} aria-invalid={Boolean(error)} onChange={(change) => { setDraft(change.target.value); setError(null) }} onKeyDown={keyDown} />{error && <small className="admin-cell-error">{error}</small>}</div>
  const pending = value !== current
  return <button className="admin-editable-cell" type="button" disabled={disabled} onDoubleClick={start} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); start() } }}><strong>{pending ? (formatResultTime(value) || 'очистить') : '—'}</strong>{pending && <small>● ожидает синхронизации</small>}</button>
}

function TextFactCell({ label, value, current, maxLength, required = false, disabled, onSave }: { label: string; value: string | null; current: string | null; maxLength: number; required?: boolean; disabled: boolean; onSave: (value: string) => Promise<void> }) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(value ?? '')
  const [error, setError] = useState<string | null>(null)
  useEffect(() => { if (!editing) setDraft(value ?? '') }, [editing, value])
  const save = async () => {
    const normalized = draft.trim()
    if (required && !normalized) { setError('Значение обязательно'); return }
    if (normalized.length > maxLength) { setError(`Максимум ${maxLength} символов`); return }
    try { await onSave(normalized); setEditing(false); setError(null) } catch { /* row notice is shown */ }
  }
  if (editing) return <div className="admin-inline-editor"><input autoFocus aria-label={label} maxLength={maxLength} value={draft} onChange={(event) => { setDraft(event.target.value); setError(null) }} onBlur={() => void save()} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void save() } else if (event.key === 'Escape') { setEditing(false); setDraft(value ?? ''); setError(null) } }} />{error && <small className="admin-cell-error">{error}</small>}</div>
  const pending = value !== current
  return <button className="admin-editable-cell" type="button" disabled={disabled} onDoubleClick={() => setEditing(true)} onKeyDown={(event) => { if (event.key === 'Enter') setEditing(true) }}><strong>{value || '—'}</strong>{pending && <small>текущее: {current || '—'}</small>}</button>
}

function GenderFactCell({ value, current, disabled, onSave }: { value: string | null; current: string | null; disabled: boolean; onSave: (value: string | null) => Promise<void> }) {
  return <div className="admin-fact-select"><select aria-label="Пол" disabled={disabled} value={value ?? ''} onChange={(event) => void onSave(event.target.value || null).catch(() => undefined)}><option value="">Не указан</option><option value="male">Мужской</option><option value="female">Женский</option></select>{value !== current && <small>текущее: {genderLabel(current)}</small>}</div>
}

function DateFactCell({ value, current, disabled, onSave }: { value: string | null; current: string | null; disabled: boolean; onSave: (value: string | null) => Promise<void> }) {
  const [draft, setDraft] = useState(value ?? '')
  const [error, setError] = useState<string | null>(null)
  useEffect(() => setDraft(value ?? ''), [value])
  const save = async () => {
    if (draft && (!/^\d{4}-\d{2}-\d{2}$/.test(draft) || Number.isNaN(Date.parse(`${draft}T00:00:00Z`)))) { setError('Некорректная дата'); return }
    if (draft && draft > new Date().toISOString().slice(0, 10)) { setError('Дата не может быть в будущем'); return }
    try { await onSave(draft || null); setError(null) } catch { /* row notice is shown */ }
  }
  return <div className="admin-inline-editor admin-fact-select"><input aria-label="Дата рождения" type="date" disabled={disabled} value={draft} onChange={(event) => { setDraft(event.target.value); setError(null) }} onBlur={() => void save()} />{value !== current && <small>текущее: {formatBirthDate(current)}</small>}{error && <small className="admin-cell-error">{error}</small>}</div>
}

function RaceFactCell({ value, current, options, disabled, onSave }: { value: number; current: number; options: Race[]; disabled: boolean; onSave: (value: number) => Promise<void> }) {
  return <div className="admin-fact-select"><select aria-label="Формат / дистанция" disabled={disabled || !options.length} value={value} onChange={(event) => void onSave(Number(event.target.value)).catch(() => undefined)}>{options.map((race) => <option key={race.id} value={race.id}>{raceLabel(race)}</option>)}</select>{value !== current && <small>текущее: {raceLabel(options.find((race) => race.id === current))}</small>}</div>
}

function ResultStatusCell({ value, current, disabled, onSave }: { value: string; current: string | null; disabled: boolean; onSave: (value: string) => Promise<void> }) {
  const [editing, setEditing] = useState(false)
  if (editing) return <select autoFocus value={value} disabled={disabled} onBlur={() => setEditing(false)} onChange={(change) => { void onSave(change.target.value).then(() => setEditing(false)).catch(() => undefined) }}>{RESULT_STATUSES.map((status) => <option key={status} value={status}>{statusRussian(status)}</option>)}</select>
  return <button className="admin-editable-cell" type="button" disabled={disabled} onDoubleClick={() => setEditing(true)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); setEditing(true) } }}><strong>{statusRussian(value)}</strong>{value !== current && <small>Было: {statusRussian(current)}</small>}</button>
}

function CommentCell({ value, disabled, onSave }: { value: string; disabled: boolean; onSave: (value: string) => Promise<void> }) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(value)
  const inputRef = useRef<HTMLInputElement>(null)
  useEffect(() => { if (!editing) setDraft(value) }, [editing, value])
  useEffect(() => { if (editing) { inputRef.current?.focus(); inputRef.current?.select() } }, [editing])
  const save = async () => { try { await onSave(draft.trim()); setEditing(false) } catch { /* row notice is shown */ } }
  if (editing) return <input ref={inputRef} maxLength={4000} value={draft} onChange={(change) => setDraft(change.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void save() } else if (event.key === 'Escape') { setEditing(false); setDraft(value) } }} />
  return <button className="admin-editable-cell admin-comment-cell" type="button" disabled={disabled} onDoubleClick={() => setEditing(true)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); setEditing(true) } }}>{value || 'Добавить комментарий'}</button>
}

function SyncState({ work }: { work: JournalWorkItem | null }) {
  if (!work) return <span className="admin-sync-state">—</span>
  const labels = { SYNCED: '✓ Синхронизировано', PENDING: '● Ожидает', CONFLICT: '⚠ Конфликт', FAILED: '✕ Ошибка' }
  return <span className={`admin-sync-state sync-${work.syncStatus.toLowerCase()}`}>{labels[work.syncStatus]}{work.syncErrorCode && <small>{work.syncErrorCode}</small>}</span>
}

function MissingResult() { return <span className="admin-missing-result">Текущий результат отсутствует.</span> }

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
  const openAttachment = async (attachmentId: number) => { if (!detail) return; try { await authorizeAndOpenAttachment(() => api.authorizeAttachment(detail.eventId, detail.issueId, attachmentId)) } catch (reason) { setError(adminErrorMessage(reason)) } }
  useEffect(() => { api.journalDetail(issueId).then(setDetail).catch((reason) => setError(adminErrorMessage(reason))).finally(() => setLoading(false)) }, [api, issueId])
  return <Drawer title={`Журнал · обращение №${issueId}`} onClose={onClose}><Loadable loading={loading} error={error} empty={!detail}>{detail && <div className="admin-stack"><div className="admin-inline-badges"><StatusBadge value={detail.status} />{detail.queueArchivedAt && <span className="admin-badge admin-badge-neutral">Архив</span>}</div><section className="admin-detail-section"><h3>Обращение</h3><dl className="admin-detail-list"><div><dt>Контакт</dt><dd>{detail.contactEmail}</dd></div><div><dt>Сообщение</dt><dd>{detail.message}</dd></div><div><dt>Заявлено</dt><dd>Официальное время {formatDuration(detail.claimedGunTimeMs)} · Чистое время {formatDuration(detail.claimedChipTimeMs)}</dd></div></dl></section><section className="admin-detail-section historical"><h3>Состояние на момент обращения</h3><dl className="admin-detail-list"><div><dt>Мероприятие</dt><dd>{detail.historicalSnapshot.eventName}</dd></div><div><dt>Старт на момент обращения</dt><dd>{historicalStartName(detail.historicalSnapshot.sportFormatName, detail.historicalSnapshot.raceName)}</dd></div><div><dt>Стартовый номер / участник</dt><dd>{detail.historicalSnapshot.bib ?? '—'} · {detail.historicalSnapshot.displayName}</dd></div><div><dt>Факт результата</dt><dd>{statusRussian(detail.historicalSnapshot.observedResultStatus)} · Официальное время {formatDuration(detail.historicalSnapshot.observedGunTimeMs)} · Чистое время {formatDuration(detail.historicalSnapshot.observedChipTimeMs)}</dd></div></dl></section><section className="admin-detail-section current"><h3>Текущее состояние</h3>{detail.currentContext.registrationExists ? <dl className="admin-detail-list"><div><dt>Участник</dt><dd>#{detail.currentContext.registrationId}{detail.currentContext.registrationRetired ? ' · удалён из актуального набора' : ''}</dd></div><div><dt>Старт</dt><dd>{detail.currentContext.race?.name ?? '—'}</dd></div><div><dt>Категория</dt><dd>{detail.currentContext.category?.name ?? '—'}</dd></div><div><dt>Результат</dt><dd>{detail.currentContext.result ? `${statusRussian(detail.currentContext.result.status)} · Официальное время ${formatDuration(detail.currentContext.result.gunTimeMs)} · Чистое время ${formatDuration(detail.currentContext.result.chipTimeMs)}` : 'Нет'}</dd></div></dl> : <AdminNotice tone="warning">Текущий участник отсутствует.</AdminNotice>}</section><section className="admin-detail-section"><h3>История</h3><ol className="admin-history">{detail.history.map((item) => <li key={item.historyId}><StatusBadge value={item.action} /><div><strong>{item.fromStatus ? `${statusRussian(item.fromStatus)} → ${statusRussian(item.toStatus)}` : statusRussian(item.toStatus)}</strong><small>{new Date(item.createdAt).toLocaleString('ru-RU')} · {item.actor ?? 'система'} {item.reason ? `· ${item.reason}` : ''}</small></div></li>)}</ol></section><section className="admin-detail-section"><h3>Вложения ({detail.attachmentCount})</h3><ul className="admin-attachment-list">{detail.attachments.map((attachment) => <li key={attachment.attachmentId}><div><strong>{attachment.originalFileName}</strong><small>{bytesLabel(attachment.sizeBytes)} · {statusRussian(attachment.scanStatus)}</small></div>{attachment.uploadStatus === 'UPLOADED' && attachment.scanStatus === 'CLEAN' && <button className="admin-link-button" onClick={() => void openAttachment(attachment.attachmentId)}>Открыть</button>}</li>)}</ul></section></div>}</Loadable></Drawer>
}

interface WorkDraft {
  desiredResultStatus: string | null
  desiredGunTimeMs: number | null
  desiredChipTimeMs: number | null
  desiredDisplayName: string
  desiredGender: string | null
  desiredBirthDate: string | null
  desiredBib: string | null
  desiredRaceId: number
  assigneeUserId: number | null
  internalComment: string | null
}

interface CityGroupData { city: string; rows: JournalWorkspaceRow[] }

function workDraft(row: JournalWorkspaceRow): WorkDraft {
  return {
    desiredResultStatus: row.workItem?.desiredResultStatus ?? row.currentResultStatus,
    desiredGunTimeMs: row.workItem?.desiredGunTimeMs ?? row.currentGunTimeMs,
    desiredChipTimeMs: row.workItem?.desiredChipTimeMs ?? row.currentChipTimeMs,
    desiredDisplayName: row.workItem?.desiredDisplayName ?? row.currentDisplayName,
    desiredGender: row.workItem?.desiredGender ?? row.currentGender,
    desiredBirthDate: row.workItem?.desiredBirthDate ?? row.currentBirthDate,
    desiredBib: row.workItem?.desiredBib ?? row.currentBib,
    desiredRaceId: row.workItem?.desiredRaceId ?? row.currentRaceId,
    assigneeUserId: row.workItem?.assigneeUserId ?? null,
    internalComment: row.workItem?.internalComment ?? null,
  }
}

function workFromResponse(response: JournalWorkItemResponse): JournalWorkItem {
  return { ...response }
}

function cityGroups(rows: JournalWorkspaceRow[]): CityGroupData[] {
  return groupJournalRowsByCity(rows)
}

function isActiveRow(row: JournalWorkspaceRow): boolean {
  return ['NEW', 'IN_PROGRESS'].includes(row.issueStatus) || row.workItem?.syncStatus === 'PENDING'
}

function matchesFilters(row: JournalWorkspaceRow, filters: JournalFilters): boolean {
  const contains = (value: string | null, query: string | undefined) => !query || (value ?? '').toLocaleLowerCase('ru-RU').includes(query.toLocaleLowerCase('ru-RU'))
  return (!filters.issueId || row.issueId === filters.issueId)
    && (!filters.eventId || row.eventId === filters.eventId)
    && (!filters.raceId || row.currentRaceId === filters.raceId)
    && contains(row.location, filters.location)
    && contains(row.workItem?.desiredBib ?? row.currentBib, filters.bib)
    && contains(row.workItem?.desiredDisplayName ?? row.currentDisplayName, filters.participant)
    && (!filters.issueType || row.issueType === filters.issueType)
    && (!filters.correctionReason || row.correctionReason === filters.correctionReason)
    && (!filters.status?.length || filters.status.includes(row.issueStatus))
}

function sheetKey(sheet: JournalWorkspaceSheet): string { return `${sheet.seriesId}:${sheet.year}` }

function toggleSet(current: Set<string>, value: string): Set<string> {
  const next = new Set(current)
  if (next.has(value)) next.delete(value); else next.add(value)
  return next
}

function syncNotice(summary: JournalSyncSummary): string {
  return `Синхронизация завершена. Применено: ${summary.applied}. Конфликтов: ${summary.conflicts}. Ошибок: ${summary.errors}.`
}

function toggleStatus(current: IssueStatus[] | undefined, value: IssueStatus): IssueStatus[] | undefined {
  const next = current?.includes(value) ? current.filter((item) => item !== value) : [...(current ?? []), value]
  return next.length ? next : undefined
}

function filtersFromUrl(): JournalFilters {
  const query = new URLSearchParams(window.location.search)
  const number = (name: string) => query.get(name) ? Number(query.get(name)) : undefined
  const statuses = query.getAll('status').filter((value): value is IssueStatus => ['NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'].includes(value))
  return { issueId: number('issueId'), eventId: number('eventId'), location: query.get('location') || undefined, raceId: number('raceId'), bib: query.get('bib') || undefined, participant: query.get('participant') || undefined, issueType: (query.get('issueType') as JournalFilters['issueType']) || undefined, correctionReason: query.get('correctionReason') || undefined, status: statuses.length ? statuses : undefined, queueScope: 'ALL', page: 0, size: PAGE_SIZE }
}

function historicalStartName(formatName: string | null, raceName: string): string {
  return formatName ? `${formatName} · ${raceName}` : raceName
}

function genderLabel(value: string | null): string {
  if (value === 'male') return 'Мужской'
  if (value === 'female') return 'Женский'
  return 'Не указан'
}

function formatBirthDate(value: string | null): string {
  if (!value) return '—'
  const [year, month, day] = value.split('-')
  return `${day}.${month}.${year}`
}

function raceLabel(race: Race | undefined): string {
  if (!race) return '—'
  if (race.distanceMeters === null) return race.name
  const kilometres = race.distanceMeters / 1000
  return `${race.name} · ${Number.isInteger(kilometres) ? kilometres : kilometres.toFixed(1)} км`
}

function writeFiltersToUrl(filters: JournalFilters): void {
  const query = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => { if (value === undefined || value === null || value === '') return; if (Array.isArray(value)) value.forEach((item) => query.append(key, String(item))); else query.set(key, String(value)) })
  window.history.replaceState({}, '', `/admin/support/issues?${query.toString()}`)
}
