/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { AwardPolicy, EventSummary, Race, RecalculationPreview } from '../types'
import { adminErrorMessage } from '../utils'
import { awardPolicyUpdate, withRankingBasis } from '../awardPolicy'
import { AdminNotice, ConfirmDialog, Field, Loadable, StatusBadge } from '../components/AdminUi'

export function EventAwardTab({ api, event, races, onChanged }: { api: AdminApi; event: EventSummary; races: Race[]; onChanged: () => Promise<void> }) {
  const [raceId, setRaceId] = useState(races[0]?.id ?? 0)
  const [policy, setPolicy] = useState<AwardPolicy | null>(null)
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
    try { setPolicy(await api.awardPolicy(raceId)) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, raceId])
  useEffect(() => { void loadPolicy() }, [loadPolicy])

  const save = async (eventSubmit: FormEvent) => {
    eventSubmit.preventDefault(); if (!policy) return; setBusy('policy'); setError(null); setSuccess(null)
    try { setPolicy(await api.updateAwardPolicy(raceId, awardPolicyUpdate(policy))); setSuccess('Настройки зачёта сохранены. Технические поля старта синхронизированы backend.'); await onChanged() }
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
    try { await api.recalculationApply(event.id, preview.operationId); setPreview(null); setSuccess('Пересчёт применён. Категории и ranking обновлены backend.'); await onChanged(); setRecalcRaceIds([]) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }

  if (!races.length) return <div className="admin-empty">Сначала создайте старт.</div>
  return <div className="admin-stack">
    <div className="admin-section-toolbar"><div><h2>Зачёт и награждение</h2><p>Эти настройки — единственный источник правил официального протокола.</p></div><Field label="Старт"><select value={raceId} onChange={(change) => setRaceId(Number(change.target.value))}>{races.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field></div>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}{success && <AdminNotice tone="success">{success}</AdminNotice>}
    <Loadable loading={loading} error={null} empty={!policy}>{policy && <section className="admin-card"><div className="admin-card-heading"><div><h3>Официальный зачёт</h3><p>{rankingExplanation(policy.rankingBasis)}</p></div><StatusBadge value={race?.resultsPublicationStatus ?? 'DRAFT'} /></div>
      {race?.resultsPublicationStatus === 'PUBLISHED' && <AdminNotice tone="warning">Сначала верните старт в черновик. Сохранение настроек не меняет статус публикации автоматически.</AdminNotice>}
      <form className="admin-form" onSubmit={save}>
        <div className="admin-form-grid"><Field label="Ranking basis"><select value={policy.rankingBasis} onChange={(change) => setPolicy(withRankingBasis(policy, change.target.value as AwardPolicy['rankingBasis']))}><option value="GUN_TIME">Официальное время (Gun Time)</option><option value="CHIP_TIME">Чистое время (Chip Time)</option><option value="NONE">Официальный зачёт отключён</option></select></Field><Field label="Primary standing"><select value={policy.primaryStandingMode} disabled={policy.rankingBasis === 'NONE'} onChange={(change) => setPolicy({ ...policy, primaryStandingMode: change.target.value as AwardPolicy['primaryStandingMode'] })}><option value="ALL">Общий</option><option value="BY_GENDER">По полу</option><option value="NONE">Нет</option></select></Field></div>
        <div className="admin-form-grid"><Field label="Призовых мест primary"><input type="number" min="0" max="1000" disabled={policy.rankingBasis === 'NONE'} value={policy.absolutePrizePlaces} onChange={(change) => setPolicy({ ...policy, absolutePrizePlaces: Number(change.target.value) })} /></Field><Field label="Расчёт возраста взрослых"><select value={policy.ageCalculationMode} onChange={(change) => setPolicy({ ...policy, ageCalculationMode: change.target.value as AwardPolicy['ageCalculationMode'] })}><option value="EVENT_DATE">На дату мероприятия</option><option value="END_OF_EVENT_YEAR">На конец года мероприятия</option></select></Field></div>
        <label className="admin-check"><input type="checkbox" disabled={policy.rankingBasis === 'NONE'} checked={policy.categoryEnabled} onChange={(change) => setPolicy({ ...policy, categoryEnabled: change.target.checked })} /><span>Включить официальный категорийный зачёт</span></label>
        {policy.categoryEnabled && <><Field label="Призовых мест в категории"><input type="number" min="0" max="1000" value={policy.categoryPrizePlaces} onChange={(change) => setPolicy({ ...policy, categoryPrizePlaces: Number(change.target.value) })} /></Field><label className="admin-check"><input type="checkbox" checked={policy.excludeAbsoluteWinnersFromCategory} onChange={(change) => setPolicy({ ...policy, excludeAbsoluteWinnersFromCategory: change.target.checked })} /><span>Исключать победителей primary из категорийного зачёта</span></label></>}
        <AdminNotice tone="warning"><strong>Настройка влияет на итоговый протокол.</strong> Сохранение может создать обязательный recalculation pending; Apply никогда не выполняется автоматически.</AdminNotice>
        <button className="admin-button-primary" disabled={busy !== null || race?.resultsPublicationStatus === 'PUBLISHED'}>Сохранить настройки</button>
      </form>
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

export function EventPublicationTab({ api, event, races, onChanged }: { api: AdminApi; event: EventSummary; races: Race[]; onChanged: () => Promise<void> }) {
  const [eventStatus, setEventStatus] = useState(event.publicationStatus)
  const [resultsStatus, setResultsStatus] = useState(event.resultsPublicationStatus)
  const [confirm, setConfirm] = useState<{ kind: 'event' | 'publishRace' | 'draftRace'; id?: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)

  const applyConfirm = async () => {
    if (!confirm) return; setBusy(true); setMessage(null)
    try {
      if (confirm.kind === 'event') await api.updateEventPublication(event.id, { eventPublicationStatus: eventStatus, resultsPublicationStatus: resultsStatus })
      if (confirm.kind === 'publishRace' && confirm.id) await api.publishRace(event.id, confirm.id)
      if (confirm.kind === 'draftRace' && confirm.id) await api.draftRace(event.id, confirm.id, 'Изменено через Admin UI')
      setMessage({ tone: 'success', text: 'Состояние публикации обновлено.' }); setConfirm(null); await onChanged()
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(false) }
  }

  const updateRaceVisibility = async (race: Race) => {
    setBusy(true); setMessage(null)
    try { await api.updateRace(event.id, race.id, { sourceCode: race.sourceCode, name: race.name, distanceMeters: race.distanceMeters, startsAt: race.startsAt, displayOrder: race.displayOrder, publicVisible: !race.publicVisible }); await onChanged() }
    catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(false) }
  }

  return <div className="admin-stack">
    <div className="admin-section-toolbar"><div><h2>Публикация</h2><p>Мероприятие, общий доступ к результатам и каждый старт управляются независимо.</p></div></div>
    {message && <AdminNotice tone={message.tone}>{message.text}</AdminNotice>}
    <section className="admin-card"><div className="admin-card-heading"><div><h3>Мероприятие</h3><p>Публикация карточки мероприятия и общий доступ к результатам.</p></div></div><div className="admin-form-grid"><Field label="Статус мероприятия"><select value={eventStatus} onChange={(change) => setEventStatus(change.target.value as EventSummary['publicationStatus'])}><option value="DRAFT">Черновик</option><option value="PUBLISHED">Опубликовано</option><option value="ARCHIVED">Архив</option></select></Field><Field label="Доступ к результатам"><select value={resultsStatus} onChange={(change) => setResultsStatus(change.target.value as EventSummary['resultsPublicationStatus'])}><option value="DRAFT">Скрыты</option><option value="PUBLISHED">Доступны</option></select></Field></div><button className="admin-button-primary" disabled={busy || (eventStatus === event.publicationStatus && resultsStatus === event.resultsPublicationStatus)} onClick={() => setConfirm({ kind: 'event' })}>Сохранить публикацию мероприятия</button></section>
    <section className="admin-card"><div className="admin-card-heading"><div><h3>Старты</h3><p>Видимость и публикация результатов настраиваются для каждого старта.</p></div></div><div className="admin-publication-list">{races.map((race) => <article key={race.id}><div><strong>{race.name}</strong><p>{effectiveVisibility(event, race)}</p></div><div className="admin-inline-badges"><StatusBadge value={race.resultsPublicationStatus} />{race.publicVisible ? <span className="admin-badge admin-badge-success">Старт виден</span> : <span className="admin-badge admin-badge-neutral">Старт скрыт</span>}</div><div className="admin-row-actions"><button className="admin-link-button" disabled={busy} onClick={() => void updateRaceVisibility(race)}>{race.publicVisible ? 'Скрыть старт' : 'Показать старт'}</button>{race.resultsPublicationStatus === 'PUBLISHED' ? <button className="admin-link-button danger" onClick={() => setConfirm({ kind: 'draftRace', id: race.id })}>Вернуть в черновик</button> : <button className="admin-link-button" onClick={() => setConfirm({ kind: 'publishRace', id: race.id })}>Опубликовать</button>}</div></article>)}</div></section>
    {confirm && <ConfirmDialog title={confirm.kind === 'event' ? 'Изменить публикацию мероприятия?' : confirm.kind === 'publishRace' ? 'Опубликовать старт?' : 'Вернуть старт в черновик?'} description={confirm.kind === 'event' ? 'Публичная доступность изменится сразу. Спортивные факты и официальный зачёт не пересчитываются.' : confirm.kind === 'publishRace' ? 'Backend проверит готовность протокола и обязательный пересчёт.' : 'Публичный протокол станет недоступен, но данные останутся сохранены.'} confirmLabel={confirm.kind === 'draftRace' ? 'Вернуть в черновик' : 'Подтвердить'} danger={confirm.kind === 'draftRace'} busy={busy} onConfirm={() => void applyConfirm()} onClose={() => setConfirm(null)} />}
  </div>
}

function effectiveVisibility(event: EventSummary, race: Race): string {
  if (event.publicationStatus !== 'PUBLISHED') return 'Скрыто: мероприятие не опубликовано.'
  if (event.resultsPublicationStatus !== 'PUBLISHED') return 'Скрыто: результаты мероприятия отключены.'
  if (!race.publicVisible) return 'Старт скрыт настройками публичной видимости.'
  if (race.resultsPublicationStatus !== 'PUBLISHED') return 'Скрыто: результаты старта в черновике.'
  return 'Публичные результаты доступны.'
}
