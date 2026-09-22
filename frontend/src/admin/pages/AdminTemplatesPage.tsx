/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { EventSeries } from '../types'
import { adminErrorMessage } from '../utils'
import { AdminLink } from '../router'
import { AdminNotice, Drawer, Field, Loadable, StatusBadge } from '../components/AdminUi'

export function AdminTemplatesPage({ api }: { api: AdminApi }) {
  const [templates, setTemplates] = useState<EventSeries[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editor, setEditor] = useState<EventSeries | 'new' | null>(null)

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try { setTemplates(await api.eventSeries()) }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api])
  useEffect(() => { void load() }, [load])

  return <>
    <div className="admin-page-heading">
      <div><p className="admin-eyebrow">Основное</p><h1>Шаблоны</h1><p>Шаблоны помогают создавать мероприятия с повторяющимися настройками.</p></div>
      <button className="admin-button-primary" type="button" onClick={() => setEditor('new')}>+ Новый шаблон</button>
    </div>
    <Loadable loading={loading} error={error}>
      {templates.length ? <div className="admin-template-grid">{templates.map((template) => <article className="admin-card" key={template.id}>
        <div className="admin-card-heading"><div><h2>{template.name}</h2><p>{template.startCount} {startWord(template.startCount)} · {template.eventCount} {eventWord(template.eventCount)}</p></div><StatusBadge value={template.active ? 'ACTIVE' : 'INACTIVE'} /></div>
        {template.description && <p>{template.description}</p>}
        <div className="admin-row-actions"><AdminLink className="admin-button-secondary" href={`/admin/templates/${template.id}`}>Открыть</AdminLink><button className="admin-link-button" type="button" onClick={() => setEditor(template)}>Редактировать</button></div>
      </article>)}</div> : <section className="admin-empty">
        <h2>Шаблонов пока нет</h2><p>Шаблон можно использовать повторно при создании мероприятий в разных городах и годах.</p>
        <button className="admin-button-primary" type="button" onClick={() => setEditor('new')}>+ Создать шаблон</button>
      </section>}
    </Loadable>
    {editor && <TemplateDrawer api={api} value={editor === 'new' ? null : editor} onClose={() => setEditor(null)} onSaved={async () => { setEditor(null); await load() }} />}
  </>
}

function TemplateDrawer({ api, value, onClose, onSaved }: {
  api: AdminApi; value: EventSeries | null; onClose: () => void; onSaved: () => Promise<void>
}) {
  const [name, setName] = useState(value?.name ?? '')
  const [description, setDescription] = useState(value?.description ?? '')
  const [active, setActive] = useState(value?.active ?? true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null)
    try {
      const body = { name: name.trim(), description: description.trim() || null, active }
      if (value) await api.updateEventSeries(value.id, body)
      else await api.createEventSeries(body)
      await onSaved()
    } catch (reason) { setError(adminErrorMessage(reason)) } finally { setBusy(false) }
  }
  return <Drawer title={value ? 'Редактировать шаблон' : 'Новый шаблон'} onClose={onClose}><form className="admin-form" onSubmit={submit}>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    <Field label="Название"><input required maxLength={255} value={name} onChange={(event) => setName(event.target.value)} /></Field>
    <Field label="Описание"><textarea value={description} onChange={(event) => setDescription(event.target.value)} /></Field>
    <label className="admin-check"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} /> Использовать при создании мероприятий</label>
    <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy || !name.trim()}>{busy ? 'Сохраняем…' : value ? 'Сохранить' : 'Создать шаблон'}</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div>
  </form></Drawer>
}

function eventWord(count: number): string {
  const mod100 = count % 100
  if (mod100 >= 11 && mod100 <= 14) return 'мероприятий'
  if (count % 10 === 1) return 'мероприятие'
  if (count % 10 >= 2 && count % 10 <= 4) return 'мероприятия'
  return 'мероприятий'
}

function startWord(count: number): string {
  const mod100 = count % 100
  if (mod100 >= 11 && mod100 <= 14) return 'стартов'
  if (count % 10 === 1) return 'старт'
  if (count % 10 >= 2 && count % 10 <= 4) return 'старта'
  return 'стартов'
}
