/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { EventDocument, EventInfoBlock, EventScheduleItem } from '../types'
import { adminErrorMessage } from '../utils'
import { AdminNotice, ConfirmDialog, Field, Loadable } from '../components/AdminUi'

type DeleteTarget = { kind: 'block' | 'schedule' | 'document'; id: number; label: string }

export function EventContentManager({ api, eventId }: { api: AdminApi; eventId: number }) {
  const [blocks, setBlocks] = useState<EventInfoBlock[]>([])
  const [schedule, setSchedule] = useState<EventScheduleItem[]>([])
  const [documents, setDocuments] = useState<EventDocument[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setLoading(true); setError(null)
    try {
      const [blockValues, scheduleValues, documentValues] = await Promise.all([
        api.infoBlocks(eventId), api.schedule(eventId), api.documents(eventId),
      ])
      setBlocks(blockValues); setSchedule(scheduleValues); setDocuments(documentValues)
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setLoading(false) }
  }, [api, eventId])
  useEffect(() => { void load() }, [load])

  const remove = async () => {
    if (!deleteTarget) return
    setBusy(true); setError(null)
    try {
      if (deleteTarget.kind === 'block') await api.deleteInfoBlock(eventId, deleteTarget.id)
      if (deleteTarget.kind === 'schedule') await api.deleteScheduleItem(eventId, deleteTarget.id)
      if (deleteTarget.kind === 'document') await api.deleteDocument(eventId, deleteTarget.id)
      setMessage('Элемент удалён.'); setDeleteTarget(null); await load()
    } catch (reason) { setError(adminErrorMessage(reason)) }
    finally { setBusy(false) }
  }

  return <section className="admin-card">
    <div className="admin-card-heading"><div><h2>Контент мероприятия</h2><p>Информационные блоки, расписание и PDF-документы публичной карточки.</p></div></div>
    {message && <AdminNotice tone="success">{message}</AdminNotice>}
    <Loadable loading={loading} error={error} empty={false}>
      <div className="admin-content-grid">
        <ContentSection title="Информационные блоки" empty="Блоков пока нет.">
          {blocks.map((block) => <InfoBlockEditor key={block.id} api={api} eventId={eventId} value={block} onSaved={load} onDelete={() => setDeleteTarget({ kind: 'block', id: block.id, label: block.title })} />)}
          <InfoBlockEditor api={api} eventId={eventId} value={null} onSaved={load} />
        </ContentSection>
        <ContentSection title="Расписание" empty="Пунктов пока нет.">
          {schedule.map((item) => <ScheduleEditor key={item.id} api={api} eventId={eventId} value={item} onSaved={load} onDelete={() => setDeleteTarget({ kind: 'schedule', id: item.id, label: item.title })} />)}
          <ScheduleEditor api={api} eventId={eventId} value={null} onSaved={load} />
        </ContentSection>
        <ContentSection title="Документы PDF" empty="Документов пока нет.">
          {documents.map((document) => <DocumentEditor key={document.id} api={api} eventId={eventId} value={document} onSaved={load} onDelete={() => setDeleteTarget({ kind: 'document', id: document.id, label: document.displayName })} />)}
          <DocumentEditor api={api} eventId={eventId} value={null} onSaved={load} />
        </ContentSection>
      </div>
    </Loadable>
    {deleteTarget && <ConfirmDialog title="Удалить элемент?" description={`«${deleteTarget.label}» будет удалён. Действие попадёт в аудит.`} confirmLabel="Удалить" danger busy={busy} onConfirm={() => void remove()} onClose={() => setDeleteTarget(null)} />}
  </section>
}

function ContentSection({ title, empty, children }: { title: string; empty: string; children: React.ReactNode }) {
  return <section className="admin-content-section"><h3>{title}</h3><div className="admin-content-items">{children || <p>{empty}</p>}</div></section>
}

function InfoBlockEditor({ api, eventId, value, onSaved, onDelete }: { api: AdminApi; eventId: number; value: EventInfoBlock | null; onSaved: () => Promise<void>; onDelete?: () => void }) {
  const [form, setForm] = useState({ title: value?.title ?? '', content: value?.content ?? '', displayOrder: value?.displayOrder ?? 0 })
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); setError(null); try { if (value) await api.updateInfoBlock(eventId, value.id, form); else await api.createInfoBlock(eventId, form); if (!value) setForm({ title: '', content: '', displayOrder: 0 }); await onSaved() } catch (reason) { setError(adminErrorMessage(reason)) } finally { setBusy(false) } }
  return <form className="admin-content-item" onSubmit={submit}><strong>{value ? value.title : 'Новый блок'}</strong>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<Field label="Заголовок"><input required value={form.title} onChange={(change) => setForm({ ...form, title: change.target.value })} /></Field><Field label="Текст"><textarea required value={form.content} onChange={(change) => setForm({ ...form, content: change.target.value })} /></Field><Field label="Порядок"><input type="number" min="0" value={form.displayOrder} onChange={(change) => setForm({ ...form, displayOrder: Number(change.target.value) })} /></Field><div className="admin-form-actions"><button className="admin-button-secondary" disabled={busy}>{value ? 'Сохранить' : 'Добавить'}</button>{value && <button className="admin-link-button danger" type="button" onClick={onDelete}>Удалить</button>}</div></form>
}

function ScheduleEditor({ api, eventId, value, onSaved, onDelete }: { api: AdminApi; eventId: number; value: EventScheduleItem | null; onSaved: () => Promise<void>; onDelete?: () => void }) {
  const [form, setForm] = useState({ startsAt: value?.startsAt?.slice(0, 16) ?? '', endsAt: value?.endsAt?.slice(0, 16) ?? '', title: value?.title ?? '', description: value?.description ?? '', displayOrder: value?.displayOrder ?? 0 })
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); setError(null); const body = { ...form, endsAt: form.endsAt || null, description: form.description || null }; try { if (value) await api.updateScheduleItem(eventId, value.id, body); else await api.createScheduleItem(eventId, body); if (!value) setForm({ startsAt: '', endsAt: '', title: '', description: '', displayOrder: 0 }); await onSaved() } catch (reason) { setError(adminErrorMessage(reason)) } finally { setBusy(false) } }
  return <form className="admin-content-item" onSubmit={submit}><strong>{value ? value.title : 'Новый пункт'}</strong>{error && <AdminNotice tone="danger">{error}</AdminNotice>}<Field label="Название"><input required value={form.title} onChange={(change) => setForm({ ...form, title: change.target.value })} /></Field><div className="admin-form-grid"><Field label="Начало"><input type="datetime-local" required value={form.startsAt} onChange={(change) => setForm({ ...form, startsAt: change.target.value })} /></Field><Field label="Окончание"><input type="datetime-local" value={form.endsAt} onChange={(change) => setForm({ ...form, endsAt: change.target.value })} /></Field></div><Field label="Описание"><textarea value={form.description} onChange={(change) => setForm({ ...form, description: change.target.value })} /></Field><Field label="Порядок"><input type="number" min="0" value={form.displayOrder} onChange={(change) => setForm({ ...form, displayOrder: Number(change.target.value) })} /></Field><div className="admin-form-actions"><button className="admin-button-secondary" disabled={busy}>{value ? 'Сохранить' : 'Добавить'}</button>{value && <button className="admin-link-button danger" type="button" onClick={onDelete}>Удалить</button>}</div></form>
}

function DocumentEditor({ api, eventId, value, onSaved, onDelete }: { api: AdminApi; eventId: number; value: EventDocument | null; onSaved: () => Promise<void>; onDelete?: () => void }) {
  const [form, setForm] = useState({ type: value?.type ?? 'OTHER', displayName: value?.displayName ?? '', displayOrder: value?.displayOrder ?? 0, publicDocument: value?.publicDocument ?? true })
  const [file, setFile] = useState<File | null>(null); const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => { event.preventDefault(); if (!value && !file) { setError('Выберите PDF-файл.'); return } setBusy(true); setError(null); try { if (value) await api.updateDocument(eventId, value.id, form); else await api.uploadDocument(eventId, form, file!); if (!value) { setFile(null); setForm({ type: 'OTHER', displayName: '', displayOrder: 0, publicDocument: true }) } await onSaved() } catch (reason) { setError(adminErrorMessage(reason)) } finally { setBusy(false) } }
  const open = async () => { const tab = window.open('', '_blank'); try { const blob = await api.documentContent(eventId, value!.id); const url = URL.createObjectURL(blob); if (tab) tab.location.href = url; else window.open(url, '_blank'); window.setTimeout(() => URL.revokeObjectURL(url), 60_000) } catch (reason) { tab?.close(); setError(adminErrorMessage(reason)) } }
  return <form className="admin-content-item" onSubmit={submit}><strong>{value ? value.displayName : 'Новый документ'}</strong>{value && <small>{value.originalFilename} · {Math.ceil(value.sizeBytes / 1024)} КБ</small>}{error && <AdminNotice tone="danger">{error}</AdminNotice>}<Field label="Название"><input required value={form.displayName} onChange={(change) => setForm({ ...form, displayName: change.target.value })} /></Field><Field label="Тип"><select value={form.type} onChange={(change) => setForm({ ...form, type: change.target.value as EventDocument['type'] })}><option value="PARTICIPANT_GUIDE">Памятка участника</option><option value="REGULATIONS">Положение</option><option value="COURSE_MAP">Карта трассы</option><option value="PROGRAM">Программа</option><option value="OTHER">Другое</option></select></Field>{!value && <Field label="PDF-файл"><input type="file" accept="application/pdf,.pdf" required onChange={(change) => setFile(change.target.files?.[0] ?? null)} /></Field>}<Field label="Порядок"><input type="number" min="0" value={form.displayOrder} onChange={(change) => setForm({ ...form, displayOrder: Number(change.target.value) })} /></Field><label className="admin-check"><input type="checkbox" checked={form.publicDocument} onChange={(change) => setForm({ ...form, publicDocument: change.target.checked })} /><span>Показывать публично</span></label><div className="admin-form-actions"><button className="admin-button-secondary" disabled={busy}>{value ? 'Сохранить' : 'Загрузить'}</button>{value && <button className="admin-link-button" type="button" onClick={() => void open()}>Открыть</button>}{value && <button className="admin-link-button danger" type="button" onClick={onDelete}>Удалить</button>}</div></form>
}
