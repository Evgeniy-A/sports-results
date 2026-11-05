/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { AdminCategory, AwardPolicy, EventSummary, Race, StartCluster } from '../types'
import { adminErrorMessage } from '../utils'
import { AdminNotice, ConfirmDialog, Drawer, Field, Loadable } from '../components/AdminUi'
import { CategorySettings } from '../components/CategorySettings'

export function CategoriesClustersTab({ api, event, races, initialRaceId }: { api: AdminApi; event: EventSummary; races: Race[]; initialRaceId?: number }) {
  const [raceId, setRaceId] = useState(races.some((race) => race.id === initialRaceId) ? initialRaceId! : races[0]?.id ?? 0)
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [policy, setPolicy] = useState<AwardPolicy | null>(null)
  const [clusters, setClusters] = useState<StartCluster[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [clusterEditor, setClusterEditor] = useState<StartCluster | 'new' | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<{ kind: 'cluster'; id: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const race = races.find((candidate) => candidate.id === raceId)

  const load = useCallback(async () => {
    if (!raceId) { setLoading(false); return }
    setLoading(true); setError(null)
    try {
      const [categoryValues, clusterValues, policyValue] = await Promise.all([
        api.categories(raceId), api.clusters(event.id, raceId), api.awardPolicy(raceId),
      ])
      setCategories(categoryValues); setClusters(clusterValues); setPolicy(policyValue)
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, event.id, raceId])
  useEffect(() => { void load() }, [load])

  const remove = async () => {
    if (!deleteTarget) return; setBusy(true); setError(null)
    try {
      await api.deleteCluster(event.id, raceId, deleteTarget.id)
      setDeleteTarget(null); await load()
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  if (!races.length) return <div className="admin-empty">Сначала создайте старт.</div>
  return <div className="admin-stack">
    <div className="admin-section-toolbar"><div><h2>Категории и стартовые волны</h2><p>Эти справочники всегда принадлежат конкретному старту.</p></div><Field label="Старт"><select value={raceId} onChange={(change) => setRaceId(Number(change.target.value))}>{races.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field></div>
    {race?.resultsPublicationStatus === 'PUBLISHED' && <AdminNotice tone="warning">Результаты старта опубликованы. Изменения, влияющие на протокол, требуют сначала вернуть старт в черновик.</AdminNotice>}
    <AdminNotice tone="info">Для участников младше 18 лет на дату мероприятия категория берётся из исходных данных регистрации. Для взрослых применяется настроенный способ расчёта — на дату мероприятия или на конец года.</AdminNotice>
    <Loadable loading={loading} error={error}>
      <section className="admin-card">
        {policy && policy.categoryRankingBasis !== 'NONE'
          ? <CategorySettings categories={categories} defaultRankingBasis={policy.categoryRankingBasis} disabled={race?.resultsPublicationStatus === 'PUBLISHED'} onCreate={(body) => api.createCategory(raceId, body)} onUpdate={(categoryId, body) => api.updateCategory(raceId, categoryId, body)} onDelete={(categoryId) => api.deleteCategory(raceId, categoryId)} onReload={load} />
          : <AdminNotice tone="info">Сначала включите награждение по категориям в разделе «Зачёт и награждение».</AdminNotice>}
      </section>
      <section className="admin-card">
        <div className="admin-card-heading"><div><h3>Стартовые волны</h3><p>Исходная группировка старта; стартовая волна не рассчитывается и не ранжирует.</p></div><button className="admin-button-primary" onClick={() => setClusterEditor('new')}>Добавить стартовую волну</button></div>
        {clusters.length ? <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Название</th><th>Код</th><th>Source name</th><th>Время старта</th><th>Порядок</th><th /></tr></thead><tbody>{clusters.map((cluster) => <tr key={cluster.id}><td><strong>{cluster.displayName}</strong></td><td>{cluster.code ?? '—'}</td><td>{cluster.sourceName ?? '—'}</td><td>{cluster.startsAt ? cluster.startsAt.replace('T', ' ').slice(0, 16) : '—'}</td><td>{cluster.displayOrder}</td><td><div className="admin-row-actions"><button className="admin-link-button" onClick={() => setClusterEditor(cluster)}>Изменить</button><button className="admin-link-button danger" onClick={() => setDeleteTarget({ kind: 'cluster', id: cluster.id })}>Удалить</button></div></td></tr>)}</tbody></table></div> : <div className="admin-empty compact">Для этого старта стартовые волны не созданы.</div>}
      </section>
    </Loadable>
    {clusterEditor && <ClusterDrawer api={api} eventId={event.id} raceId={raceId} value={clusterEditor === 'new' ? null : clusterEditor} onClose={() => setClusterEditor(null)} onSaved={async () => { setClusterEditor(null); await load() }} />}
    {deleteTarget && <ConfirmDialog title="Удалить стартовую волну?" description="Стартовую волну нельзя удалить, если она используется участниками." confirmLabel="Удалить" danger busy={busy} onConfirm={() => void remove()} onClose={() => setDeleteTarget(null)} />}
  </div>
}

function ClusterDrawer({ api, eventId, raceId, value, onClose, onSaved }: { api: AdminApi; eventId: number; raceId: number; value: StartCluster | null; onClose: () => void; onSaved: () => Promise<void> }) {
  const [form, setForm] = useState({ code: value?.code ?? '', sourceName: value?.sourceName ?? '', displayName: value?.displayName ?? '', displayOrder: value?.displayOrder ?? 0, startsAt: value?.startsAt?.slice(0, 16) ?? '' })
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); setError(null); try { const body = { ...form, code: form.code || null, sourceName: form.sourceName || null, startsAt: form.startsAt || null }; if (value) await api.updateCluster(eventId, raceId, value.id, body); else await api.createCluster(eventId, raceId, body); await onSaved() } catch (reason) { setError(adminErrorMessage(reason)) } finally { setBusy(false) } }
  return <Drawer title={value ? 'Изменить стартовую волну' : 'Новая стартовая волна'} onClose={onClose}><form className="admin-form" onSubmit={submit}>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<Field label="Название"><input required value={form.displayName} onChange={(change) => setForm({ ...form, displayName: change.target.value })} /></Field><div className="admin-form-grid"><Field label="Код"><input value={form.code} onChange={(change) => setForm({ ...form, code: change.target.value })} /></Field><Field label="Source name"><input value={form.sourceName} onChange={(change) => setForm({ ...form, sourceName: change.target.value })} /></Field></div><div className="admin-form-grid"><Field label="Время старта"><input type="datetime-local" value={form.startsAt} onChange={(change) => setForm({ ...form, startsAt: change.target.value })} /></Field><Field label="Порядок"><input type="number" min="0" value={form.displayOrder} onChange={(change) => setForm({ ...form, displayOrder: Number(change.target.value) })} /></Field></div><div className="admin-form-actions"><button className="admin-button-primary" disabled={busy}>Сохранить</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div></form></Drawer>
}
