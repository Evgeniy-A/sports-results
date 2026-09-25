/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { AwardPolicyUpdate, EventSeries, ResultInquiryConfiguration, TemplateStart } from '../types'
import { AdminLink } from '../router'
import { adminErrorMessage } from '../utils'
import { AdminNotice, ConfirmDialog, Drawer, Field, Loadable } from '../components/AdminUi'
import { AwardEditor } from '../components/EventStartComposer'
import { CategorySettings } from '../components/CategorySettings'
import { awardStatusLabel } from '../awardPolicyPresentation'
import { defaultPolicy } from '../eventStartDrafts'
import { ResultInquirySettingsFields } from '../components/ResultInquirySettingsFields'

export function AdminTemplatePage({ api, templateId }: { api: AdminApi; templateId: number }) {
  const [template, setTemplate] = useState<EventSeries | null>(null)
  const [starts, setStarts] = useState<TemplateStart[]>([])
  const [editor, setEditor] = useState<TemplateStart | 'new' | null>(null)
  const [awardEditor, setAwardEditor] = useState<TemplateStart | null>(null)
  const [deleting, setDeleting] = useState<TemplateStart | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const dragged = useRef<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const [templates, loadedStarts] = await Promise.all([api.eventSeries(), api.templateStarts(templateId)])
      setTemplate(templates.find((item) => item.id === templateId) ?? null)
      setStarts(loadedStarts)
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, templateId])
  useEffect(() => { void load() }, [load])

  const persistOrder = async (ordered: TemplateStart[]) => {
    setStarts(ordered); setBusy(true); setError(null)
    try { setStarts(await api.reorderTemplateStarts(templateId, ordered.map((item) => item.id))) }
    catch (reason) { setError(adminErrorMessage(reason)); await load() }
    finally { setBusy(false) }
  }
  const move = (startId: number, direction: -1 | 1) => {
    const index = starts.findIndex((item) => item.id === startId)
    const target = index + direction
    if (index < 0 || target < 0 || target >= starts.length) return
    const next = [...starts]
    const [item] = next.splice(index, 1)
    next.splice(target, 0, item)
    void persistOrder(next)
  }
  const dropBefore = (targetId: number) => {
    const sourceId = dragged.current
    dragged.current = null
    if (!sourceId || sourceId === targetId) return
    const sourceIndex = starts.findIndex((item) => item.id === sourceId)
    const targetIndex = starts.findIndex((item) => item.id === targetId)
    if (sourceIndex < 0 || targetIndex < 0) return
    const next = [...starts]
    const [item] = next.splice(sourceIndex, 1)
    next.splice(targetIndex, 0, item)
    void persistOrder(next)
  }
  const remove = async () => {
    if (!deleting) return
    setBusy(true); setError(null)
    try { await api.deleteTemplateStart(templateId, deleting.id); setDeleting(null); await load() }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  return <>
    <div className="admin-page-heading"><div><AdminLink href="/admin/templates">← Шаблоны</AdminLink><p className="admin-eyebrow">Шаблон</p><h1>{template?.name ?? 'Шаблон'}</h1><p>{template?.description || 'Повторно используемый набор стартов для новых мероприятий.'}</p></div><button className="admin-button-primary" type="button" onClick={() => setEditor('new')}>+ Добавить старт</button></div>
    {error && <AdminNotice tone="danger">{error}</AdminNotice>}
    <Loadable loading={loading} error={template ? null : error || 'Шаблон не найден'}>
      {template && <TemplateInquirySettingsCard key={template.updatedAt} api={api} template={template} onSaved={setTemplate} />}
      <section className="admin-card"><div className="admin-section-toolbar"><div><h2>Старты шаблона</h2><p>Перетащите строки или используйте стрелки. Изменения не затронут уже созданные мероприятия.</p></div></div>
        {starts.length ? <div className="admin-template-starts">{starts.map((start, index) => <article
          className="admin-template-start"
          draggable={!busy}
          key={start.id}
          onDragStart={() => { dragged.current = start.id }}
          onDragOver={(event) => event.preventDefault()}
          onDrop={() => dropBefore(start.id)}
        >
          <div className="admin-start-drag" aria-label="Перетащить старт">≡</div>
          <div><h3>{start.name}</h3><p>Дистанция: {formatDistance(start.distanceMeters)}</p><p><strong>{awardStatusLabel(start.awardPolicy)}</strong>{!start.publicVisible && ' · Скрыт по умолчанию'}</p></div>
          <div className="admin-template-start-actions"><div className="admin-order-buttons"><button type="button" aria-label="Поднять старт" disabled={busy || index === 0} onClick={() => move(start.id, -1)}>↑</button><button type="button" aria-label="Опустить старт" disabled={busy || index === starts.length - 1} onClick={() => move(start.id, 1)}>↓</button></div><button className="admin-link-button" type="button" onClick={() => setEditor(start)}>Редактировать</button><button className="admin-link-button" type="button" onClick={() => setAwardEditor(start)}>{start.awardPolicy ? 'Изменить награждение' : 'Настроить награждение'}</button><button className="admin-link-button danger" type="button" onClick={() => setDeleting(start)}>Удалить</button></div>
        </article>)}</div> : <div className="admin-empty"><h3>Стартов пока нет</h3><p>Добавьте типовой старт, который будет предложен при создании новых мероприятий.</p><button className="admin-button-primary" type="button" onClick={() => setEditor('new')}>+ Добавить старт</button></div>}
      </section>
    </Loadable>
    {editor && <TemplateStartDrawer api={api} templateId={templateId} value={editor === 'new' ? null : editor} onClose={() => setEditor(null)} onSaved={async () => { setEditor(null); await load() }} />}
    {awardEditor && <TemplateAwardDrawer api={api} templateId={templateId} start={awardEditor} onClose={() => setAwardEditor(null)} onSaved={async () => { setAwardEditor(null); await load() }} />}
    {deleting && <ConfirmDialog title="Удалить старт из шаблона?" description="Это не изменит уже созданные мероприятия." confirmLabel="Удалить старт" danger busy={busy} onConfirm={() => void remove()} onClose={() => setDeleting(null)} />}
  </>
}

function TemplateInquirySettingsCard({ api, template, onSaved }: {
  api: AdminApi
  template: EventSeries
  onSaved: (template: EventSeries) => void
}) {
  const [settings, setSettings] = useState<ResultInquiryConfiguration>({ ...template.resultInquiryDefaults })
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setMessage(null)
    try {
      const saved = await api.updateEventSeries(template.id, {
        name: template.name,
        slug: template.slug,
        description: template.description,
        active: template.active,
        resultInquiryDefaults: settings,
      })
      setSettings(saved.resultInquiryDefaults)
      onSaved(saved)
      setMessage({ tone: 'success', text: 'Настройки шаблона сохранены.' })
    } catch (reason) {
      setMessage({ tone: 'danger', text: adminErrorMessage(reason) })
    } finally { setBusy(false) }
  }
  return <section className="admin-card admin-inquiry-settings">
    <div className="admin-card-heading"><div><h2>Обращения по результатам</h2><p>Значения по умолчанию для новых мероприятий.</p></div></div>
    {message && <AdminNotice tone={message.tone}>{message.text}</AdminNotice>}
    <form className="admin-form" onSubmit={submit}>
      <ResultInquirySettingsFields value={settings} onChange={setSettings} enabledLabel="Принимать обращения" />
      <p className="admin-form-help">Эти настройки будут скопированы в новые мероприятия из шаблона. Уже созданные мероприятия не изменятся.</p>
      <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy}>{busy ? 'Сохраняем…' : 'Сохранить настройки'}</button></div>
    </form>
  </section>
}

function TemplateStartDrawer({ api, templateId, value, onClose, onSaved }: {
  api: AdminApi; templateId: number; value: TemplateStart | null; onClose: () => void; onSaved: () => Promise<void>
}) {
  const [name, setName] = useState(value?.name ?? '')
  const [distance, setDistance] = useState(value?.distanceMeters === null || value?.distanceMeters === undefined ? '' : String(value.distanceMeters))
  const [publicVisible, setPublicVisible] = useState(value?.publicVisible ?? true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null)
    try {
      const body = { name: name.trim(), distanceMeters: distance === '' ? null : Number(distance), publicVisible }
      if (value) await api.updateTemplateStart(templateId, value.id, body)
      else await api.createTemplateStart(templateId, body)
      await onSaved()
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  return <Drawer title={value ? 'Редактировать старт шаблона' : 'Добавить старт'} onClose={onClose}><form className="admin-form" onSubmit={submit}>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<Field label="Название"><input required maxLength={255} value={name} onChange={(event) => setName(event.target.value)} /></Field><Field label="Дистанция, м" hint="Необязательно"><input type="number" min="0" step="0.001" value={distance} onChange={(event) => setDistance(event.target.value)} /></Field><label className="admin-check"><input type="checkbox" checked={publicVisible} onChange={(event) => setPublicVisible(event.target.checked)} /><span>Показывать публично</span></label><div className="admin-form-actions"><button className="admin-button-primary" disabled={busy || !name.trim()}>{busy ? 'Сохраняем…' : value ? 'Сохранить' : 'Создать старт'}</button><button className="admin-button-secondary" type="button" onClick={onClose}>Отмена</button></div></form></Drawer>
}

function TemplateAwardDrawer({ api, templateId, start, onClose, onSaved }: {
  api: AdminApi; templateId: number; start: TemplateStart; onClose: () => void; onSaved: () => Promise<void>
}) {
  const [policy, setPolicy] = useState<AwardPolicyUpdate | null>(() => start.awardPolicy ? { ...start.awardPolicy } : defaultPolicy())
  const [categories, setCategories] = useState(start.categories)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const save = async () => {
    if (!policy) return
    setBusy(true); setError(null)
    try { await api.updateTemplateAwardPolicy(templateId, start.id, policy); await onSaved() }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  const remove = async () => {
    setBusy(true); setError(null)
    try { await api.deleteTemplateAwardPolicy(templateId, start.id); await onSaved() }
    catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }
  const reloadCategories = async () => setCategories(await api.templateCategories(templateId, start.id))
  return <Drawer title={`Награждение · ${start.name}`} onClose={onClose}><div className="admin-form">{error && <AdminNotice tone="danger">{error}</AdminNotice>}<AwardEditor value={policy} onChange={setPolicy} expanded categorySettings={<CategorySettings categories={categories} onCreate={(body) => api.createTemplateCategory(templateId, start.id, body)} onUpdate={(categoryId, body) => api.updateTemplateCategory(templateId, start.id, categoryId, body)} onDelete={(categoryId) => api.deleteTemplateCategory(templateId, start.id, categoryId)} onReload={reloadCategories} />} />{policy ? <div className="admin-form-actions"><button className="admin-button-primary" type="button" disabled={busy} onClick={() => void save()}>Сохранить настройки</button>{start.awardPolicy && <button className="admin-button-secondary" type="button" disabled={busy} onClick={() => void remove()}>Удалить настройки</button>}</div> : <div className="admin-form-actions"><button className="admin-button-secondary" type="button" onClick={() => setPolicy(defaultPolicy())}>Настроить заново</button>{start.awardPolicy && <button className="admin-button-primary" type="button" disabled={busy} onClick={() => void remove()}>Подтвердить удаление</button>}</div>}</div></Drawer>
}

function formatDistance(distanceMeters: number | null): string {
  if (distanceMeters === null) return '—'
  if (distanceMeters >= 1000 && distanceMeters % 1000 === 0) return `${distanceMeters / 1000} км`
  return `${distanceMeters} м`
}
