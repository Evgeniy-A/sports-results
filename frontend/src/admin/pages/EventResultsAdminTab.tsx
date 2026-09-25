/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { AdminApi } from '../api'
import type { AdminCategory, EventSummary, PageResponse, Race, ResultListItem, StartCluster } from '../types'
import { formatDuration, formatGender } from '../../utils/format'
import { adminErrorMessage } from '../utils'
import { AdminPagination, Drawer, Field, Loadable, StatusBadge } from '../components/AdminUi'
import { AdminResultEditor } from '../components/AdminResultEditor'

const EMPTY_PAGE: PageResponse<ResultListItem> = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, sort: 'place', direction: 'asc' }

export function EventResultsAdminTab({ api, event, races, mode }: {
  api: AdminApi
  event: EventSummary
  races: Race[]
  mode: 'participants' | 'results'
}) {
  const [rows, setRows] = useState(EMPTY_PAGE)
  const [filters, setFilters] = useState({ name: '', bib: '', raceId: '', categoryId: '', clusterId: '', status: '', sort: mode === 'results' ? 'place' : 'displayName', direction: 'asc', page: 0 })
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [clusters, setClusters] = useState<StartCluster[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedResultId, setSelectedResultId] = useState<number | null>(null)
  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const page = await api.results(event.id, {
        name: filters.name, bib: filters.bib,
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
    <div className="admin-section-toolbar"><div><h2>{mode === 'participants' ? 'Участники мероприятия' : 'Результаты'}</h2><p>{mode === 'participants' ? 'Текущие участники по всем стартам. Общее место по мероприятию не вычисляется.' : 'Текущие спортивные факты. Официальный зачёт рассчитывает только система.'}</p></div><span className="admin-count">{rows.totalElements} записей</span></div>
    <div className="admin-filter-grid">
      <Field label="Стартовый номер"><input value={filters.bib} onChange={(change) => setFilter('bib', change.target.value)} /></Field>
      <Field label="Участник"><input value={filters.name} onChange={(change) => setFilter('name', change.target.value)} /></Field>
      <Field label="Старт"><select value={filters.raceId} onChange={(change) => setFilters((current) => ({ ...current, raceId: change.target.value, categoryId: '', clusterId: '', page: 0 }))}><option value="">Все старты</option>{races.map((race) => <option key={race.id} value={race.id}>{race.name}</option>)}</select></Field>
      <Field label="Категория"><select disabled={!filters.raceId} value={filters.categoryId} onChange={(change) => setFilter('categoryId', change.target.value)}><option value="">Все</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.displayName}</option>)}</select></Field>
      <Field label="Стартовая волна"><select disabled={!filters.raceId} value={filters.clusterId} onChange={(change) => setFilter('clusterId', change.target.value)}><option value="">Все</option>{clusters.map((cluster) => <option key={cluster.id} value={cluster.id}>{cluster.displayName}</option>)}</select></Field>
      <Field label="Статус"><select value={filters.status} onChange={(change) => setFilter('status', change.target.value)}><option value="">Все</option><option value="finished">Финишировал</option><option value="running">На дистанции</option><option value="notstarted">Не стартовал</option><option value="disqualified">Дисквалификация</option><option value="quarantine">Карантин</option></select></Field>
      <Field label="Сортировка"><select value={filters.sort} onChange={(change) => setFilter('sort', change.target.value)}><option value="place">Место</option><option value="bib">Стартовый номер</option><option value="displayName">Имя</option><option value="gunTime">Официальное время</option><option value="chipTime">Чистое время</option></select></Field>
    </div>
    <Loadable loading={loading} error={error} empty={!rows.content.length}>
      <div className="admin-table-wrap"><table className="admin-table"><thead><tr>{mode === 'results' && <th>Место</th>}<th>Стартовый номер</th><th>Участник</th><th>Старт</th><th>Категория</th>{mode === 'participants' && <><th>Исходная категория</th><th>Стартовая волна</th><th>Пол</th></>}<th>Статус</th>{mode === 'results' && <><th>Официальное время</th><th>Чистое время</th></>}<th /></tr></thead><tbody>{rows.content.map((row) => { const race = races.find((candidate) => candidate.id === row.raceId); return <tr key={row.resultId}>{mode === 'results' && <td>{row.place ?? '—'}</td>}<td><strong>{row.bib ?? '—'}</strong></td><td>{row.displayName}</td><td>{race?.name ?? row.raceName}</td><td>{row.category?.name ?? '—'}</td>{mode === 'participants' && <><td>{row.sourceCategory ?? '—'}</td><td>{row.clusterName ?? '—'}</td><td>{formatGender(row.gender)}</td></>}<td><StatusBadge value={row.status} /></td>{mode === 'results' && <><td>{formatDuration(row.gunTimeMs)}</td><td>{formatDuration(row.chipTimeMs)}</td></>}<td><button className="admin-row-link" onClick={() => setSelectedResultId(row.resultId)}>Открыть</button></td></tr> })}</tbody></table></div>
      <AdminPagination page={rows.page} totalPages={rows.totalPages} onChange={(page) => setFilter('page', page)} />
    </Loadable>
    {selectedResultId && <Drawer title={`Результат №${selectedResultId}`} onClose={() => setSelectedResultId(null)}><AdminResultEditor api={api} resultId={selectedResultId} races={races} onSaved={async () => { setSelectedResultId(null); await load() }} /></Drawer>}
  </div>
}
