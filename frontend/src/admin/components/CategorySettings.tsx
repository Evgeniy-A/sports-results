import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminCategory, TemplateCategory } from '../types'
import { suggestedCategoryName } from '../categorySettings'
import { adminErrorMessage } from '../utils'
import { AdminNotice, ConfirmDialog, Field } from './AdminUi'

export type CategoryItem = AdminCategory | TemplateCategory

export interface CategoryCommand {
  sourceName: string
  displayName: string
  minAge: number | null
  maxAge: number | null
  gender: 'MALE' | 'FEMALE' | null
  displayOrder: number
  enabled: boolean
  rankingBasisOverride: 'GUN_TIME' | 'CHIP_TIME' | null
}

export function CategorySettings({ categories, defaultRankingBasis, disabled = false, onCreate, onUpdate, onDelete, onReload }: {
  categories: CategoryItem[]
  defaultRankingBasis: 'GUN_TIME' | 'CHIP_TIME'
  disabled?: boolean
  onCreate: (body: CategoryCommand) => Promise<unknown>
  onUpdate: (id: number, body: CategoryCommand) => Promise<unknown>
  onDelete: (id: number) => Promise<unknown>
  onReload: () => Promise<void>
}) {
  const [editor, setEditor] = useState<CategoryItem | 'new' | null>(null)
  const [actionTarget, setActionTarget] = useState<{ category: CategoryItem; action: 'disable' | 'delete' } | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const nextDisplayOrder = useMemo(
    () => categories.reduce((maximum, category) => Math.max(maximum, category.displayOrder), -1) + 1,
    [categories],
  )

  const applyAction = async () => {
    if (!actionTarget) return
    setBusy(true); setError(null)
    try {
      if (actionTarget.action === 'disable') await onUpdate(actionTarget.category.id, command(actionTarget.category, false))
      else await onDelete(actionTarget.category.id)
      setActionTarget(null); await onReload()
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  return <section className="admin-category-settings">
    <div className="admin-card-heading"><div><h4>Возрастные категории</h4><p>Возраст и пол задаёт организатор; сопоставление с исходными значениями остаётся доступно для совместимости.</p></div><button className="admin-button-secondary" type="button" disabled={disabled || busy || editor !== null} onClick={() => setEditor('new')}>+ Добавить категорию</button></div>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    {editor && <CategoryInlineForm
      value={editor === 'new' ? null : editor}
      categories={categories}
      defaultRankingBasis={defaultRankingBasis}
      nextDisplayOrder={nextDisplayOrder}
      onCancel={() => setEditor(null)}
      onSave={async (commands) => {
        if (editor === 'new') {
          for (const body of commands) await onCreate(body)
        } else {
          await onUpdate(editor.id, commands[0])
        }
        setEditor(null); await onReload()
      }}
    />}
    {categories.length ? <div className="admin-category-list">{categories.map((category) => <article key={category.id}>
      <div><strong>{category.displayName}</strong><p>{category.enabled ? 'Активна' : 'Отключена'} · {ageRange(category)} · {genderLabel(category.gender)}</p><p><strong>Время зачёта этой категории:</strong> {categoryBasisLabel(category.rankingBasisOverride, defaultRankingBasis)}</p>{category.gender === null && <small>Legacy: оба пола — единая категория</small>}</div>
      <div className="admin-row-actions"><button className="admin-link-button" type="button" disabled={disabled || busy || editor !== null} onClick={() => setEditor(category)}>Изменить</button>{inUse(category)
        ? category.enabled && <button className="admin-link-button danger" type="button" disabled={disabled || busy} onClick={() => setActionTarget({ category, action: 'disable' })}>Отключить</button>
        : <button className="admin-link-button danger" type="button" disabled={disabled || busy} onClick={() => setActionTarget({ category, action: 'delete' })}>Удалить</button>}</div>
    </article>)}</div> : <div className="admin-empty compact">Возрастные категории пока не настроены.</div>}
    {actionTarget && <ConfirmDialog title={actionTarget.action === 'disable' ? `Отключить категорию «${actionTarget.category.displayName}»?` : `Удалить категорию «${actionTarget.category.displayName}»?`} description={actionTarget.action === 'disable' ? 'Категория перестанет участвовать в текущем пересчёте. Исторические связи сохранятся.' : 'Неиспользуемая категория будет удалена без изменения участников.'} confirmLabel={actionTarget.action === 'disable' ? 'Отключить' : 'Удалить'} danger busy={busy} onConfirm={() => void applyAction()} onClose={() => setActionTarget(null)} />}
  </section>
}

function CategoryInlineForm({ value, categories, defaultRankingBasis, nextDisplayOrder, onCancel, onSave }: {
  value: CategoryItem | null
  categories: CategoryItem[]
  defaultRankingBasis: 'GUN_TIME' | 'CHIP_TIME'
  nextDisplayOrder: number
  onCancel: () => void
  onSave: (commands: CategoryCommand[]) => Promise<void>
}) {
  const [form, setForm] = useState({
    sourceName: value?.sourceName ?? '',
    displayName: value?.displayName ?? '',
    minAge: value?.minAge == null ? '' : String(value.minAge),
    maxAge: value?.maxAge == null ? '' : String(value.maxAge),
    gender: value ? (value.gender ?? 'LEGACY') : 'MALE',
    enabled: value?.enabled ?? true,
    rankingBasisOverride: value?.rankingBasisOverride ?? '',
  })
  const [customName, setCustomName] = useState(Boolean(value))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const updateRange = (key: 'minAge' | 'maxAge', rangeValue: string) => {
    const next = { ...form, [key]: rangeValue }
    if (!customName) next.displayName = suggestedCategoryName(next.minAge, next.maxAge)
    setForm(next)
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null)
    try {
      const displayName = form.displayName.trim() || suggestedCategoryName(form.minAge, form.maxAge)
      const base = {
        displayName,
        minAge: form.minAge === '' ? null : Number(form.minAge),
        maxAge: form.maxAge === '' ? null : Number(form.maxAge),
        displayOrder: value?.displayOrder ?? nextDisplayOrder,
        enabled: form.enabled,
        rankingBasisOverride: form.rankingBasisOverride === '' ? null : form.rankingBasisOverride as 'GUN_TIME' | 'CHIP_TIME',
      }
      if (value) {
        await onSave([{ ...base, sourceName: form.sourceName.trim() || value.sourceName, gender: form.gender === 'LEGACY' ? null : form.gender as 'MALE' | 'FEMALE' }])
      } else {
        const genders = form.gender === 'BOTH' ? ['MALE', 'FEMALE'] as const : [form.gender as 'MALE' | 'FEMALE']
        await onSave(genders.map((gender, index) => ({
          ...base,
          displayName: form.gender === 'BOTH' ? `${displayName} · ${gender === 'MALE' ? 'мужчины' : 'женщины'}` : displayName,
          sourceName: uniqueSourceName(categories, form.sourceName.trim() || displayName, gender),
          displayOrder: nextDisplayOrder + index,
          gender,
        })))
      }
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  return <form className="admin-card admin-form admin-inline-editor" onSubmit={submit}>
    <h4>{value ? 'Изменить возрастную категорию' : 'Новая возрастная категория'}</h4>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    <div className="admin-form-grid"><Field label="Минимальный возраст"><input type="number" min="0" max="150" value={form.minAge} onChange={(event) => updateRange('minAge', event.target.value)} /></Field><Field label="Максимальный возраст" hint="Не указывайте для открытого диапазона"><input type="number" min="0" max="150" value={form.maxAge} onChange={(event) => updateRange('maxAge', event.target.value)} /></Field></div>
    <Field label="Название" hint="Предлагается автоматически; можно изменить"><input required maxLength={255} value={form.displayName} onChange={(event) => { setCustomName(true); setForm({ ...form, displayName: event.target.value }) }} /></Field>
    <Field label="Пол"><select value={form.gender} onChange={(event) => setForm({ ...form, gender: event.target.value })}>{value?.gender === null && <option value="LEGACY">Оба пола — единая категория (legacy)</option>}<option value="MALE">Мужчины</option><option value="FEMALE">Женщины</option>{!value && <option value="BOTH">Создать диапазон для мужчин и женщин</option>}</select></Field>
    <Field label="Время зачёта этой категории"><select value={form.rankingBasisOverride} onChange={(event) => setForm({ ...form, rankingBasisOverride: event.target.value })}><option value="">Как у формата — {basisName(defaultRankingBasis)}</option><option value="GUN_TIME">Gun Time</option><option value="CHIP_TIME">Chip Time</option></select></Field>
    <details className="admin-advanced-settings"><summary>Совместимость с категорией из файла</summary><Field label="Название в файле" hint="Нужно для участников без даты рождения и legacy-импорта"><input required maxLength={255} value={form.sourceName || form.displayName} onChange={(event) => setForm({ ...form, sourceName: event.target.value })} /></Field></details>
    <label className="admin-check"><input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} /><span>Категория активна</span></label>
    <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy || !form.displayName.trim()}>{busy ? 'Сохраняем…' : value ? 'Сохранить' : form.gender === 'BOTH' ? 'Создать две категории' : 'Создать категорию'}</button><button className="admin-button-secondary" type="button" onClick={onCancel}>Отмена</button></div>
  </form>
}

function inUse(category: CategoryItem): category is AdminCategory { return 'inUse' in category && category.inUse }

function command(category: CategoryItem, enabled: boolean): CategoryCommand {
  return { sourceName: category.sourceName, displayName: category.displayName, minAge: category.minAge, maxAge: category.maxAge, gender: category.gender, displayOrder: category.displayOrder, enabled, rankingBasisOverride: category.rankingBasisOverride }
}

function uniqueSourceName(categories: CategoryItem[], requested: string, gender: 'MALE' | 'FEMALE'): string {
  const suffix = gender === 'MALE' ? 'М' : 'Ж'
  const base = requested.trim() || `Категория ${suffix}`
  const proposed = `${base} [${suffix}]`
  const used = new Set(categories.map((category) => category.sourceName.toLocaleLowerCase('ru-RU')))
  if (!used.has(proposed.toLocaleLowerCase('ru-RU'))) return proposed
  let index = 2
  while (used.has(`${proposed} ${index}`.toLocaleLowerCase('ru-RU'))) index += 1
  return `${proposed} ${index}`
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
  return 'оба пола — единая категория'
}

function basisName(basis: 'GUN_TIME' | 'CHIP_TIME'): string { return basis === 'GUN_TIME' ? 'Gun Time' : 'Chip Time' }

function categoryBasisLabel(override: 'GUN_TIME' | 'CHIP_TIME' | null, defaultBasis: 'GUN_TIME' | 'CHIP_TIME'): string {
  return override ? basisName(override) : `Как у формата — ${basisName(defaultBasis)}`
}
