/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { AdminCategory, AdminResultDetails, Race, StartCluster } from '../types'
import { DigitAutoformatInput } from '../../components/DigitAutoformatInput'
import { formatDuration } from '../../utils/format'
import { formatDurationInput } from '../../utils/inputFormatting'
import { parseClaimedTime } from '../../utils/resultIssue'
import { adminErrorMessage } from '../utils'
import { AdminNotice, Field, Loadable } from './AdminUi'

export interface ResultIssueFocus {
  reason: string | null
  claimedGunTimeMs: number | null
  claimedChipTimeMs: number | null
  snapshotGunTimeMs: number | null
  snapshotChipTimeMs: number | null
}

export function AdminResultEditor({
  api,
  resultId,
  races,
  hideBirthDate = false,
  issueWorkspace = false,
  issueFocus,
  resultSaveLabel = 'Сохранить результат',
  onSaved,
}: {
  api: AdminApi
  resultId: number
  races: Race[]
  hideBirthDate?: boolean
  issueWorkspace?: boolean
  issueFocus?: ResultIssueFocus
  resultSaveLabel?: string
  onSaved?: () => Promise<void> | void
}) {
  const [detail, setDetail] = useState<AdminResultDetails | null>(null)
  const [clusters, setClusters] = useState<StartCluster[]>([])
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [registration, setRegistration] = useState({ displayName: '', firstName: '', lastName: '', birthDate: '', gender: '', bib: '', sourceCategory: '', categoryId: '', clusterId: '', entryKind: 'UNKNOWN' })
  const [result, setResult] = useState({ status: '', gunTime: '', chipTime: '' })

  const load = useCallback(async (showLoading = true) => {
    if (showLoading) setLoading(true)
    setError(null)
    try {
      const value = await api.result(resultId)
      setDetail(value)
      setRegistration({ displayName: value.displayName, firstName: value.firstName ?? '', lastName: value.lastName ?? '', birthDate: value.birthDate ?? '', gender: value.gender ?? '', bib: value.bib ?? '', sourceCategory: value.sourceCategory ?? '', categoryId: value.effectiveCategory === null ? '' : String(value.effectiveCategory.id), clusterId: value.clusterId === null ? '' : String(value.clusterId), entryKind: value.entryKind })
      setResult({ status: value.status, gunTime: durationInput(value.gunTimeMs), chipTime: durationInput(value.chipTimeMs) })
      const [clusterValues, categoryValues] = await Promise.all([
        api.clusters(value.eventId, value.raceId),
        api.categories(value.raceId),
      ])
      setClusters(clusterValues)
      setCategories(categoryValues)
    } catch (reason) {
      setError(adminErrorMessage(reason))
    } finally {
      if (showLoading) setLoading(false)
    }
  }, [api, resultId])

  useEffect(() => { void load() }, [load])
  const race = races.find((candidate) => candidate.id === detail?.raceId)

  const saveRegistration = async (event: FormEvent) => {
    event.preventDefault(); if (!detail) return; setBusy('registration'); setError(null); setSuccess(null)
    try {
      await api.updateRegistration(detail.registrationId, { ...registration, firstName: registration.firstName || null, lastName: registration.lastName || null, birthDate: registration.birthDate || null, gender: registration.gender || null, bib: registration.bib || null, sourceCategory: registration.sourceCategory || null, categoryId: registration.categoryId ? Number(registration.categoryId) : null, clusterId: registration.clusterId ? Number(registration.clusterId) : null })
      await load(false)
      await onSaved?.()
      setSuccess('Данные участника сохранены.')
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }

  const saveResult = async (event: FormEvent) => {
    event.preventDefault(); if (!detail) return
    const gunTimeMs = result.gunTime ? parseClaimedTime(result.gunTime) : null
    const chipTimeMs = result.chipTime ? parseClaimedTime(result.chipTime) : null
    if ((result.gunTime && gunTimeMs === null) || (result.chipTime && chipTimeMs === null)) { setError('Время укажите как ЧЧ:ММ:СС или ЧЧ:ММ:СС.мс.'); return }
    setBusy('result'); setError(null); setSuccess(null)
    try {
      await api.updateResult(detail.resultId, { status: result.status, gunTimeMs, chipTimeMs, overallPlace: detail.overallPlace, genderPlace: detail.genderPlace, categoryPlace: detail.categoryPlace, netOverallPlace: detail.netOverallPlace, netGenderPlace: detail.netGenderPlace, netCategoryPlace: detail.netCategoryPlace })
      await load(false)
      await onSaved?.()
      setSuccess('Результат сохранён. Статус обращения не изменён.')
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }

  const focusedGun = issueFocus?.reason === 'OFFICIAL_TIME'
  const focusedChip = issueFocus?.reason === 'CHIP_TIME'
  const visibleCategories = categories.filter((category) => category.enabled || String(category.id) === registration.categoryId)
  const knownGender = ['male', 'female'].includes(registration.gender.toLowerCase())
  const knownStatus = RESULT_STATUSES.some((status) => status.value === result.status)

  return <Loadable loading={loading} error={detail ? null : error} empty={!detail}>{detail && <div className="admin-stack admin-result-editor">
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    {success && <AdminNotice tone="success">{success}</AdminNotice>}
    {race?.resultsPublicationStatus === 'PUBLISHED' && <AdminNotice tone="warning">Старт опубликован. Сохранённая коррекция сразу изменит текущие публичные данные; проверьте согласованность мест и времени.</AdminNotice>}
    <section className="admin-card"><h3>Участник</h3><p className="admin-muted">Текущие данные участника. Исторический снимок обращения через эту форму не редактируется.</p><form className="admin-form" onSubmit={saveRegistration}><Field label="Отображаемое имя"><input required value={registration.displayName} onChange={(change) => setRegistration({ ...registration, displayName: change.target.value })} /></Field><div className="admin-form-grid"><Field label="Имя"><input value={registration.firstName} onChange={(change) => setRegistration({ ...registration, firstName: change.target.value })} /></Field><Field label="Фамилия"><input value={registration.lastName} onChange={(change) => setRegistration({ ...registration, lastName: change.target.value })} /></Field></div><div className="admin-form-grid"><Field label="Стартовый номер"><input value={registration.bib} onChange={(change) => setRegistration({ ...registration, bib: change.target.value })} /></Field>{!hideBirthDate && <Field label="Дата рождения"><input type="date" value={registration.birthDate} onChange={(change) => setRegistration({ ...registration, birthDate: change.target.value })} /></Field>}</div><div className="admin-form-grid"><Field label="Пол"><select value={registration.gender} onChange={(change) => setRegistration({ ...registration, gender: change.target.value })}><option value="">Не указан</option>{!knownGender && registration.gender && <option value={registration.gender}>Не указан</option>}<option value="male">Мужчина</option><option value="female">Женщина</option></select></Field>{!issueWorkspace && <Field label="Тип участника"><select value={registration.entryKind} onChange={(change) => setRegistration({ ...registration, entryKind: change.target.value })}><option value="PERSON">Человек</option><option value="TEAM">Команда</option><option value="OTHER">Другой</option><option value="UNKNOWN">Не определён</option></select></Field>}</div><Field label="Категория"><select value={registration.categoryId} disabled={visibleCategories.length === 0} onChange={(change) => setRegistration({ ...registration, categoryId: change.target.value })}><option value="">Без категории</option>{visibleCategories.map((category) => <option key={category.id} value={category.id}>{category.displayName}</option>)}</select></Field>{issueWorkspace ? <p className="admin-muted">Исходная категория из файла: {registration.sourceCategory || '—'}</p> : <Field label="Исходная категория"><input value={registration.sourceCategory} onChange={(change) => setRegistration({ ...registration, sourceCategory: change.target.value })} /></Field>}<Field label="Стартовая волна"><select value={registration.clusterId} onChange={(change) => setRegistration({ ...registration, clusterId: change.target.value })}><option value="">Без стартовой волны</option>{clusters.map((cluster) => <option key={cluster.id} value={cluster.id}>{cluster.displayName}</option>)}</select></Field><button className="admin-button-primary" disabled={busy !== null}>{busy === 'registration' ? 'Сохраняем…' : 'Сохранить участника'}</button></form></section>
    <section className="admin-card"><h3>Текущий результат</h3>{issueFocus && <div className="admin-result-comparison" aria-label="Сравнение времени"><Comparison label="Официальное время" current={detail.gunTimeMs} snapshot={issueFocus.snapshotGunTimeMs} claimed={issueFocus.claimedGunTimeMs} focused={focusedGun} /><Comparison label="Чистое время" current={detail.chipTimeMs} snapshot={issueFocus.snapshotChipTimeMs} claimed={issueFocus.claimedChipTimeMs} focused={focusedChip} /></div>}<form className="admin-form" onSubmit={saveResult}><Field label="Статус"><select required value={result.status} onChange={(change) => setResult({ ...result, status: change.target.value })}>{!knownStatus && <option value={result.status}>Другой статус</option>}{RESULT_STATUSES.map((status) => <option key={status.value} value={status.value}>{status.label}</option>)}</select></Field><div className="admin-form-grid"><div className={focusedGun ? 'admin-issue-focus-field' : undefined}><Field label="Официальное время"><DigitAutoformatInput aria-label="Официальное время" placeholder="ЧЧ:ММ:СС.ммм" value={result.gunTime} formatter={formatDurationInput} onValueChange={(value) => setResult({ ...result, gunTime: value })} /></Field>{focusedGun && issueFocus?.claimedGunTimeMs !== null && <button className="admin-button-subtle admin-button-compact" type="button" onClick={() => setResult({ ...result, gunTime: durationInput(issueFocus!.claimedGunTimeMs) })}>Подставить заявленное</button>}</div><div className={focusedChip ? 'admin-issue-focus-field' : undefined}><Field label="Чистое время"><DigitAutoformatInput aria-label="Чистое время" placeholder="ЧЧ:ММ:СС.ммм" value={result.chipTime} formatter={formatDurationInput} onValueChange={(value) => setResult({ ...result, chipTime: value })} /></Field>{focusedChip && issueFocus?.claimedChipTimeMs !== null && <button className="admin-button-subtle admin-button-compact" type="button" onClick={() => setResult({ ...result, chipTime: durationInput(issueFocus!.claimedChipTimeMs) })}>Подставить заявленное</button>}</div></div><AdminNotice tone="info">Официальные места система рассчитывает из текущих спортивных данных. Импортированные места эта форма не изменяет.</AdminNotice><button className="admin-button-primary" disabled={busy !== null}>{busy === 'result' ? 'Сохраняем…' : resultSaveLabel}</button></form></section>
  </div>}</Loadable>
}

function Comparison({ label, current, snapshot, claimed, focused }: { label: string; current: number | null; snapshot: number | null; claimed: number | null; focused: boolean }) {
  return <article className={focused ? 'is-focused' : undefined}><strong>{label}</strong><dl><div><dt>На момент обращения</dt><dd>{formatDuration(snapshot)}</dd></div><div><dt>Заявлено</dt><dd>{formatDuration(claimed)}</dd></div><div><dt>Сейчас</dt><dd>{formatDuration(current)}</dd></div></dl></article>
}

function durationInput(value: number | null): string { return value === null ? '' : formatDuration(value) }

const RESULT_STATUSES = [
  { value: 'finished', label: 'Финишировал' },
  { value: 'running', label: 'На дистанции' },
  { value: 'notstarted', label: 'Не стартовал' },
  { value: 'disqualified', label: 'Дисквалификация' },
  { value: 'quarantine', label: 'Карантин' },
] as const
