/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { AwardPolicyUpdate, EventSeries, TemplateStart } from '../types'
import { AdminLink, navigateAdmin } from '../router'
import { setAdminNavigationBlocker } from '../navigationGuard'
import { adminErrorMessage } from '../utils'
import { AdminNotice, ConfirmDialog, Field, Loadable, StatusBadge } from '../components/AdminUi'
import { AdminCreationSection } from '../components/AdminCreationLayout'
import { AwardEditor } from '../components/EventStartComposer'
import { CategorySettings } from '../components/CategorySettings'
import { awardStatusLabel } from '../awardPolicyPresentation'
import { defaultPolicy } from '../eventStartDrafts'

const LEAVE_TEMPLATE_MESSAGE = 'Есть несохранённые изменения шаблона. Покинуть страницу без сохранения?'

export function AdminTemplatePage({ api, templateId }: { api: AdminApi; templateId: number | 'new' }) {
  const creating = templateId === 'new'
  const [template, setTemplate] = useState<EventSeries | null>(null)
  const [starts, setStarts] = useState<TemplateStart[]>([])
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [active, setActive] = useState(true)
  const [formatEditor, setFormatEditor] = useState<TemplateStart | 'new' | null>(null)
  const [awardEditorId, setAwardEditorId] = useState<number | null>(null)
  const [deletingFormat, setDeletingFormat] = useState<TemplateStart | null>(null)
  const [confirmDeleteTemplate, setConfirmDeleteTemplate] = useState(false)
  const [loading, setLoading] = useState(!creating)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const dragged = useRef<number | null>(null)
  const allowNavigation = useRef(false)

  const load = useCallback(async () => {
    if (creating) return
    setLoading(true); setError(null)
    try {
      const [templates, loadedStarts] = await Promise.all([api.eventSeries(), api.templateStarts(templateId)])
      const loaded = templates.find((item) => item.id === templateId) ?? null
      setTemplate(loaded); setStarts(loadedStarts)
      if (loaded) { setName(loaded.name); setDescription(loaded.description ?? ''); setActive(loaded.active) }
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, creating, templateId])
  useEffect(() => { void load() }, [load])

  const metaDirty = Boolean(template && (
    name !== template.name || description !== (template.description ?? '') || active !== template.active
  )) || Boolean(creating && (name || description))
  useEffect(() => {
    if (!metaDirty) return
    return setAdminNavigationBlocker(() => allowNavigation.current || window.confirm(LEAVE_TEMPLATE_MESSAGE))
  }, [metaDirty])
  useEffect(() => {
    if (!metaDirty) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [metaDirty])

  const saveMeta = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null); setMessage(null)
    try {
      const body = { name: name.trim(), description: description.trim() || null, active }
      if (creating) {
        const created = await api.createEventSeries(body)
        allowNavigation.current = true
        navigateAdmin(`/admin/templates/${created.id}`)
      } else if (template) {
        const saved = await api.updateEventSeries(template.id, { ...body, slug: template.slug })
        setTemplate(saved); setName(saved.name); setDescription(saved.description ?? ''); setActive(saved.active)
        setMessage('Основная информация сохранена.')
      }
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  const changeActive = async (nextActive: boolean) => {
    if (!template) return
    setBusy(true); setError(null); setMessage(null)
    try {
      const saved = await api.updateEventSeries(template.id, {
        name: template.name, slug: template.slug, description: template.description,
        active: nextActive,
      })
      setTemplate(saved); setName(saved.name); setDescription(saved.description ?? ''); setActive(saved.active)
      setMessage(nextActive ? 'Шаблон возвращён в работу.' : 'Шаблон архивирован. Созданные мероприятия не изменены.')
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  const deleteTemplate = async () => {
    if (!template) return
    setBusy(true); setError(null)
    try { await api.deleteEventSeries(template.id); navigateAdmin('/admin/templates') }
    catch (reason) { setError(adminErrorMessage(reason)); setConfirmDeleteTemplate(false) }
    finally { setBusy(false) }
  }

  const persistOrder = async (ordered: TemplateStart[]) => {
    if (creating) return
    setStarts(ordered); setBusy(true); setError(null)
    try { setStarts(await api.reorderTemplateStarts(templateId, ordered.map((item) => item.id))) }
    catch (reason) { setError(adminErrorMessage(reason)); await load() }
    finally { setBusy(false) }
  }
  const move = (startId: number, direction: -1 | 1) => {
    const index = starts.findIndex((item) => item.id === startId)
    const target = index + direction
    if (index < 0 || target < 0 || target >= starts.length) return
    const next = [...starts]; const [item] = next.splice(index, 1); next.splice(target, 0, item)
    void persistOrder(next)
  }
  const dropBefore = (targetId: number) => {
    const sourceId = dragged.current; dragged.current = null
    if (!sourceId || sourceId === targetId) return
    const sourceIndex = starts.findIndex((item) => item.id === sourceId)
    const targetIndex = starts.findIndex((item) => item.id === targetId)
    if (sourceIndex < 0 || targetIndex < 0) return
    const next = [...starts]; const [item] = next.splice(sourceIndex, 1); next.splice(targetIndex, 0, item)
    void persistOrder(next)
  }
  const removeFormat = async () => {
    if (!deletingFormat || creating) return
    setBusy(true); setError(null)
    try { await api.deleteTemplateStart(templateId, deletingFormat.id); setDeletingFormat(null); await load() }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  if (creating) return <TemplateCreation name={name} description={description} active={active}
    setName={setName} setDescription={setDescription} setActive={setActive} busy={busy} error={error} onSubmit={saveMeta} />

  return <>
    <div className="admin-page-heading"><div><AdminLink href="/admin/templates">← Шаблоны</AdminLink><p className="admin-eyebrow">Полноэкранный редактор шаблона</p><h1>{template?.name ?? 'Шаблон'}</h1><p>Основная информация → форматы → награждение → возрастные категории → проверка.</p></div>{template && <StatusBadge value={template.active ? 'ACTIVE' : 'INACTIVE'} />}</div>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    {message && <AdminNotice tone="success">{message}</AdminNotice>}
    <Loadable loading={loading} error={template ? null : error || 'Шаблон не найден'}>
      {template && <div className="admin-template-editor">
        <AdminCreationSection number="01" title="Основная информация" description="Название, описание и доступность шаблона для новых мероприятий.">
          <form className="admin-form" onSubmit={saveMeta}>
            <Field label="Название"><input required maxLength={255} value={name} onChange={(event) => setName(event.target.value)} /></Field>
            <Field label="Описание"><textarea value={description} onChange={(event) => setDescription(event.target.value)} /></Field>
            <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy || !name.trim() || !metaDirty}>{busy ? 'Сохраняем…' : 'Сохранить основное'}</button></div>
          </form>
          <div className="admin-template-lifecycle">
            <div><strong>{template.active ? 'Шаблон используется' : 'Архивный шаблон'}</strong><p>{template.active ? 'Доступен при создании новых мероприятий.' : 'Не предлагается для новых мероприятий. Уже созданные мероприятия остаются публичными и не меняются.'}</p></div>
            {template.active
              ? <button className="admin-button-secondary" type="button" disabled={busy} onClick={() => void changeActive(false)}>Архивировать</button>
              : <button className="admin-button-secondary" type="button" disabled={busy} onClick={() => void changeActive(true)}>Вернуть в работу</button>}
            {template.eventCount === 0 && <button className="admin-button-danger-outline" type="button" disabled={busy} onClick={() => setConfirmDeleteTemplate(true)}>Удалить шаблон</button>}
          </div>
        </AdminCreationSection>

        <AdminCreationSection number="02" title="Форматы" description="Форматы копируются только в новые мероприятия. Дистанцию можно не указывать.">
          <div className="admin-section-toolbar is-compact"><p>Порядок форматов можно менять стрелками или перетаскиванием.</p><button className="admin-button-secondary" type="button" onClick={() => setFormatEditor('new')}>+ Добавить формат</button></div>
          {formatEditor && <TemplateFormatForm api={api} templateId={template.id} value={formatEditor === 'new' ? null : formatEditor} onCancel={() => setFormatEditor(null)} onSaved={async () => { setFormatEditor(null); await load() }} />}
          {starts.length ? <div className="admin-template-starts">{starts.map((start, index) => <article className="admin-template-start admin-template-format-card" draggable={!busy} key={start.id} onDragStart={() => { dragged.current = start.id }} onDragOver={(event) => event.preventDefault()} onDrop={() => dropBefore(start.id)}>
            <div className="admin-start-drag" aria-label="Перетащить формат">≡</div>
            <div><h3>{start.name}</h3><p>Дистанция: {formatDistance(start.distanceMeters)}</p><p><strong>{awardStatusLabel(start.awardPolicy)}</strong></p></div>
            <div className="admin-template-start-actions"><div className="admin-order-buttons"><button type="button" aria-label="Поднять формат" disabled={busy || index === 0} onClick={() => move(start.id, -1)}>↑</button><button type="button" aria-label="Опустить формат" disabled={busy || index === starts.length - 1} onClick={() => move(start.id, 1)}>↓</button></div><button className="admin-link-button" type="button" onClick={() => setFormatEditor(start)}>Настроить формат</button><button className="admin-link-button" type="button" onClick={() => setAwardEditorId(awardEditorId === start.id ? null : start.id)}>{awardEditorId === start.id ? 'Свернуть награждение' : 'Награждение и категории'}</button><button className="admin-link-button danger" type="button" onClick={() => setDeletingFormat(start)}>Удалить</button></div>
            {awardEditorId === start.id && <TemplateAwardSection key={`${start.id}-${start.updatedAt}`} api={api} templateId={template.id} start={start} onReload={load} />}
          </article>)}</div> : <div className="admin-empty"><h3>Форматов пока нет</h3><p>Добавьте первый спортивный формат шаблона.</p><button className="admin-button-primary" type="button" onClick={() => setFormatEditor('new')}>+ Добавить формат</button></div>}
        </AdminCreationSection>

        <AdminCreationSection number="03" title="Проверка" description="Шаблон готов, когда настроены нужные форматы, награждение и категории.">
          <dl className="admin-detail-list"><div><dt>Форматов</dt><dd>{starts.length}</dd></div><div><dt>Мероприятий создано</dt><dd>{template.eventCount}</dd></div><div><dt>Статус</dt><dd>{template.active ? 'Доступен для новых мероприятий' : 'Архивный'}</dd></div></dl>
          <AdminNotice tone="info">Изменения шаблона не меняют уже созданные мероприятия, их форматы, AwardPolicy или категории.</AdminNotice>
        </AdminCreationSection>
      </div>}
    </Loadable>
    {deletingFormat && <ConfirmDialog title="Удалить формат из шаблона?" description="Это не изменит уже созданные мероприятия." confirmLabel="Удалить формат" danger busy={busy} onConfirm={() => void removeFormat()} onClose={() => setDeletingFormat(null)} />}
    {confirmDeleteTemplate && <ConfirmDialog title="Удалить неиспользуемый шаблон?" description="Шаблон и его вложенные настройки будут удалены. Мероприятий из него не создавалось." confirmLabel="Удалить шаблон" danger busy={busy} onConfirm={() => void deleteTemplate()} onClose={() => setConfirmDeleteTemplate(false)} />}
  </>
}

function TemplateCreation({ name, description, active, setName, setDescription, setActive, busy, error, onSubmit }: {
  name: string; description: string; active: boolean
  setName: (value: string) => void; setDescription: (value: string) => void; setActive: (value: boolean) => void
  busy: boolean; error: string | null; onSubmit: (event: FormEvent) => void
}) {
  return <><div className="admin-page-heading"><div><AdminLink href="/admin/templates">← Шаблоны</AdminLink><p className="admin-eyebrow">Новый шаблон</p><h1>Создание шаблона</h1><p>Сначала сохраните основную информацию, затем на этой же полноценной странице настройте форматы.</p></div></div>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    <AdminCreationSection number="01" title="Основная информация" description="После создания редактор продолжит работу без возврата в список.">
      <form className="admin-form" onSubmit={onSubmit}><Field label="Название"><input autoFocus required maxLength={255} value={name} onChange={(event) => setName(event.target.value)} /></Field><Field label="Описание"><textarea value={description} onChange={(event) => setDescription(event.target.value)} /></Field><label className="admin-check"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} /><span>Использовать при создании мероприятий</span></label><div className="admin-form-actions"><button className="admin-button-primary" disabled={busy || !name.trim()}>{busy ? 'Создаём…' : 'Создать и продолжить'}</button><AdminLink className="admin-button-secondary" href="/admin/templates">Отмена</AdminLink></div></form>
    </AdminCreationSection></>
}

function TemplateFormatForm({ api, templateId, value, onCancel, onSaved }: {
  api: AdminApi; templateId: number; value: TemplateStart | null; onCancel: () => void; onSaved: () => Promise<void>
}) {
  const [name, setName] = useState(value?.name ?? '')
  const [distance, setDistance] = useState(value?.distanceMeters == null ? '' : String(value.distanceMeters))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null)
    try {
      const body = { name: name.trim(), distanceMeters: distance === '' ? null : Number(distance), publicVisible: value?.publicVisible ?? true }
      if (value) await api.updateTemplateStart(templateId, value.id, body)
      else await api.createTemplateStart(templateId, body)
      await onSaved()
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  return <form className="admin-card admin-form admin-inline-editor" onSubmit={submit}><h3>{value ? 'Настроить формат' : 'Новый формат'}</h3>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<div className="admin-form-grid"><Field label="Название"><input required maxLength={255} value={name} onChange={(event) => setName(event.target.value)} /></Field><Field label="Дистанция, м" hint="Необязательно"><input type="number" min="0" step="0.001" value={distance} onChange={(event) => setDistance(event.target.value)} /></Field></div><p className="admin-form-help">Публичная видимость настраивается отдельно в каждом созданном мероприятии.</p><div className="admin-form-actions"><button className="admin-button-primary" disabled={busy || !name.trim()}>{busy ? 'Сохраняем…' : 'Сохранить формат'}</button><button className="admin-button-secondary" type="button" onClick={onCancel}>Отмена</button></div></form>
}

function TemplateAwardSection({ api, templateId, start, onReload }: {
  api: AdminApi; templateId: number; start: TemplateStart; onReload: () => Promise<void>
}) {
  const [policy, setPolicy] = useState<AwardPolicyUpdate | null>(() => start.awardPolicy ? { ...start.awardPolicy } : defaultPolicy())
  const [categories, setCategories] = useState(start.categories)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const savedPolicy = useMemo(() => start.awardPolicy ? JSON.stringify(start.awardPolicy) : null, [start.awardPolicy])
  const dirty = JSON.stringify(policy) !== savedPolicy
  const save = async () => {
    if (!policy) return
    setBusy(true); setError(null)
    try { await api.updateTemplateAwardPolicy(templateId, start.id, policy); await onReload() }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  const remove = async () => {
    setBusy(true); setError(null)
    try { await api.deleteTemplateAwardPolicy(templateId, start.id); setPolicy(null); await onReload() }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  const reloadCategories = async () => setCategories(await api.templateCategories(templateId, start.id))
  return <div className="admin-template-award-inline"><h3>Зачёт и награждение · {start.name}</h3>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<AwardEditor value={policy} onChange={setPolicy} expanded allowRemove={false} categorySettings={policy && policy.categoryRankingBasis !== 'NONE' ? <CategorySettings categories={categories} defaultRankingBasis={policy.categoryRankingBasis} onCreate={(body) => api.createTemplateCategory(templateId, start.id, body)} onUpdate={(categoryId, body) => api.updateTemplateCategory(templateId, start.id, categoryId, body)} onDelete={(categoryId) => api.deleteTemplateCategory(templateId, start.id, categoryId)} onReload={reloadCategories} /> : undefined} /><div className="admin-form-actions">{policy ? <button className="admin-button-primary" type="button" disabled={busy || !dirty} onClick={() => void save()}>{busy ? 'Сохраняем…' : 'Сохранить награждение'}</button> : <button className="admin-button-secondary" type="button" onClick={() => setPolicy(defaultPolicy())}>Настроить заново</button>}{start.awardPolicy && <button className="admin-button-secondary" type="button" disabled={busy} onClick={() => void remove()}>Удалить настройки</button>}</div></div>
}

function formatDistance(distanceMeters: number | null): string {
  if (distanceMeters === null) return 'не указана'
  if (distanceMeters >= 1000 && distanceMeters % 1000 === 0) return `${distanceMeters / 1000} км`
  return `${distanceMeters} м`
}
