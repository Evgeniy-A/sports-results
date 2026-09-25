/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { AdminApi } from '../api'
import type { AdminCategory, AwardPolicy, EventSummary, Race, RecalculationPreview } from '../types'
import { adminErrorMessage } from '../utils'
import { awardPolicyUpdate } from '../awardPolicy'
import { AdminNotice, Field, Loadable, StatusBadge } from '../components/AdminUi'
import { AwardEditor } from '../components/EventStartComposer'
import { CategorySettings } from '../components/CategorySettings'

export function EventAwardTab({ api, event, races, initialRaceId, onChanged }: { api: AdminApi; event: EventSummary; races: Race[]; initialRaceId?: number; onChanged: () => Promise<void> }) {
  const [raceId, setRaceId] = useState(initialRaceId && races.some((race) => race.id === initialRaceId) ? initialRaceId : races[0]?.id ?? 0)
  const [policy, setPolicy] = useState<AwardPolicy | null>(null)
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [recalcRaceIds, setRecalcRaceIds] = useState<number[]>(races.filter((race) => race.resultRecalculationRequired).map((race) => race.id))
  const [preview, setPreview] = useState<RecalculationPreview | null>(null)
  const race = races.find((candidate) => candidate.id === raceId)

  const loadPolicy = useCallback(async () => {
    if (!raceId) { setLoading(false); return }
    setLoading(true); setError(null)
    try {
      const [loadedPolicy, loadedCategories] = await Promise.all([
        api.awardPolicy(raceId), api.categories(raceId),
      ])
      setPolicy(loadedPolicy); setCategories(loadedCategories)
    }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, raceId])
  useEffect(() => { void loadPolicy() }, [loadPolicy])

  const save = async () => {
    if (!policy) return; setBusy('policy'); setError(null); setSuccess(null)
    try { setPolicy(await api.updateAwardPolicy(raceId, awardPolicyUpdate(policy))); setSuccess('Настройки зачёта сохранены. Технические поля старта синхронизированы системой.'); await onChanged() }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }
  const buildPreview = async () => {
    setBusy('preview'); setError(null); setSuccess(null)
    try { setPreview(await api.recalculationPreview(event.id, recalcRaceIds)) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }
  const applyPreview = async () => {
    if (!preview) return; setBusy('apply'); setError(null)
    try { await api.recalculationApply(event.id, preview.operationId); setPreview(null); setSuccess('Пересчёт применён. Категории и официальные места обновлены системой.'); await onChanged(); setRecalcRaceIds([]) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }

  if (!races.length) return <div className="admin-empty">Сначала создайте старт.</div>
  return <div className="admin-stack">
    <div className="admin-section-toolbar"><div><h2>Зачёт и награждение</h2><p>Эти настройки — единственный источник правил официального протокола.</p></div><Field label="Старт"><select value={raceId} onChange={(change) => setRaceId(Number(change.target.value))}>{races.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field></div>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}{success && <AdminNotice tone="success">{success}</AdminNotice>}
    <Loadable loading={loading} error={null} empty={!policy}>{policy && <section className="admin-card"><div className="admin-card-heading"><div><h3>Настройки награждения</h3><p>{rankingExplanation(policy.rankingBasis)}</p></div><StatusBadge value={race?.resultsPublicationStatus ?? 'DRAFT'} /></div>
      {race?.resultsPublicationStatus === 'PUBLISHED' && <AdminNotice tone="warning">Сначала верните старт в черновик. Сохранение настроек не меняет статус публикации автоматически.</AdminNotice>}
      <div className="admin-form">
        <AwardEditor value={awardPolicyUpdate(policy)} expanded allowRemove={false} onChange={(next) => { if (next) setPolicy({ ...policy, ...next }) }} categorySettings={<CategorySettings categories={categories} disabled={race?.resultsPublicationStatus === 'PUBLISHED'} onCreate={(body) => api.createCategory(raceId, body)} onUpdate={(categoryId, body) => api.updateCategory(raceId, categoryId, body)} onDelete={(categoryId) => api.deleteCategory(raceId, categoryId)} onReload={async () => { setCategories(await api.categories(raceId)); await onChanged() }} />} />
        <AdminNotice tone="warning"><strong>Настройка влияет на итоговый протокол.</strong> Сохранение может создать обязательный recalculation pending; Apply никогда не выполняется автоматически.</AdminNotice>
        <button className="admin-button-primary" type="button" disabled={busy !== null || race?.resultsPublicationStatus === 'PUBLISHED'} onClick={() => void save()}>Сохранить настройки</button>
      </div>
    </section>}</Loadable>

    <section className="admin-card"><div className="admin-card-heading"><div><h3>Пересчёт категорий</h3><p>Сначала Preview, затем отдельный Apply.</p></div></div>
      <fieldset className="admin-checkbox-grid"><legend>Старты для пересчёта</legend>{races.map((item) => <label key={item.id}><input type="checkbox" checked={recalcRaceIds.includes(item.id)} onChange={() => { setPreview(null); setRecalcRaceIds((current) => current.includes(item.id) ? current.filter((id) => id !== item.id) : [...current, item.id]) }} /><span><strong>{item.name}</strong><small>{item.resultRecalculationRequired ? 'Требуется пересчёт' : 'Нет ожидающих изменений'} · {item.resultsPublicationStatus === 'PUBLISHED' ? 'Опубликовано' : 'Черновик'}</small></span></label>)}</fieldset>
      <button className="admin-button-secondary" type="button" disabled={!recalcRaceIds.length || busy !== null} onClick={() => void buildPreview()}>{busy === 'preview' ? 'Считаем Preview…' : 'Посмотреть изменения'}</button>
      {preview && <div className="admin-recalc-preview"><div className="admin-stat-grid"><Stat label="Registration" value={preview.currentRegistrationCount} /><Stat label="Несовершеннолетние" value={preview.minorCount} /><Stat label="Взрослые" value={preview.adultCount} /><Stat label="Без DOB" value={preview.noBirthDateCount} /><Stat label="Изменится" value={preview.changedCategoryCount} /><Stat label="Без изменений" value={preview.unchangedCategoryCount} /><Stat label="Blocker" value={preview.blockingCount} /></div><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Bib</th><th>Старая категория</th><th>Новая категория</th><th>Причина</th><th>Blocker</th></tr></thead><tbody>{preview.rows.map((row) => <tr key={row.registrationId}><td>{row.bib ?? '—'}</td><td>{row.oldEffectiveCategory ?? '—'}</td><td>{row.newEffectiveCategory ?? '—'}</td><td>{row.reason}</td><td>{row.blockingCode ?? '—'}</td></tr>)}</tbody></table></div>{preview.rowsTruncated && <p className="admin-muted">Показана часть diff.</p>}<button className="admin-button-primary" type="button" disabled={preview.blockingCount > 0 || busy !== null} onClick={() => void applyPreview()}>{busy === 'apply' ? 'Применяем…' : 'Применить пересчёт'}</button></div>}
    </section>
  </div>
}

function rankingExplanation(basis: AwardPolicy['rankingBasis']): string {
  if (basis === 'GUN_TIME') return 'Официальные места рассчитываются по официальному времени.'
  if (basis === 'CHIP_TIME') return 'Официальные места рассчитываются по чистому времени.'
  return 'Официальный зачёт отключён.'
}

function Stat({ label, value }: { label: string; value: number }) { return <div><span>{label}</span><strong>{value}</strong></div> }
