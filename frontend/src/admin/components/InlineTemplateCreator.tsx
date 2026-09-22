import { useState } from 'react'
import type { AdminApi } from '../api'
import type { EventSeries } from '../types'
import { adminErrorMessage } from '../utils'
import { AdminNotice, Field } from './AdminUi'

export function InlineTemplateCreator({ api, initiallyOpen = false, onCreated }: {
  api: AdminApi
  initiallyOpen?: boolean
  onCreated: (template: EventSeries) => void
}) {
  const [open, setOpen] = useState(initiallyOpen)
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const create = async () => {
    if (!name.trim()) { setError('Укажите название шаблона.'); return }
    setBusy(true); setError(null)
    try {
      const created = await api.createEventSeries({ name: name.trim(), description: null, active: true })
      onCreated(created)
      setName('')
      setOpen(false)
    } catch (reason) {
      setError(adminErrorMessage(reason))
    } finally {
      setBusy(false)
    }
  }

  return <>
    <button
      type="button"
      className="admin-link-button admin-create-series-toggle"
      aria-expanded={open}
      onClick={() => { setOpen((current) => !current); setError(null) }}
    >+ Создать новый шаблон</button>
    {open && <section className="admin-inline-create" aria-label="Создание шаблона">
      <Field label="Название шаблона"><input
        maxLength={255}
        value={name}
        onChange={(event) => setName(event.target.value)}
        onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void create() } }}
        placeholder="Например, Гонка Героев"
      /></Field>
      {error && <AdminNotice tone="danger">{error}</AdminNotice>}
      <div className="admin-form-actions">
        <button className="admin-button-secondary" type="button" disabled={busy || !name.trim()} onClick={() => void create()}>{busy ? 'Создаём…' : 'Создать шаблон'}</button>
        <button className="admin-link-button" type="button" disabled={busy} onClick={() => { setOpen(false); setError(null) }}>Отмена</button>
      </div>
    </section>}
  </>
}
