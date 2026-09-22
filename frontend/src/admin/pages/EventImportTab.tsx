import { useMemo, useState } from 'react'
import type { AdminApi } from '../api'
import type {
  CanonicalImportField,
  EventSummary,
  ImportApplyResult,
  ImportFileAnalysis,
  ImportInputOptions,
  ImportMode,
  ImportPreview,
  Race,
} from '../types'
import { adminErrorMessage } from '../utils'
import { AdminNotice, ConfirmDialog, Field, StatusBadge } from '../components/AdminUi'

const MODES: Array<{ value: ImportMode; label: string; description: string }> = [
  { value: 'ADD_NEW', label: 'Добавить новые', description: 'Создаёт только новых участников и результаты. Существующие строки не изменяются.' },
  { value: 'UPDATE_EXISTING', label: 'Обновить существующие', description: 'Изменяет только найденных текущих участников и результаты. Новые строки не добавляются.' },
  { value: 'EMERGENCY_REPLACE', label: 'Экстренно заменить данные', description: 'Логически заменяет текущий набор выбранных стартов, сохраняя старые данные в истории.' },
]

type SourceKind = 'TEMPLATE' | 'EXTERNAL'
type ExternalScope = 'SINGLE' | 'MULTI'

export function EventImportTab({ api, event, races }: { api: AdminApi; event: EventSummary; races: Race[] }) {
  const [mode, setMode] = useState<ImportMode>('ADD_NEW')
  const [sourceKind, setSourceKind] = useState<SourceKind>('TEMPLATE')
  const [externalScope, setExternalScope] = useState<ExternalScope>('SINGLE')
  const [targetRaceId, setTargetRaceId] = useState<number | null>(races.length === 1 ? races[0].id : null)
  const [file, setFile] = useState<File | null>(null)
  const [analysis, setAnalysis] = useState<ImportFileAnalysis | null>(null)
  const [columnMappings, setColumnMappings] = useState<Record<string, CanonicalImportField>>({})
  const [raceMappings, setRaceMappings] = useState<Record<string, number>>({})
  const [saveProfile, setSaveProfile] = useState(false)
  const [profileName, setProfileName] = useState('')
  const [saveRaceMappings, setSaveRaceMappings] = useState(false)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [applied, setApplied] = useState<ImportApplyResult | null>(null)
  const [busy, setBusy] = useState<'download' | 'analyze' | 'preview' | 'apply' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [confirmation, setConfirmation] = useState('')
  const [previewUpdatedAt, setPreviewUpdatedAt] = useState<Date | null>(null)

  const fieldLabels = useMemo(() => Object.fromEntries(
    (analysis?.canonicalFields ?? []).map((field) => [field.field, field.displayName]),
  ) as Partial<Record<CanonicalImportField, string>>, [analysis])
  const mappingProblems = useMemo(() => {
    if (!analysis || analysis.legacyCsv || analysis.sportsResultsTemplate) return []
    const problems: string[] = []
    const values = Object.values(columnMappings)
    const duplicate = values.find((field, index) => values.indexOf(field) !== index)
    if (duplicate) problems.push(`Поле «${fieldLabels[duplicate] ?? duplicate}» выбрано для нескольких колонок.`)
    for (const required of analysis.canonicalFields.filter((field) => field.required)) {
      if (!values.includes(required.field)) problems.push(`Не удалось определить обязательное поле: ${required.displayName}.`)
    }
    if (sourceKind === 'EXTERNAL' && externalScope === 'MULTI') {
      if (!values.includes('RACE')) problems.push('Сопоставьте колонку, которая определяет Старт.')
      const unresolved = analysis.raceValues.filter((value) => !raceMappings[value.sourceValue])
      if (unresolved.length === 1) {
        problems.push(`Остался несопоставленный старт: ${unresolved[0].sourceValue}.`)
      } else if (unresolved.length > 1) {
        problems.push(`Сопоставьте все ${analysis.raceValues.length} старта из файла, чтобы продолжить.`)
      }
    }
    return problems
  }, [analysis, columnMappings, externalScope, fieldLabels, raceMappings, sourceKind])
  const scopeRaceIds = useMemo(() => {
    if (!analysis) return []
    if (analysis.sportsResultsTemplate || analysis.legacyCsv) return analysis.resolvedRaceIds
    if (externalScope === 'SINGLE') return targetRaceId === null ? [] : [targetRaceId]
    return [...new Set(Object.values(raceMappings))].sort((left, right) => left - right)
  }, [analysis, externalScope, raceMappings, targetRaceId])
  const selectedRaces = useMemo(
    () => races.filter((race) => scopeRaceIds.includes(race.id)),
    [races, scopeRaceIds],
  )
  const emergencyBlocked = mode === 'EMERGENCY_REPLACE'
    && selectedRaces.some((race) => race.resultsPublicationStatus !== 'DRAFT')

  const options = (): ImportInputOptions | undefined => {
    if (!analysis || analysis.legacyCsv) return undefined
    return {
      targetRaceId: sourceKind === 'EXTERNAL' && externalScope === 'SINGLE' ? targetRaceId : null,
      columnMappings,
      raceMappings,
      saveRaceMappings: sourceKind === 'EXTERNAL' && externalScope === 'MULTI' && saveRaceMappings,
    }
  }

  const resetAfterFile = () => {
    setAnalysis(null); setColumnMappings({}); setRaceMappings({}); setPreview(null); setPreviewUpdatedAt(null); setApplied(null)
    setError(null); setSuccess(null); setSaveProfile(false); setProfileName(''); setSaveRaceMappings(false)
  }
  const chooseMode = (value: ImportMode) => { setMode(value); setPreview(null); setPreviewUpdatedAt(null); setApplied(null); setError(null) }
  const chooseSource = (value: SourceKind) => { setSourceKind(value); setFile(null); resetAfterFile() }

  const downloadTemplate = async () => {
    setBusy('download'); setError(null)
    try {
      const blob = await api.importTemplate(event.id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${event.slug || 'event'}-results-template.xlsx`
      document.body.append(link); link.click(); link.remove(); URL.revokeObjectURL(url)
      setSuccess('Шаблон Excel скачан.')
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }

  const analyze = async (reuseMappings = false) => {
    if (!file) return
    const selectedTarget = sourceKind === 'EXTERNAL' && externalScope === 'SINGLE'
      ? targetRaceId ?? undefined
      : undefined
    if (sourceKind === 'EXTERNAL' && externalScope === 'SINGLE' && selectedTarget === undefined) {
      setError('Выберите Старт для одно-стартового файла.'); return
    }
    setBusy('analyze'); setError(null); setSuccess(null); setPreview(null); setApplied(null)
    try {
      const currentOptions: ImportInputOptions | undefined = reuseMappings ? {
        targetRaceId: selectedTarget ?? null,
        columnMappings,
        raceMappings,
        saveRaceMappings: false,
      } : undefined
      const result = await api.analyzeImport(event.id, file, selectedTarget, currentOptions)
      if (sourceKind === 'TEMPLATE' && !result.sportsResultsTemplate) {
        setAnalysis(null)
        setError('Файл не является актуальным шаблоном Sports Results. Выберите «Другой файл хронометражиста» для CSV/XLSX со своей структурой.')
        return
      }
      setAnalysis(result)
      setColumnMappings(result.columnMappings)
      setRaceMappings(Object.fromEntries(
        result.raceValues.filter((value) => value.raceId !== null).map((value) => [value.sourceValue, value.raceId as number]),
      ))
      if (result.suggestedProfile) setSuccess(`Подобран профиль «${result.suggestedProfile.name}». Сопоставление можно изменить.`)
    } catch (reason) { setAnalysis(null); setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }

  const runPreview = async () => {
    if (!file || !analysis || mappingProblems.length || !scopeRaceIds.length) return
    setBusy('preview'); setError(null); setSuccess(null); setApplied(null)
    try {
      if (saveProfile && !analysis.sportsResultsTemplate && !analysis.legacyCsv) {
        if (!profileName.trim()) throw new Error('Введите название формата файла.')
        await api.createImportMappingProfile({
          name: profileName.trim(),
          fileType: analysis.fileType,
          headerSignature: analysis.headerSignature,
          mappings: columnMappings,
          raceDiscriminatorHeader: Object.entries(columnMappings).find(([, field]) => field === 'RACE')?.[0] ?? null,
        })
      }
      setPreview(await api.importPreview(event.id, mode, scopeRaceIds, file, options()))
      setPreviewUpdatedAt(new Date())
      setSuccess('Проверка данных обновлена.')
    } catch (reason) { setPreview(null); setError(adminErrorMessage(reason)) }
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
    try {
      const result = await api.importApply(event.id, preview.operationId, file)
      setApplied(result); setPreview(null); setConfirmOpen(false)
    } catch (reason) { setError(adminErrorMessage(reason)); setConfirmOpen(false) }
    finally { setBusy(null) }
  }

  return <div className="admin-stack">
    <div className="admin-section-toolbar"><div><h2>Загрузка результатов</h2><p>Сначала файл распознаётся и проверяется. Данные изменяются только после отдельного подтверждения.</p></div></div>
    <div className="admin-mode-selector" role="radiogroup" aria-label="Режим загрузки">{MODES.map((item) => <button key={item.value} type="button" role="radio" aria-checked={mode === item.value} className={item.value === 'EMERGENCY_REPLACE' ? 'danger-mode' : ''} onClick={() => chooseMode(item.value)}><strong>{item.label}</strong><span>{item.description}</span></button>)}</div>
    {mode === 'EMERGENCY_REPLACE' && <AdminNotice tone="danger"><strong>Опасная операция.</strong> Текущий набор выбранных стартов будет логически заменён новым. Старые данные и история сохранятся.</AdminNotice>}

    <section className="admin-card admin-import-choice"><div className="admin-card-heading"><div><h3>1. Выберите способ передачи</h3><p>Шаблон Sports Results уже содержит привязку к мероприятию и Стартам.</p></div></div>
      <div className="admin-import-paths">
        <button type="button" className={sourceKind === 'TEMPLATE' ? 'selected' : ''} onClick={() => chooseSource('TEMPLATE')}><strong>Рекомендуемый формат</strong><span>Скачать и затем загрузить заполненный Excel-шаблон.</span></button>
        <button type="button" className={sourceKind === 'EXTERNAL' ? 'selected' : ''} onClick={() => chooseSource('EXTERNAL')}><strong>Другой файл хронометражиста</strong><span>CSV или XLSX с распознаванием и сопоставлением колонок.</span></button>
      </div>
      {sourceKind === 'TEMPLATE' && <div className="admin-import-template-action"><div><strong>Excel-шаблон для «{event.name}»</strong><p>Инструкция и отдельный лист для каждого текущего Старта.</p></div><button className="admin-button-secondary" type="button" disabled={busy !== null || !races.length} onClick={() => void downloadTemplate()}>{busy === 'download' ? 'Готовим файл…' : 'Скачать шаблон Excel'}</button></div>}
      {sourceKind === 'EXTERNAL' && <div className="admin-inline-options"><label><input type="radio" checked={externalScope === 'SINGLE'} onChange={() => { setExternalScope('SINGLE'); resetAfterFile() }} /> Один Старт</label><label><input type="radio" checked={externalScope === 'MULTI'} onChange={() => { setExternalScope('MULTI'); resetAfterFile() }} /> Несколько Стартов в одном файле</label></div>}
    </section>

    <section className="admin-card"><div className="admin-card-heading"><div><h3>2. Выберите файл</h3><p>{sourceKind === 'TEMPLATE' ? 'Загрузите заполненный шаблон Sports Results.' : 'Система распознает CSV/XLSX и предложит уточнения только при необходимости.'}</p></div></div>
      {sourceKind === 'EXTERNAL' && externalScope === 'SINGLE' && <Field label="Старт"><select value={targetRaceId ?? ''} onChange={(change) => { setTargetRaceId(change.target.value ? Number(change.target.value) : null); resetAfterFile() }}><option value="">Выберите Старт</option>{races.map((race) => <option key={race.id} value={race.id}>{race.name}</option>)}</select></Field>}
      <Field label={sourceKind === 'TEMPLATE' ? 'Заполненный Excel-шаблон' : 'CSV или XLSX'} hint="Максимальный размер файла — 25 МиБ."><input type="file" accept=".csv,text/csv,.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={(change) => { setFile(change.target.files?.[0] ?? null); resetAfterFile() }} /></Field>
      {file && <p className="admin-file-line"><strong>{file.name}</strong> · {(file.size / 1024).toFixed(1)} КБ</p>}
      <button className="admin-button-primary" type="button" disabled={!file || busy !== null} onClick={() => void analyze()}>{busy === 'analyze' ? 'Распознаём…' : 'Распознать файл'}</button>
    </section>

    {analysis && <ImportMapping
      analysis={analysis} races={races} sourceKind={sourceKind} externalScope={externalScope}
      mappings={columnMappings} raceMappings={raceMappings} mappingProblems={mappingProblems}
      saveProfile={saveProfile} profileName={profileName}
      saveRaceMappings={saveRaceMappings} busy={busy}
      onMapping={(header, field) => setColumnMappings((current) => {
        const next = { ...current }; if (field) next[header] = field; else delete next[header]; return next
      })}
      onRaceMapping={(source, raceId) => setRaceMappings((current) => ({ ...current, [source]: raceId }))}
      onSaveProfile={setSaveProfile} onProfileName={setProfileName}
      onSaveRaceMappings={setSaveRaceMappings} onRefresh={() => void analyze(true)}
      onValidate={() => void runPreview()}
    />}

    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    {success && <AdminNotice tone="success">{success}</AdminNotice>}
    {emergencyBlocked && <AdminNotice tone="warning">Экстренная замена доступна только для Стартов в черновике.</AdminNotice>}

    {preview && <section className="admin-card"><div className="admin-card-heading"><div><h3>4. Проверка данных</h3><p>{file?.name}{previewUpdatedAt ? ` · обновлено ${previewUpdatedAt.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}` : ''}</p></div><StatusBadge value={preview.blockingErrorsPresent ? 'BLOCKING' : preview.operationStatus} /></div>
      <div className="admin-stat-grid"><Stat label="Строк" value={preview.totals.totalRows} /><Stat label="Новых" value={preview.totals.newCount} /><Stat label="Без изменений" value={preview.totals.unchangedCount} /><Stat label="Изменятся" value={preview.totals.changedCount} /><Stat label="Заблокировано" value={preview.modeSummary.blocked} /><Stat label="Вне выбранных Стартов" value={preview.totals.outOfScopeCount} /><Stat label="Дубли номеров" value={preview.totals.duplicateCount} /><Stat label="Ошибки" value={preview.totals.invalidCount} /></div>
      {mode === 'EMERGENCY_REPLACE' && <div className="admin-scope-summary"><strong>Будут изменены результаты:</strong> {selectedRaces.map((race) => race.name).join(', ') || '—'}{races.some((race) => !scopeRaceIds.includes(race.id)) && <><br /><strong>Не будут затронуты:</strong> {races.filter((race) => !scopeRaceIds.includes(race.id)).map((race) => race.name).join(', ')}</>}</div>}
      {preview.emergencySummary && <div className="admin-danger-summary"><h4>План экстренной замены</h4><div className="admin-stat-grid"><Stat label="Текущих" value={preview.emergencySummary.totals.currentCount} /><Stat label="Будет перенесено в историю" value={preview.emergencySummary.totals.retireCount} /><Stat label="Будет добавлено" value={preview.emergencySummary.totals.insertCount} /><Stat label="Обращений в архив" value={preview.emergencySummary.totals.activeIssuesArchiveCount} /></div></div>}
      {preview.diagnostics.length > 0 && <><h4>Проблемы</h4><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Строка</th><th>Лист / колонка</th><th>Описание</th></tr></thead><tbody>{preview.diagnostics.map((item, index) => <tr key={`${item.sourceRowNumber}-${item.code}-${index}`}><td>{item.sourceRowNumber ?? 'Файл'}</td><td>{item.field ?? '—'}</td><td>{importDiagnostic(item.code, item.message)}</td></tr>)}</tbody></table></div>{preview.diagnosticsTruncated && <p className="admin-muted">Показана только часть диагностики.</p>}</>}
      <h4>Решения по строкам</h4><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Строка</th><th>Номер</th><th>Участник</th><th>Старт</th><th>Решение</th><th>Причина</th><th>Изменения</th></tr></thead><tbody>{preview.rows.map((row) => <tr key={row.sourceRowNumber}><td>{row.sourceRowNumber}</td><td>{row.bib ?? '—'}</td><td>{row.participantName ?? '—'}</td><td>{row.targetRace?.raceName ?? '—'}</td><td><StatusBadge value={row.decision} /></td><td>{importReason(row.reasonCode, row.reason)}</td><td>{row.diffs.length ? row.diffs.map((diff) => <div className="admin-diff" key={diff.field}><strong>{importFieldLabel(diff.field)}</strong>{diff.oldValue && <del>{importDiffValue(diff.field, diff.oldValue)}</del>}<ins>{importDiffValue(diff.field, diff.newValue)}</ins></div>) : '—'}</td></tr>)}</tbody></table></div>
      {preview.rowsTruncated && <p className="admin-muted">Показана часть строк; при применении будут обработаны все проверенные строки.</p>}
      {preview.blockingErrorsPresent ? <AdminNotice tone="danger">Применение недоступно: исправьте блокирующие ошибки и повторите проверку.</AdminNotice> : <div className="admin-form-actions"><button className={mode === 'EMERGENCY_REPLACE' ? 'admin-button-danger' : 'admin-button-primary'} type="button" disabled={busy !== null} onClick={requestApply}>{busy === 'apply' ? 'Применяем…' : mode === 'EMERGENCY_REPLACE' ? 'Применить экстренную замену' : 'Применить импорт'}</button><span>Проверка действует до {new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(preview.expiresAt))}</span></div>}
    </section>}

    {applied && <AdminNotice tone="success"><strong>Импорт применён.</strong> Добавлено: {applied.insertedCount}; обновлено: {applied.updatedCount}; перенесено в историю: {applied.retiredCount}; обращений архивировано: {applied.archivedIssueCount}.</AdminNotice>}
    {confirmOpen && <ConfirmDialog title="Экстренно заменить данные?" description="Текущий набор данных выбранных Стартов будет логически заменён новым." confirmLabel="Заменить данные" danger busy={busy === 'apply'} onConfirm={() => { if (confirmation === 'ЗАМЕНИТЬ') void runApply() }} onClose={() => setConfirmOpen(false)}><Field label="Введите ЗАМЕНИТЬ для подтверждения"><input autoComplete="off" value={confirmation} onChange={(change) => setConfirmation(change.target.value)} /></Field>{confirmation && confirmation !== 'ЗАМЕНИТЬ' && <p className="admin-field-error">Введите слово без изменений.</p>}</ConfirmDialog>}
  </div>
}

function ImportMapping({ analysis, races, sourceKind, externalScope, mappings, raceMappings, mappingProblems, saveProfile, profileName, saveRaceMappings, busy, onMapping, onRaceMapping, onSaveProfile, onProfileName, onSaveRaceMappings, onRefresh, onValidate }: {
  analysis: ImportFileAnalysis
  races: Race[]
  sourceKind: SourceKind
  externalScope: ExternalScope
  mappings: Record<string, CanonicalImportField>
  raceMappings: Record<string, number>
  mappingProblems: string[]
  saveProfile: boolean
  profileName: string
  saveRaceMappings: boolean
  busy: string | null
  onMapping: (header: string, field: CanonicalImportField | null) => void
  onRaceMapping: (source: string, raceId: number) => void
  onSaveProfile: (value: boolean) => void
  onProfileName: (value: string) => void
  onSaveRaceMappings: (value: boolean) => void
  onRefresh: () => void
  onValidate: () => void
}) {
  const needsColumnMapping = !analysis.sportsResultsTemplate && !analysis.legacyCsv
  const raceFieldMapped = Object.values(mappings).includes('RACE')
  return <section className="admin-card"><div className="admin-card-heading"><div><h3>3. Сопоставление</h3><p>{analysis.sportsResultsTemplate ? `Шаблон Sports Results v${analysis.templateFormatVersion}: Event и Старты определены автоматически.` : analysis.legacyCsv ? 'Распознан совместимый CSV Sports Results.' : `Распознан внешний ${analysis.fileType}. Неизвестные лишние колонки не импортируются.`}</p></div><StatusBadge value={analysis.readyForValidation ? 'READY' : 'CHECK'} /></div>
    <div className="admin-stat-grid"><Stat label="Листов" value={analysis.sheets.length} /><Stat label="Строк" value={analysis.sheets.reduce((sum, sheet) => sum + sheet.rowCount, 0)} /><Stat label="Колонок" value={analysis.columns.length} /><Stat label="Сопоставлено стартов" value={analysis.raceValues.length ? `${Object.keys(raceMappings).length} из ${analysis.raceValues.length}` : analysis.resolvedRaceIds.length} /></div>
    {analysis.sheets.length > 1 && <p className="admin-muted">{analysis.sheets.map((sheet) => `${sheet.name}: ${sheet.rowCount}`).join(' · ')}</p>}
    {analysis.suggestedProfile && <AdminNotice tone="success">Применён профиль «{analysis.suggestedProfile.name}» по точной сигнатуре заголовков.</AdminNotice>}
    {needsColumnMapping && <><h4>Сопоставление колонок</h4><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Колонка файла</th><th>Поле Sports Results</th></tr></thead><tbody>{analysis.columns.map((column) => <tr key={column.header}><td><strong>{column.header}</strong>{column.automatic && <small className="admin-mapping-note"> распознано</small>}</td><td><select aria-label={`Сопоставление ${column.header}`} value={mappings[column.header] ?? ''} onChange={(change) => onMapping(column.header, change.target.value ? change.target.value as CanonicalImportField : null)}><option value="">Не импортировать</option>{analysis.canonicalFields.map((field) => <option key={field.field} value={field.field}>{field.displayName}{field.required ? ' — обязательно' : ''}</option>)}</select></td></tr>)}</tbody></table></div>
      <label className="admin-check"><input type="checkbox" checked={saveProfile} onChange={(change) => onSaveProfile(change.target.checked)} /><span>Сохранить это сопоставление</span></label>
      <p className="admin-muted">Система запомнит, как читать файлы с таким набором колонок.</p>
      {saveProfile && <Field label="Название формата файла" hint="Например: MyLaps, ChronoTrack или формат подрядчика Казань"><input maxLength={160} value={profileName} onChange={(change) => onProfileName(change.target.value)} /></Field>}
    </>}
    {sourceKind === 'EXTERNAL' && externalScope === 'MULTI' && raceFieldMapped && <><div className="admin-form-actions"><button className="admin-button-secondary" type="button" disabled={busy !== null} onClick={onRefresh}>{busy === 'analyze' ? 'Читаем значения…' : 'Найти старты в файле'}</button></div>{analysis.raceValues.length > 0 && <><h4>Сопоставление стартов</h4><p className="admin-muted">Сопоставлено стартов: {Object.keys(raceMappings).length} из {analysis.raceValues.length}</p><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>В файле</th><th>Старт мероприятия</th></tr></thead><tbody>{analysis.raceValues.map((value) => <tr key={value.sourceValue}><td><strong>{value.sourceValue}</strong>{value.automatic && <small className="admin-mapping-note"> распознано</small>}</td><td><select value={raceMappings[value.sourceValue] ?? ''} onChange={(change) => onRaceMapping(value.sourceValue, Number(change.target.value))}><option value="">Выберите Старт</option>{races.map((race) => <option key={race.id} value={race.id}>{race.name}</option>)}</select></td></tr>)}</tbody></table></div><label className="admin-check"><input type="checkbox" checked={saveRaceMappings} onChange={(change) => onSaveRaceMappings(change.target.checked)} /><span>Запомнить сопоставление стартов для следующих импортов</span></label><p className="admin-muted">При следующей загрузке система попробует сопоставить эти обозначения автоматически.</p></>}</>}
    {mappingProblems.length > 0 && <AdminNotice tone="danger"><ul>{mappingProblems.map((problem) => <li key={problem}>{problem}</li>)}</ul></AdminNotice>}
    <div className="admin-form-actions"><button className="admin-button-primary" type="button" disabled={mappingProblems.length > 0 || busy !== null || (saveProfile && !profileName.trim())} onClick={onValidate}>{busy === 'preview' ? 'Проверяем…' : 'Проверить данные'}</button><span>{Object.values(mappings).length} полей будет импортировано</span></div>
  </section>
}

function importReason(code: string, fallback: string): string {
  const reasons: Record<string, string> = {
    NEW_REGISTRATION: 'Участник будет добавлен',
    EMERGENCY_INSERT_NEW: 'Участник будет добавлен в новый набор данных',
    NO_CHANGES: 'Изменений в данных не обнаружено',
    SAFE_CHANGES_FOUND: 'Найдены изменения, которые можно применить',
    TARGET_RACE_OUT_OF_SCOPE: 'Строка относится к старту вне выбранной области',
    CURRENT_RACE_OUT_OF_SCOPE: 'Текущий участник относится к старту вне выбранной области',
    DUPLICATE_BIB_IN_FILE: 'Стартовый номер повторяется в файле',
    AMBIGUOUS_EVENT_BIB: 'По этому номеру найдено несколько участников',
    EMPTY_BIB: 'Для безопасного сопоставления нужен стартовый номер',
    UNKNOWN_RACE: 'Старт из файла не сопоставлен с мероприятием',
    INVALID_SOURCE_ROW: 'В строке есть некорректные значения',
    START_CLUSTER_CLEAR_NOT_ALLOWED: 'Пустое значение волны не может удалить существующую привязку',
    AMBIGUOUS_START_CLUSTER: 'Стартовая волна сопоставляется неоднозначно',
    START_CLUSTER_IDENTIFIERS_CONFLICT: 'Обозначения стартовой волны относятся к разным сохранённым волнам',
    RACE_MOVE_REQUIRES_DOB: 'Для переноса между стартами нужна дата рождения из файла',
    RACE_MOVE_DOB_MISMATCH: 'Дата рождения не подтверждает перенос между стартами',
    RACE_MOVE_CLUSTER_REQUIRED: 'Для переноса между стартами укажите стартовую волну',
    CROSS_SCOPE_BIB_CONFLICT: 'Такой номер уже используется в старте вне области замены',
    EMPTY_REPLACEMENT_SCOPE: 'В файле нет строк для выбранных стартов',
    MINOR_SOURCE_CATEGORY_REQUIRED: 'Для несовершеннолетнего без даты рождения нужна категория из файла',
  }
  return reasons[code] ?? fallback
}

function importDiagnostic(code: string, fallback: string): string {
  const diagnostics: Record<string, string> = {
    CSV_VALUE_INVALID: 'Проверьте значение в указанной колонке.',
    SOURCE_VALUE_INVALID: 'Проверьте значение в указанной колонке.',
  }
  return diagnostics[code] ?? importReason(code, fallback)
}

function importFieldLabel(field: string): string {
  const labels: Record<string, string> = {
    race: 'Старт', firstName: 'Имя', lastName: 'Фамилия', displayName: 'Имя участника',
    gender: 'Пол', birthDate: 'Дата рождения', sourceCategory: 'Категория из файла',
    effectiveCategory: 'Категория', categoryDefinition: 'Категория', cluster: 'Стартовая волна',
    clusterDefinition: 'Стартовая волна', result: 'Результат', status: 'Статус',
    gunTimeMs: 'Официальное время', chipTimeMs: 'Чистое время', overallPlace: 'Место',
    genderPlace: 'Место по полу', categoryPlace: 'Место в категории', netOverallPlace: 'Место по чистому времени',
    netGenderPlace: 'Место по полу (чистое время)', netCategoryPlace: 'Место в категории (чистое время)',
  }
  return labels[field] ?? 'Данные'
}

function importDiffValue(field: string, value: string | null): string {
  if (!value) return '—'
  if (field === 'categoryDefinition') return `Будет создана: ${value.replace(/^CREATE:/, '')}`
  if (field === 'clusterDefinition') return `Будет добавлена: ${value}`
  if (field === 'result' && value === 'CREATE') return 'Будет создан'
  return value
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return <div><span>{label}</span><strong>{value}</strong></div>
}
