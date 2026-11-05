/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { AdminApi } from '../api'
import type { EventSeries } from '../types'
import { adminErrorMessage } from '../utils'
import { AdminLink } from '../router'
import { Loadable, StatusBadge } from '../components/AdminUi'

export function AdminTemplatesPage({ api }: { api: AdminApi }) {
  const [templates, setTemplates] = useState<EventSeries[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try { setTemplates(await api.eventSeries()) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api])
  useEffect(() => { void load() }, [load])

  return <>
    <div className="admin-page-heading">
      <div><p className="admin-eyebrow">Основное</p><h1>Шаблоны</h1><p>Шаблоны помогают создавать мероприятия с повторяющимися форматами и правилами награждения.</p></div>
      <AdminLink className="admin-button-primary" href="/admin/templates/new">+ Новый шаблон</AdminLink>
    </div>
    <Loadable loading={loading} error={error}>
      {templates.length ? <div className="admin-template-grid">{templates.map((template) => <article className="admin-card" key={template.id}>
        <div className="admin-card-heading"><div><h2>{template.name}</h2><p>{template.startCount} {formatWord(template.startCount)} · {template.eventCount} {eventWord(template.eventCount)}</p></div><StatusBadge value={template.active ? 'ACTIVE' : 'INACTIVE'} /></div>
        {template.description && <p>{template.description}</p>}
        {!template.active && <p className="admin-muted">Архивный шаблон не предлагается для новых мероприятий.</p>}
        <div className="admin-row-actions"><AdminLink className="admin-button-secondary" href={`/admin/templates/${template.id}`}>Открыть редактор</AdminLink></div>
      </article>)}</div> : <section className="admin-empty">
        <h2>Шаблонов пока нет</h2><p>Создайте шаблон и последовательно настройте его форматы, награждение и категории.</p>
        <AdminLink className="admin-button-primary" href="/admin/templates/new">+ Создать шаблон</AdminLink>
      </section>}
    </Loadable>
  </>
}

function eventWord(count: number): string {
  const mod100 = count % 100
  if (mod100 >= 11 && mod100 <= 14) return 'мероприятий'
  if (count % 10 === 1) return 'мероприятие'
  if (count % 10 >= 2 && count % 10 <= 4) return 'мероприятия'
  return 'мероприятий'
}

function formatWord(count: number): string {
  const mod100 = count % 100
  if (mod100 >= 11 && mod100 <= 14) return 'форматов'
  if (count % 10 === 1) return 'формат'
  if (count % 10 >= 2 && count % 10 <= 4) return 'формата'
  return 'форматов'
}
