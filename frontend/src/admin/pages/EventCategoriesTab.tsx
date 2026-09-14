/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { AdminCategory, EventSummary, Race, StartCluster } from '../types'
import { adminErrorMessage } from '../utils'
import { AdminNotice, ConfirmDialog, Drawer, Field, Loadable, StatusBadge } from '../components/AdminUi'

export function CategoriesClustersTab({ api, event, races }: { api: AdminApi; event: EventSummary; races: Race[] }) {
  const [raceId, setRaceId] = useState(races[0]?.id ?? 0)
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [clusters, setClusters] = useState<StartCluster[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [categoryEditor, setCategoryEditor] = useState<AdminCategory | 'new' | null>(null)
  const [clusterEditor, setClusterEditor] = useState<StartCluster | 'new' | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<{ kind: 'category' | 'cluster'; id: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const race = races.find((candidate) => candidate.id === raceId)

  const load = useCallback(async () => {
    if (!raceId) { setLoading(false); return }
    setLoading(true); setError(null)
    try {
      const [categoryValues, clusterValues] = await Promise.all([
        api.categories(raceId), api.clusters(event.id, raceId),
      ])
      setCategories(categoryValues); setClusters(clusterValues)
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, event.id, raceId])
  useEffect(() => { void load() }, [load])

  const remove = async () => {
    if (!deleteTarget) return; setBusy(true); setError(null)
    try {
      if (deleteTarget.kind === 'category') await api.deleteCategory(raceId, deleteTarget.id)
      else await api.deleteCluster(event.id, raceId, deleteTarget.id)
      setDeleteTarget(null); await load()
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  if (!races.length) return <div className="admin-empty">Сначала создайте Race.</div>
  return <div className="admin-stack">
    <div className="admin-section-toolbar"><div><h2>Категории и стартовые кластеры</h2><p>Эти справочники всегда принадлежат конкретному Race.</p></div><Field label="Race"><select value={raceId} onChange={(change) => setRaceId(Number(change.target.value))}>{races.map((item) => <option key={item.id} value={item.id}>{item.sportFormatName} · {item.name}</option>)}</select></Field></div>
    {race?.resultsPublicationStatus === 'PUBLISHED' && <AdminNotice tone="warning">Результаты Race опубликованы. Изменения, влияющие на протокол, требуют сначала вернуть Race в черновик.</AdminNotice>}
    <AdminNotice tone="info">Для участников младше 18 лет на дату мероприятия категория берётся из исходных данных регистрации. Для взрослых применяется настроенный способ расчёта — на дату мероприятия или на конец года.</AdminNotice>
    <Loadable loading={loading} error={error}>
      <section className="admin-card">
        <div className="admin-card-heading"><div><h3>Категории</h3><p>Возрастные границы, пол и source mapping.</p></div><button className="admin-button-primary" onClick={() => setCategoryEditor('new')}>Добавить категорию</button></div>
        {categories.length ? <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Название</th><th>Source name</th><th>Пол</th><th>Возраст</th><th>Порядок</th><th>Состояние</th><th /></tr></thead><tbody>{categories.map((category) => <tr key={category.id}><td><strong>{category.displayName}</strong></td><td>{category.sourceName}</td><td>{category.gender === 'MALE' ? 'М' : category.gender === 'FEMALE' ? 'Ж' : 'Любой'}</td><td>{ageRange(category)}</td><td>{category.displayOrder}</td><td><StatusBadge value={category.enabled ? 'PUBLISHED' : 'DRAFT'} /></td><td><div className="admin-row-actions"><button className="admin-link-button" onClick={() => setCategoryEditor(category)}>Изменить</button><button className="admin-link-button danger" onClick={() => setDeleteTarget({ kind: 'category', id: category.id })}>Удалить</button></div></td></tr>)}</tbody></table></div> : <div className="admin-empty compact">Для этого Race категории не созданы.</div>}
      </section>
      <section className="admin-card">
        <div className="admin-card-heading"><div><h3>StartCluster</h3><p>Исходная группировка старта; кластер не рассчитывается и не ранжирует.</p></div><button className="admin-button-primary" onClick={() => setClusterEditor('new')}>Добавить кластер</button></div>
        {clusters.length ? <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Название</th><th>Код</th><th>Source name</th><th>Время старта</th><th>Порядок</th><th /></tr></thead><tbody>{clusters.map((cluster) => <tr key={cluster.id}><td><strong>{cluster.displayName}</strong></td><td>{cluster.code ?? '—'}</td><td>{cluster.sourceName ?? '—'}</td><td>{cluster.startsAt ? cluster.startsAt.replace('T', ' ').slice(0, 16) : '—'}</td><td>{cluster.displayOrder}</td><td><div className="admin-row-actions"><button className="admin-link-button" onClick={() => setClusterEditor(cluster)}>Изменить</button><button className="admin-link-button danger" onClick={() => setDeleteTarget({ kind: 'cluster', id: cluster.id })}>Удалить</button></div></td></tr>)}</tbody></table></div> : <div className="admin-empty compact">Для этого Race кластеры не созданы.</div>}
      </section>
    </Loadable>
    {categoryEditor && <CategoryDrawer api={api} raceId={raceId} value={categoryEditor === 'new' ? null : categoryEditor} onClose={() => setCategoryEditor(null)} onSaved={async () => { setCategoryEditor(null); await load() }} />}
    {clusterEditor && <ClusterDrawer api={api} eventId={event.id} raceId={raceId} value={clusterEditor === 'new' ? null : clusterEditor} onClose={() => setClusterEditor(null)} onSaved={async () => { setClusterEditor(null); await load() }} />}
    {deleteTarget && <ConfirmDialog title={deleteTarget.kind === 'category' ? 'Удалить категорию?' : 'Удалить кластер?'} description={deleteTarget.kind === 'category' ? 'Категорию нельзя удалить, если она используется участниками или историческими данными.' : 'Кластер нельзя удалить, если он используется регистрациями.'} confirmLabel="Удалить" danger busy={busy} onConfirm={() => void remove()} onClose={() => setDeleteTarget(null)} />}
  </div>
}

function ageRange(category: AdminCategory): string {
  if (category.minAge === null && category.maxAge === null) return 'Любой'
  if (category.minAge === null) return `до ${category.maxAge}`
  if (category.maxAge === null) return `${category.minAge}+`
  return `${category.minAge}–${category.maxAge}`
}

function CategoryDrawer({ api, raceId, value, onClose, onSaved }: { api: AdminApi; raceId: number; value: AdminCategory | null; onClose: () => void; onSaved: () => Promise<void> }) {
  const [form, setForm] = useState({ sourceName: value?.sourceName ?? '', displayName: value?.displayName ?? '', minAge: value?.minAge === null || value?.minAge === undefined ? '' : String(value.minAge), maxAge: value?.maxAge === null || value?.maxAge === undefined ? '' : String(value.maxAge), gender: value?.gender ?? '', displayOrder: value?.displayOrder ?? 0, enabled: value?.enabled ?? true })
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); setError(null); try { const body = { ...form, minAge: form.minAge === '' ? null : Number(form.minAge), maxAge: form.maxAge === '' ? null : Number(form.maxAge), gender: form.gender || null }; if (value) await api.updateCategory(raceId, value.id, body); else await api.createCategory(raceId, body); await onSaved() } catch (reason) { setError(adminErrorMessage(reason)) } finally { setBusy(false) } }
  return <Drawer title={value ? 'Изменить категорию' : 'Новая категория'} onClose={onClose}><form className="admin-form" onSubmit={submit}>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<Field label="Публичное название"><input required value={form.displayName} onChange={(change) => setForm({ ...form, displayName: change.target.value })} /></Field><Field label="Source name"><input required value={form.sourceName} onChange={(change) => setForm({ ...form, sourceName: change.target.value })} /></Field><div className="admin-form-grid"><Field label="Минимальный возраст"><input type="number" min="0" max="150" value={form.minAge} onChange={(change) => setForm({ ...form, minAge: change.target.value })} /></Field><Field label="Максимальный возраст"><input type="number" min="0" max="150" value={form.maxAge} onChange={(change) => setForm({ ...form, maxAge: change.target.value })} /></Field></div><div className="admin-form-grid"><Field label="Пол"><select value={form.gender} onChange={(change) => setForm({ ...form, gender: change.target.value as typeof form.gender })}><option value="">Любой</option><option value="MALE">Мужской</option><option value="FEMALE">Женский</option></select></Field><Field label="Порядок"><input type="number" min="0" value={form.displayOrder} onChange={(change) => setForm({ ...form, displayOrder: Number(change.target.value) })} /></Field></div><label className="admin-check"><input type="checkbox" checked={form.enabled} onChange={(change) => setForm({ ...form, enabled: change.target.checked })} /><span>Категория активна</span></label><div className="admin-form-actions"><button className="admin-button-primary" disabled={busy}>Сохранить</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div></form></Drawer>
}

function ClusterDrawer({ api, eventId, raceId, value, onClose, onSaved }: { api: AdminApi; eventId: number; raceId: number; value: StartCluster | null; onClose: () => void; onSaved: () => Promise<void> }) {
  const [form, setForm] = useState({ code: value?.code ?? '', sourceName: value?.sourceName ?? '', displayName: value?.displayName ?? '', displayOrder: value?.displayOrder ?? 0, startsAt: value?.startsAt?.slice(0, 16) ?? '' })
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); setError(null); try { const body = { ...form, code: form.code || null, sourceName: form.sourceName || null, startsAt: form.startsAt || null }; if (value) await api.updateCluster(eventId, raceId, value.id, body); else await api.createCluster(eventId, raceId, body); await onSaved() } catch (reason) { setError(adminErrorMessage(reason)) } finally { setBusy(false) } }
  return <Drawer title={value ? 'Изменить StartCluster' : 'Новый StartCluster'} onClose={onClose}><form className="admin-form" onSubmit={submit}>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<Field label="Название"><input required value={form.displayName} onChange={(change) => setForm({ ...form, displayName: change.target.value })} /></Field><div className="admin-form-grid"><Field label="Код"><input value={form.code} onChange={(change) => setForm({ ...form, code: change.target.value })} /></Field><Field label="Source name"><input value={form.sourceName} onChange={(change) => setForm({ ...form, sourceName: change.target.value })} /></Field></div><div className="admin-form-grid"><Field label="Время старта"><input type="datetime-local" value={form.startsAt} onChange={(change) => setForm({ ...form, startsAt: change.target.value })} /></Field><Field label="Порядок"><input type="number" min="0" value={form.displayOrder} onChange={(change) => setForm({ ...form, displayOrder: Number(change.target.value) })} /></Field></div><div className="admin-form-actions"><button className="admin-button-primary" disabled={busy}>Сохранить</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div></form></Drawer>
}
