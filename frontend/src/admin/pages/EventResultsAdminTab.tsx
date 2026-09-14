/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { AdminCategory, AdminResultDetails, EventSummary, PageResponse, Race, ResultListItem, SportFormat, StartCluster } from '../types'
import { formatDuration, formatGender } from '../../utils/format'
import { parseClaimedTime } from '../../utils/resultIssue'
import { adminErrorMessage } from '../utils'
import { AdminNotice, AdminPagination, Drawer, Field, Loadable, StatusBadge } from '../components/AdminUi'

const EMPTY_PAGE: PageResponse<ResultListItem> = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, sort: 'place', direction: 'asc' }

export function EventResultsAdminTab({ api, event, races, formats, mode }: {
  api: AdminApi
  event: EventSummary
  races: Race[]
  formats: SportFormat[]
  mode: 'participants' | 'results'
}) {
  const [rows, setRows] = useState(EMPTY_PAGE)
  const [filters, setFilters] = useState({ name: '', bib: '', sportFormatId: '', raceId: '', categoryId: '', clusterId: '', status: '', sort: mode === 'results' ? 'place' : 'displayName', direction: 'asc', page: 0 })
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [clusters, setClusters] = useState<StartCluster[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedResultId, setSelectedResultId] = useState<number | null>(null)
  const availableRaces = useMemo(() => filters.sportFormatId ? races.filter((race) => race.sportFormatId === Number(filters.sportFormatId)) : races, [filters.sportFormatId, races])

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const page = await api.results(event.id, {
        name: filters.name, bib: filters.bib,
        sportFormatId: filters.sportFormatId ? Number(filters.sportFormatId) : undefined,
        raceId: filters.raceId ? Number(filters.raceId) : undefined,
        categoryId: filters.categoryId ? Number(filters.categoryId) : undefined,
        clusterId: filters.clusterId ? Number(filters.clusterId) : undefined,
        status: filters.status, page: filters.page, size: 50,
        sort: filters.sort, direction: filters.direction, rankingBasis: 'CHIP_TIME',
      })
      setRows(page)
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, event.id, filters])
  useEffect(() => { void load() }, [load])

  useEffect(() => {
    const raceId = Number(filters.raceId)
    if (!raceId) { setCategories([]); setClusters([]); return }
    Promise.all([api.categories(raceId), api.clusters(event.id, raceId)])
      .then(([categoryValues, clusterValues]) => { setCategories(categoryValues); setClusters(clusterValues) })
      .catch((reason) => setError(adminErrorMessage(reason)))
  }, [api, event.id, filters.raceId])

  const setFilter = (field: keyof typeof filters, value: string | number) => setFilters((current) => ({ ...current, [field]: value, page: field === 'page' ? Number(value) : 0 }))

  return <div className="admin-stack">
    <div className="admin-section-toolbar"><div><h2>{mode === 'participants' ? 'Участники мероприятия' : 'Результаты'}</h2><p>{mode === 'participants' ? 'Текущие Registration по всем форматам и Race. Общего места по Event не вычисляется.' : 'Текущие спортивные факты. Ranking пересчитывает только backend.'}</p></div><span className="admin-count">{rows.totalElements} записей</span></div>
    <div className="admin-filter-grid">
      <Field label="Bib"><input value={filters.bib} onChange={(change) => setFilter('bib', change.target.value)} /></Field>
      <Field label="Участник"><input value={filters.name} onChange={(change) => setFilter('name', change.target.value)} /></Field>
      <Field label="SportFormat"><select value={filters.sportFormatId} onChange={(change) => { setFilters((current) => ({ ...current, sportFormatId: change.target.value, raceId: '', categoryId: '', clusterId: '', page: 0 })) }}><option value="">Все форматы</option>{formats.map((format) => <option key={format.id} value={format.id}>{format.displayName}</option>)}</select></Field>
      <Field label="Race"><select value={filters.raceId} onChange={(change) => setFilters((current) => ({ ...current, raceId: change.target.value, categoryId: '', clusterId: '', page: 0 }))}><option value="">Все Race</option>{availableRaces.map((race) => <option key={race.id} value={race.id}>{race.name}</option>)}</select></Field>
      <Field label="Категория"><select disabled={!filters.raceId} value={filters.categoryId} onChange={(change) => setFilter('categoryId', change.target.value)}><option value="">Все</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.displayName}</option>)}</select></Field>
      <Field label="StartCluster"><select disabled={!filters.raceId} value={filters.clusterId} onChange={(change) => setFilter('clusterId', change.target.value)}><option value="">Все</option>{clusters.map((cluster) => <option key={cluster.id} value={cluster.id}>{cluster.displayName}</option>)}</select></Field>
      <Field label="Статус"><select value={filters.status} onChange={(change) => setFilter('status', change.target.value)}><option value="">Все</option><option value="finished">Финишировал</option><option value="running">На дистанции</option><option value="notstarted">Не стартовал</option><option value="disqualified">Дисквалификация</option><option value="quarantine">Карантин</option></select></Field>
      <Field label="Сортировка"><select value={filters.sort} onChange={(change) => setFilter('sort', change.target.value)}><option value="place">Место</option><option value="bib">Bib</option><option value="displayName">Имя</option><option value="gunTime">Официальное время</option><option value="chipTime">Чистое время</option></select></Field>
    </div>
    <Loadable loading={loading} error={error} empty={!rows.content.length}>
      <div className="admin-table-wrap"><table className="admin-table"><thead><tr>{mode === 'results' && <th>Место</th>}<th>Bib</th><th>Участник</th><th>SportFormat / Race</th><th>Категория</th>{mode === 'participants' && <><th>Source category</th><th>StartCluster</th><th>Пол</th></>}<th>Статус</th>{mode === 'results' && <><th>Официальное время</th><th>Чистое время</th></>}<th /></tr></thead><tbody>{rows.content.map((row) => { const race = races.find((candidate) => candidate.id === row.raceId); return <tr key={row.resultId}>{mode === 'results' && <td>{row.place ?? '—'}</td>}<td><strong>{row.bib ?? '—'}</strong></td><td>{row.displayName}</td><td><small>{race?.sportFormatName ?? '—'}</small>{row.raceName}</td><td>{row.category?.name ?? '—'}</td>{mode === 'participants' && <><td>{row.sourceCategory ?? '—'}</td><td>{row.clusterName ?? '—'}</td><td>{formatGender(row.gender)}</td></>}<td><StatusBadge value={row.status} /></td>{mode === 'results' && <><td>{formatDuration(row.gunTimeMs)}</td><td>{formatDuration(row.chipTimeMs)}</td></>}<td><button className="admin-row-link" onClick={() => setSelectedResultId(row.resultId)}>Открыть</button></td></tr> })}</tbody></table></div>
      <AdminPagination page={rows.page} totalPages={rows.totalPages} onChange={(page) => setFilter('page', page)} />
    </Loadable>
    {selectedResultId && <ResultEditor api={api} resultId={selectedResultId} races={races} onClose={() => setSelectedResultId(null)} onSaved={async () => { setSelectedResultId(null); await load() }} />}
  </div>
}

function ResultEditor({ api, resultId, races, onClose, onSaved }: { api: AdminApi; resultId: number; races: Race[]; onClose: () => void; onSaved: () => Promise<void> }) {
  const [detail, setDetail] = useState<AdminResultDetails | null>(null)
  const [clusters, setClusters] = useState<StartCluster[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [registration, setRegistration] = useState({ displayName: '', firstName: '', lastName: '', birthDate: '', gender: '', bib: '', sourceCategory: '', clusterId: '', entryKind: 'UNKNOWN' })
  const [result, setResult] = useState({ status: '', gunTime: '', chipTime: '', overallPlace: '', genderPlace: '', categoryPlace: '', netOverallPlace: '', netGenderPlace: '', netCategoryPlace: '' })

  useEffect(() => {
    api.result(resultId).then(async (value) => {
      setDetail(value)
      setRegistration({ displayName: value.displayName, firstName: value.firstName ?? '', lastName: value.lastName ?? '', birthDate: value.birthDate ?? '', gender: value.gender ?? '', bib: value.bib ?? '', sourceCategory: value.sourceCategory ?? '', clusterId: value.clusterId === null ? '' : String(value.clusterId), entryKind: value.entryKind })
      setResult({ status: value.status, gunTime: durationInput(value.gunTimeMs), chipTime: durationInput(value.chipTimeMs), overallPlace: nullableText(value.overallPlace), genderPlace: nullableText(value.genderPlace), categoryPlace: nullableText(value.categoryPlace), netOverallPlace: nullableText(value.netOverallPlace), netGenderPlace: nullableText(value.netGenderPlace), netCategoryPlace: nullableText(value.netCategoryPlace) })
      setClusters(await api.clusters(value.eventId, value.raceId)); setLoading(false)
    }).catch((reason) => { setError(adminErrorMessage(reason)); setLoading(false) })
  }, [api, resultId])
  const race = races.find((candidate) => candidate.id === detail?.raceId)

  const saveRegistration = async (event: FormEvent) => {
    event.preventDefault(); if (!detail) return; setBusy('registration'); setError(null)
    try { await api.updateRegistration(detail.registrationId, { ...registration, firstName: registration.firstName || null, lastName: registration.lastName || null, birthDate: registration.birthDate || null, gender: registration.gender || null, bib: registration.bib || null, sourceCategory: registration.sourceCategory || null, clusterId: registration.clusterId ? Number(registration.clusterId) : null }); await onSaved() }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }
  const saveResult = async (event: FormEvent) => {
    event.preventDefault(); if (!detail) return
    const gunTimeMs = result.gunTime ? parseClaimedTime(result.gunTime) : null
    const chipTimeMs = result.chipTime ? parseClaimedTime(result.chipTime) : null
    if ((result.gunTime && gunTimeMs === null) || (result.chipTime && chipTimeMs === null)) { setError('Время укажите как ЧЧ:ММ:СС или ЧЧ:ММ:СС.мс.'); return }
    setBusy('result'); setError(null)
    try { await api.updateResult(detail.resultId, { status: result.status, gunTimeMs, chipTimeMs, overallPlace: numberOrNull(result.overallPlace), genderPlace: numberOrNull(result.genderPlace), categoryPlace: numberOrNull(result.categoryPlace), netOverallPlace: numberOrNull(result.netOverallPlace), netGenderPlace: numberOrNull(result.netGenderPlace), netCategoryPlace: numberOrNull(result.netCategoryPlace) }); await onSaved() }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(null) }
  }

  return <Drawer title={`Результат №${resultId}`} onClose={onClose}><Loadable loading={loading} error={error} empty={!detail}>{detail && <div className="admin-stack">
    {race?.resultsPublicationStatus === 'PUBLISHED' && <AdminNotice tone="warning">Race опубликован. Сохранённая коррекция сразу изменит текущие публичные данные; проверьте согласованность мест и времени.</AdminNotice>}
    <section><h3>Registration</h3><p className="admin-muted">Текущий snapshot участника. Исторические retired записи через эту форму не редактируются.</p><form className="admin-form" onSubmit={saveRegistration}><Field label="Отображаемое имя"><input required value={registration.displayName} onChange={(change) => setRegistration({ ...registration, displayName: change.target.value })} /></Field><div className="admin-form-grid"><Field label="Имя"><input value={registration.firstName} onChange={(change) => setRegistration({ ...registration, firstName: change.target.value })} /></Field><Field label="Фамилия"><input value={registration.lastName} onChange={(change) => setRegistration({ ...registration, lastName: change.target.value })} /></Field></div><div className="admin-form-grid"><Field label="Bib"><input value={registration.bib} onChange={(change) => setRegistration({ ...registration, bib: change.target.value })} /></Field><Field label="Дата рождения"><input type="date" value={registration.birthDate} onChange={(change) => setRegistration({ ...registration, birthDate: change.target.value })} /></Field></div><div className="admin-form-grid"><Field label="Пол"><input value={registration.gender} onChange={(change) => setRegistration({ ...registration, gender: change.target.value })} /></Field><Field label="Entry kind"><select value={registration.entryKind} onChange={(change) => setRegistration({ ...registration, entryKind: change.target.value })}><option value="PERSON">PERSON</option><option value="TEAM">TEAM</option><option value="OTHER">OTHER</option><option value="UNKNOWN">UNKNOWN</option></select></Field></div><Field label="Source category"><input value={registration.sourceCategory} onChange={(change) => setRegistration({ ...registration, sourceCategory: change.target.value })} /></Field><Field label="StartCluster"><select value={registration.clusterId} onChange={(change) => setRegistration({ ...registration, clusterId: change.target.value })}><option value="">Без кластера</option>{clusters.map((cluster) => <option key={cluster.id} value={cluster.id}>{cluster.displayName}</option>)}</select></Field><p className="admin-muted">Эффективная категория: {detail.effectiveCategory?.name ?? 'не назначена'}</p><button className="admin-button-primary" disabled={busy !== null}>Сохранить Registration</button></form></section>
    <section><h3>Result</h3><form className="admin-form" onSubmit={saveResult}><Field label="Статус"><input required value={result.status} onChange={(change) => setResult({ ...result, status: change.target.value })} /></Field><div className="admin-form-grid"><Field label="Официальное время"><input placeholder="01:23:45.678" value={result.gunTime} onChange={(change) => setResult({ ...result, gunTime: change.target.value })} /></Field><Field label="Чистое время"><input placeholder="01:23:45.678" value={result.chipTime} onChange={(change) => setResult({ ...result, chipTime: change.target.value })} /></Field></div><div className="admin-place-grid">{(['overallPlace', 'genderPlace', 'categoryPlace', 'netOverallPlace', 'netGenderPlace', 'netCategoryPlace'] as const).map((field) => <Field key={field} label={field}><input type="number" min="1" value={result[field]} onChange={(change) => setResult({ ...result, [field]: change.target.value })} /></Field>)}</div><AdminNotice tone="info">Frontend не пересчитывает ranking. После сохранения таблица заново запрашивается у backend.</AdminNotice><button className="admin-button-primary" disabled={busy !== null}>Сохранить Result</button></form></section>
  </div>}</Loadable></Drawer>
}

function durationInput(value: number | null): string { return value === null ? '' : formatDuration(value) }
function nullableText(value: number | null): string { return value === null ? '' : String(value) }
function numberOrNull(value: string): number | null { return value === '' ? null : Number(value) }
