import { useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminCategory, TemplateCategory } from '../types'
import { adminErrorMessage } from '../utils'
import { AdminNotice, ConfirmDialog, Drawer, Field } from './AdminUi'

export type CategoryItem = AdminCategory | TemplateCategory

export interface CategoryCommand {
  sourceName: string
  displayName: string
  minAge: number | null
  maxAge: number | null
  gender: 'MALE' | 'FEMALE' | null
  displayOrder: number
  enabled: boolean
}

export function CategorySettings({ categories, disabled = false, onCreate, onUpdate, onDelete, onReload }: {
  categories: CategoryItem[]
  disabled?: boolean
  onCreate: (body: CategoryCommand) => Promise<unknown>
  onUpdate: (id: number, body: CategoryCommand) => Promise<unknown>
  onDelete: (id: number) => Promise<unknown>
  onReload: () => Promise<void>
}) {
  const [editor, setEditor] = useState<CategoryItem | 'new' | null>(null)
  const [deleting, setDeleting] = useState<CategoryItem | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const remove = async () => {
    if (!deleting) return
    setBusy(true); setError(null)
    try { await onDelete(deleting.id); setDeleting(null); await onReload() }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  return <section className="admin-category-settings">
    <div className="admin-card-heading"><div><h4>Возрастные категории</h4><p>Категории принадлежат только этому старту.</p></div><button className="admin-button-secondary" type="button" disabled={disabled || busy} onClick={() => setEditor('new')}>+ Добавить категорию</button></div>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    {categories.length ? <div className="admin-category-list">{categories.map((category) => <article key={category.id}>
      <div><strong>{category.displayName}</strong><p>{ageRange(category)} · {genderLabel(category.gender)} · название в файле: {category.sourceName}</p></div>
      <div className="admin-row-actions"><button className="admin-link-button" type="button" disabled={disabled || busy} onClick={() => setEditor(category)}>Изменить</button><button className="admin-link-button danger" type="button" disabled={disabled || busy} onClick={() => setDeleting(category)}>Удалить</button></div>
    </article>)}</div> : <div className="admin-empty compact">Возрастные категории пока не настроены.</div>}
    {editor && <CategoryDrawer value={editor === 'new' ? null : editor} onClose={() => setEditor(null)} onSave={async (body) => {
      if (editor === 'new') await onCreate(body)
      else await onUpdate(editor.id, body)
      setEditor(null); await onReload()
    }} />}
    {deleting && <ConfirmDialog title={`Удалить категорию «${deleting.displayName}»?`} description="Шаблонные категории не изменяют уже созданные мероприятия. Категорию конкретного старта нельзя удалить, если она используется участниками." confirmLabel="Удалить" danger busy={busy} onConfirm={() => void remove()} onClose={() => setDeleting(null)} />}
  </section>
}

function CategoryDrawer({ value, onClose, onSave }: {
  value: CategoryItem | null
  onClose: () => void
  onSave: (body: CategoryCommand) => Promise<void>
}) {
  const [form, setForm] = useState({
    sourceName: value?.sourceName ?? '',
    displayName: value?.displayName ?? '',
    minAge: value?.minAge === null || value?.minAge === undefined ? '' : String(value.minAge),
    maxAge: value?.maxAge === null || value?.maxAge === undefined ? '' : String(value.maxAge),
    gender: value?.gender ?? '',
    displayOrder: value?.displayOrder ?? 0,
    enabled: value?.enabled ?? true,
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null)
    try {
      await onSave({
        ...form,
        minAge: form.minAge === '' ? null : Number(form.minAge),
        maxAge: form.maxAge === '' ? null : Number(form.maxAge),
        gender: form.gender === '' ? null : form.gender as 'MALE' | 'FEMALE',
      })
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  return <Drawer title={value ? 'Изменить возрастную категорию' : 'Новая возрастная категория'} onClose={onClose}><form className="admin-form" onSubmit={submit}>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    <Field label="Название"><input required maxLength={255} value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} /></Field>
    <Field label="Название в файле" hint="Используется как fallback, если дата рождения отсутствует"><input required maxLength={255} value={form.sourceName} onChange={(event) => setForm({ ...form, sourceName: event.target.value })} /></Field>
    <div className="admin-form-grid"><Field label="Минимальный возраст"><input type="number" min="0" max="150" value={form.minAge} onChange={(event) => setForm({ ...form, minAge: event.target.value })} /></Field><Field label="Максимальный возраст"><input type="number" min="0" max="150" value={form.maxAge} onChange={(event) => setForm({ ...form, maxAge: event.target.value })} /></Field></div>
    <div className="admin-form-grid"><Field label="Пол"><select value={form.gender} onChange={(event) => setForm({ ...form, gender: event.target.value as typeof form.gender })}><option value="">Любой</option><option value="MALE">Мужской</option><option value="FEMALE">Женский</option></select></Field><Field label="Порядок"><input type="number" min="0" value={form.displayOrder} onChange={(event) => setForm({ ...form, displayOrder: Number(event.target.value) })} /></Field></div>
    <label className="admin-check"><input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} /><span>Категория активна</span></label>
    <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy}>{busy ? 'Сохраняем…' : 'Сохранить'}</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div>
  </form></Drawer>
}

function ageRange(category: CategoryItem): string {
  if (category.minAge === null && category.maxAge === null) return 'Любой возраст'
  if (category.minAge === null) return `до ${category.maxAge}`
  if (category.maxAge === null) return `${category.minAge}+`
  return `${category.minAge}–${category.maxAge}`
}

function genderLabel(gender: CategoryItem['gender']): string {
  if (gender === 'MALE') return 'мужчины'
  if (gender === 'FEMALE') return 'женщины'
  return 'любой пол'
}
