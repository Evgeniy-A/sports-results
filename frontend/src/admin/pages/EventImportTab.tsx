import { useMemo, useState } from 'react'
import type { AdminApi } from '../api'
import type { EventSummary, ImportApplyResult, ImportMode, ImportPreview, Race } from '../types'
import { adminErrorMessage } from '../utils'
import { AdminNotice, ConfirmDialog, Field, StatusBadge } from '../components/AdminUi'

const MODES: Array<{ value: ImportMode; label: string; description: string }> = [
  { value: 'ADD_NEW', label: 'Добавить новые', description: 'Создаёт только новых участников и результаты. Существующие строки не изменяются.' },
  { value: 'UPDATE_EXISTING', label: 'Обновить существующие', description: 'Изменяет только найденных текущих участников и результаты. Новые строки не добавляются.' },
  { value: 'EMERGENCY_REPLACE', label: 'Экстренно заменить данные', description: 'Логически заменяет текущий набор выбранных стартов, сохраняя старые данные в истории.' },
]

export function EventImportTab({ api, event, races }: { api: AdminApi; event: EventSummary; races: Race[] }) {
  const [mode, setMode] = useState<ImportMode>('ADD_NEW')
  const [raceIds, setRaceIds] = useState<number[]>([])
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [applied, setApplied] = useState<ImportApplyResult | null>(null)
  const [busy, setBusy] = useState<'preview' | 'apply' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [confirmation, setConfirmation] = useState('')
  const selectedRaces = useMemo(() => races.filter((race) => raceIds.includes(race.id)), [raceIds, races])
  const emergencyBlocked = mode === 'EMERGENCY_REPLACE' && selectedRaces.some((race) => race.resultsPublicationStatus !== 'DRAFT')

  const resetPreview = () => { setPreview(null); setApplied(null); setError(null) }
  const chooseMode = (value: ImportMode) => { setMode(value); setRaceIds([]); setFile(null); resetPreview() }
  const toggleRace = (raceId: number) => { setRaceIds((current) => current.includes(raceId) ? current.filter((id) => id !== raceId) : [...current, raceId]); resetPreview() }

  const runPreview = async () => {
    if (!file || !raceIds.length) return
    setBusy('preview'); setError(null); setApplied(null)
    try { setPreview(await api.importPreview(event.id, mode, raceIds, file)) }
    catch (reason) { setPreview(null); setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }
  const requestApply = () => {
    if (!preview || !file || preview.blockingErrorsPresent) return
    if (mode === 'EMERGENCY_REPLACE') { setConfirmation(''); setConfirmOpen(true) }
    else void runApply()
  }
  const runApply = async () => {
    if (!preview || !file) return
    setBusy('apply'); setError(null)
    try { const result = await api.importApply(event.id, preview.operationId, file); setApplied(result); setPreview(null); setConfirmOpen(false) }
    catch (reason) { setError(adminErrorMessage(reason)); setConfirmOpen(false) }
    finally { setBusy(null) }
  }

  return <div className="admin-stack">
    <div className="admin-section-toolbar"><div><h2>Загрузка результатов</h2><p>Предварительная проверка никогда не изменяет данные. Применение запускается только отдельным действием.</p></div></div>
    <div className="admin-mode-selector" role="radiogroup" aria-label="Режим загрузки">{MODES.map((item) => <button key={item.value} type="button" role="radio" aria-checked={mode === item.value} className={item.value === 'EMERGENCY_REPLACE' ? 'danger-mode' : ''} onClick={() => chooseMode(item.value)}><strong>{item.label}</strong><span>{item.description}</span></button>)}</div>
    {mode === 'EMERGENCY_REPLACE' && <AdminNotice tone="danger"><strong>Опасная операция.</strong> Текущий набор выбранных стартов будет логически заменён новым. Старые данные сохранятся в истории, но перестанут быть текущими. Active обращения будут архивированы согласно backend plan.</AdminNotice>}
    <section className="admin-card"><div className="admin-card-heading"><div><h3>1. Старты и CSV</h3><p>Явно выберите один или несколько стартов.</p></div></div>
      <fieldset className="admin-checkbox-grid"><legend>Старты</legend>{races.map((race) => <label key={race.id}><input type="checkbox" checked={raceIds.includes(race.id)} onChange={() => toggleRace(race.id)} /><span><strong>{race.name}</strong><small><StatusBadge value={race.resultsPublicationStatus} /></small></span></label>)}</fieldset>
      {!races.length && <div className="admin-empty compact">Нет стартов для загрузки результатов.</div>}
      <Field label="CSV-файл" hint="Колонки определяются backend по header; исходный файл не изменяется."><input type="file" accept=".csv,text/csv" onChange={(change) => { setFile(change.target.files?.[0] ?? null); resetPreview() }} /></Field>
      {file && <p className="admin-file-line"><strong>{file.name}</strong> · {(file.size / 1024).toFixed(1)} КБ</p>}
      {emergencyBlocked && <AdminNotice tone="warning">Экстренная замена доступна только для стартов в черновике. Верните выбранные опубликованные старты в черновик вручную.</AdminNotice>}
      {error && <AdminNotice tone="danger">{error}</AdminNotice>}
      <button className="admin-button-primary" type="button" disabled={!file || !raceIds.length || emergencyBlocked || busy !== null} onClick={() => void runPreview()}>{busy === 'preview' ? 'Строим Preview…' : 'Построить Preview'}</button>
    </section>

    {preview && <section className="admin-card"><div className="admin-card-heading"><div><h3>2. Preview</h3><p>{file?.name} · SHA-256 {preview.fileSha256}</p></div><StatusBadge value={preview.blockingErrorsPresent ? 'BLOCKING' : preview.operationStatus} /></div>
      <div className="admin-stat-grid"><Stat label="Строк" value={preview.totals.totalRows} /><Stat label="NEW" value={preview.totals.newCount} /><Stat label="UNCHANGED" value={preview.totals.unchangedCount} /><Stat label="CHANGED" value={preview.totals.changedCount} /><Stat label="BLOCKED" value={preview.modeSummary.blocked} /><Stat label="OUT OF SCOPE" value={preview.totals.outOfScopeCount} /><Stat label="DUPLICATE" value={preview.totals.duplicateCount} /><Stat label="INVALID" value={preview.totals.invalidCount} /><Stat label="CONFLICT" value={preview.totals.conflictCount} /><Stat label="AMBIGUOUS" value={preview.totals.ambiguousCount} /></div>
      {preview.emergencySummary && <div className="admin-danger-summary"><h4>План экстренной замены</h4><div className="admin-stat-grid"><Stat label="Текущих" value={preview.emergencySummary.totals.currentCount} /><Stat label="Будет retired" value={preview.emergencySummary.totals.retireCount} /><Stat label="Будет вставлено" value={preview.emergencySummary.totals.insertCount} /><Stat label="Обращений в архив" value={preview.emergencySummary.totals.activeIssuesArchiveCount} /></div></div>}
      {preview.diagnostics.length > 0 && <><h4>Диагностика</h4><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Строка</th><th>Поле</th><th>Код</th><th>Описание</th></tr></thead><tbody>{preview.diagnostics.map((item, index) => <tr key={`${item.sourceRowNumber}-${item.code}-${index}`}><td>{item.sourceRowNumber ?? 'Файл'}</td><td>{item.field ?? '—'}</td><td><code>{item.code}</code></td><td>{item.message}</td></tr>)}</tbody></table></div>{preview.diagnosticsTruncated && <p className="admin-muted">Показана только часть диагностики.</p>}</>}
      <h4>Решения по строкам</h4><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Строка</th><th>Bib</th><th>Участник</th><th>Старт</th><th>Решение</th><th>Причина</th><th>Изменения</th></tr></thead><tbody>{preview.rows.map((row) => <tr key={row.sourceRowNumber}><td>{row.sourceRowNumber}</td><td>{row.bib ?? '—'}</td><td>{row.participantName ?? '—'}</td><td>{row.targetRace?.raceName ?? '—'}</td><td><StatusBadge value={row.decision} /></td><td>{row.reason}</td><td>{row.diffs.length ? row.diffs.map((diff) => <div className="admin-diff" key={diff.field}><strong>{diff.field}</strong><del>{diff.oldValue ?? '∅'}</del><ins>{diff.newValue ?? '∅'}</ins></div>) : '—'}</td></tr>)}</tbody></table></div>
      {preview.rowsTruncated && <p className="admin-muted">Диагностика ограничена 200 строками; Apply использует полный backend plan.</p>}
      {preview.blockingErrorsPresent ? <AdminNotice tone="danger">Apply недоступен: устраните блокирующие ошибки и выполните Preview заново.</AdminNotice> : <div className="admin-form-actions"><button className={mode === 'EMERGENCY_REPLACE' ? 'admin-button-danger' : 'admin-button-primary'} type="button" disabled={busy !== null} onClick={requestApply}>{busy === 'apply' ? 'Применяем…' : mode === 'EMERGENCY_REPLACE' ? 'Применить экстренную замену' : 'Применить импорт'}</button><span>Preview действует до {new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(preview.expiresAt))}</span></div>}
    </section>}

    {applied && <AdminNotice tone="success"><strong>Импорт применён.</strong> Добавлено: {applied.insertedCount}; обновлено: {applied.updatedCount}; retired: {applied.retiredCount}; обращений архивировано: {applied.archivedIssueCount}. ImportBatch #{applied.importBatchId}.</AdminNotice>}
    {confirmOpen && <ConfirmDialog title="Экстренно заменить данные?" description="Текущий набор данных выбранных стартов будет логически заменён новым. Старые данные сохранятся в истории, но перестанут быть текущими." confirmLabel="Заменить данные" danger busy={busy === 'apply'} onConfirm={() => { if (confirmation === 'ЗАМЕНИТЬ') void runApply() }} onClose={() => setConfirmOpen(false)}><Field label="Введите ЗАМЕНИТЬ для подтверждения"><input autoComplete="off" value={confirmation} onChange={(change) => setConfirmation(change.target.value)} /></Field>{confirmation && confirmation !== 'ЗАМЕНИТЬ' && <p className="admin-field-error">Введите слово без изменений.</p>}</ConfirmDialog>}
  </div>
}

function Stat({ label, value }: { label: string; value: number }) {
  return <div><span>{label}</span><strong>{value}</strong></div>
}
