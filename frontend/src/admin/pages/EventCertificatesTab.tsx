/* oxlint-disable react/set-state-in-effect -- remote server state is loaded from effects */
import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminApi } from '../api'
import type { CertificateField, CertificateLayout, CertificateSettings, CertificateTemplate, EventSummary, Race } from '../types'
import { AdminNotice, Field } from '../components/AdminUi'
import { adminErrorMessage } from '../utils'

const FIELD_GROUPS: Array<[string, Array<[CertificateField, string]>]> = [
  ['Время', [['OFFICIAL_TIME', 'Официальное время'], ['GUN_TIME', 'Время от выстрела'], ['CHIP_TIME', 'Чистое время']]],
  ['Места', [['OFFICIAL_PLACE', 'Официальное место'], ['OVERALL_PLACE', 'Абсолютное место'],
    ['GENDER_PLACE', 'Место среди мужчин / женщин'], ['CATEGORY_PLACE', 'Место в категории']]],
  ['Дополнительно', [['CATEGORY_NAME', 'Категория'], ['BIB', 'Стартовый номер']]],
]

export function EventCertificatesTab({ api, event, races }: { api: AdminApi; event: EventSummary; races: Race[] }) {
  const [settings, setSettings] = useState<CertificateSettings | null>(null)
  const [templates, setTemplates] = useState<CertificateTemplate[]>([])
  const [busy, setBusy] = useState<string | null>(null)
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)
  const [upload, setUpload] = useState({ displayName: '', raceIds: [] as number[], file: null as File | null })

  const load = useCallback(async () => {
    try {
      const [nextSettings, nextTemplates] = await Promise.all([
        api.certificateSettings(event.id), api.certificateTemplates(event.id),
      ])
      setSettings(nextSettings); setTemplates(nextTemplates)
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
  }, [api, event.id])
  useEffect(() => { void load() }, [load])

  const saveSettings = async () => {
    if (!settings) return
    setBusy('settings'); setMessage(null)
    try {
      setSettings(await api.updateCertificateSettings(event.id, settings))
      setMessage({ tone: 'success', text: 'Настройки сертификатов сохранены.' })
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(null) }
  }
  const toggleField = (field: CertificateField) => setSettings((current) => current ? ({ ...current,
    allowedFields: current.allowedFields.includes(field)
      ? current.allowedFields.filter((item) => item !== field)
      : [...current.allowedFields, field],
  }) : current)
  const submitUpload = async (submit: FormEvent) => {
    submit.preventDefault(); if (!upload.file) return
    setBusy('upload'); setMessage(null)
    try {
      await api.uploadCertificateTemplate(event.id, {
        displayName: upload.displayName, displayOrder: templates.length, active: true, raceIds: upload.raceIds,
      }, upload.file)
      setUpload({ displayName: '', raceIds: [], file: null }); await load()
      setMessage({ tone: 'success', text: 'Дизайн загружен.' })
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(null) }
  }

  return <div className="admin-stack certificates-admin">
    {message && <AdminNotice tone={message.tone}>{message.text}</AdminNotice>}
    <section className="admin-card">
      <div className="admin-card-heading"><div><h2>Сертификаты участников</h2><p>ФИО, мероприятие, город и дистанция добавляются автоматически.</p></div></div>
      {settings && <div className="admin-form">
        <label className="admin-check"><input type="checkbox" checked={settings.enabled}
          onChange={(change) => setSettings({ ...settings, enabled: change.target.checked })} />
        <span>Разрешить сертификаты</span></label>
        <h3>Какие данные участник сможет добавить</h3>
        <div className="certificate-permission-grid">{FIELD_GROUPS.map(([group, fields]) => <fieldset key={group}>
          <legend>{group}</legend>{fields.map(([field, label]) => <label className="admin-check" key={field}>
            <input type="checkbox" checked={settings.allowedFields.includes(field)} onChange={() => toggleField(field)} />
            <span>{label}</span>
          </label>)}</fieldset>)}</div>
        <div className="admin-form-actions"><button className="admin-button-primary" type="button"
          disabled={busy === 'settings'} onClick={() => void saveSettings()}>Сохранить настройки</button></div>
      </div>}
    </section>

    <section className="admin-card">
      <div className="admin-card-heading"><div><h2>Дизайны</h2><p>PDF используется как фон. Пустой выбор стартов означает все старты мероприятия.</p></div></div>
      <form className="admin-form certificate-upload-form" onSubmit={submitUpload}>
        <Field label="Название дизайна"><input required value={upload.displayName}
          onChange={(change) => setUpload({ ...upload, displayName: change.target.value })} /></Field>
        <Field label="PDF-файл"><input required type="file" accept="application/pdf,.pdf"
          onChange={(change) => setUpload({ ...upload, file: change.target.files?.[0] ?? null })} /></Field>
        <RaceChecks races={races} selected={upload.raceIds}
          onChange={(raceIds) => setUpload({ ...upload, raceIds })} />
        <div className="admin-form-actions"><button className="admin-button-primary" disabled={busy === 'upload'}>
          {busy === 'upload' ? 'Загружаем…' : 'Добавить дизайн'}</button></div>
      </form>
    </section>

    {templates.map((template) => <TemplateEditor key={template.id} api={api} eventId={event.id}
      races={races} value={template} onChanged={load} setMessage={setMessage} />)}
  </div>
}

function RaceChecks({ races, selected, onChange }: { races: Race[]; selected: number[]; onChange: (ids: number[]) => void }) {
  const toggle = (id: number) => onChange(selected.includes(id) ? selected.filter((item) => item !== id) : [...selected, id])
  return <fieldset className="certificate-race-checks"><legend>Старты</legend>
    <p>{selected.length === 0 ? 'Все старты' : 'Только выбранные старты'}</p>
    {races.map((race) => <label className="admin-check" key={race.id}><input type="checkbox"
      checked={selected.includes(race.id)} onChange={() => toggle(race.id)} /><span>{race.name}</span></label>)}
  </fieldset>
}

function TemplateEditor({ api, eventId, races, value, onChanged, setMessage }: {
  api: AdminApi; eventId: number; races: Race[]; value: CertificateTemplate
  onChanged: () => Promise<void>
  setMessage: (value: { tone: 'success' | 'danger'; text: string } | null) => void
}) {
  const [draft, setDraft] = useState(value)
  const [busy, setBusy] = useState(false)
  useEffect(() => setDraft(value), [value])
  const save = async () => {
    setBusy(true); setMessage(null)
    try {
      await api.updateCertificateTemplate(eventId, value.id, {
        displayName: draft.displayName, active: draft.active, displayOrder: draft.displayOrder,
        raceIds: draft.raceIds, layout: draft.layout,
      })
      await onChanged(); setMessage({ tone: 'success', text: `Дизайн «${draft.displayName}» сохранён.` })
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(false) }
  }
  const preview = async () => {
    setBusy(true); setMessage(null)
    try {
      const blob = await api.previewCertificateTemplate(eventId, value.id, ['OFFICIAL_TIME', 'OFFICIAL_PLACE'])
      const url = URL.createObjectURL(blob); window.open(url, '_blank', 'noopener,noreferrer')
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(false) }
  }
  const remove = async () => {
    if (!window.confirm(`Удалить дизайн «${draft.displayName}»?`)) return
    setBusy(true); setMessage(null)
    try { await api.deleteCertificateTemplate(eventId, value.id); await onChanged(); setMessage({ tone: 'success', text: 'Дизайн удалён.' }) }
    catch (reason) { setMessage({ tone: 'danger', text: adminErrorMessage(reason) }) }
    finally { setBusy(false) }
  }
  const layout = (patch: Partial<CertificateLayout>) => setDraft((current) => ({ ...current, layout: { ...current.layout, ...patch } }))
  return <section className="admin-card certificate-template-card">
    <div className="admin-card-heading"><div><h2>{value.displayName}</h2><p>{value.originalFilename} · {Math.ceil(value.sizeBytes / 1024)} КБ</p></div>
      <span className={`admin-badge ${value.active ? 'admin-badge-success' : 'admin-badge-neutral'}`}>{value.active ? 'Активен' : 'Отключён'}</span></div>
    <div className="admin-form">
      <div className="admin-form-grid"><Field label="Название"><input value={draft.displayName}
        onChange={(change) => setDraft({ ...draft, displayName: change.target.value })} /></Field>
      <Field label="Порядок"><input type="number" min="0" value={draft.displayOrder}
        onChange={(change) => setDraft({ ...draft, displayOrder: Number(change.target.value) })} /></Field></div>
      <label className="admin-check"><input type="checkbox" checked={draft.active}
        onChange={(change) => setDraft({ ...draft, active: change.target.checked })} /><span>Активен</span></label>
      <RaceChecks races={races} selected={draft.raceIds} onChange={(raceIds) => setDraft({ ...draft, raceIds })} />
      <details><summary>Размещение текста</summary>
        <p className="admin-help">Координаты задаются в PDF-пунктах от левого нижнего угла.</p>
        <LayoutArea title="Имя участника" prefix="participant" layout={draft.layout} update={layout} />
        <LayoutArea title="Мероприятие, город и старт" prefix="event" layout={draft.layout} update={layout} lineSpacing />
        <LayoutArea title="Выбранные данные" prefix="dynamic" layout={draft.layout} update={layout} lineSpacing maxHeight />
      </details>
      <div className="admin-form-actions"><button type="button" className="admin-button-primary" disabled={busy} onClick={() => void save()}>Сохранить</button>
        <button type="button" className="admin-button-secondary" disabled={busy} onClick={() => void preview()}>Предпросмотр</button>
        <button type="button" className="admin-button-danger-outline" disabled={busy} onClick={() => void remove()}>Удалить</button></div>
    </div>
  </section>
}

function LayoutArea({ title, prefix, layout, update, lineSpacing = false, maxHeight = false }: {
  title: string; prefix: 'participant' | 'event' | 'dynamic'; layout: CertificateLayout
  update: (patch: Partial<CertificateLayout>) => void; lineSpacing?: boolean; maxHeight?: boolean
}) {
  const number = (suffix: string) => layout[`${prefix}${suffix}` as keyof CertificateLayout] as number
  const setNumber = (suffix: string, value: string) => update({ [`${prefix}${suffix}`]: Number(value) } as Partial<CertificateLayout>)
  const alignment = `${prefix}Alignment` as keyof CertificateLayout
  return <fieldset className="certificate-layout-area"><legend>{title}</legend><div className="certificate-layout-grid">
    {['X', 'Y', 'Width', 'FontSize', 'MinFontSize'].map((suffix) => <Field key={suffix} label={suffix}><input type="number" step="0.5"
      value={number(suffix)} onChange={(change) => setNumber(suffix, change.target.value)} /></Field>)}
    {maxHeight && <Field label="MaxHeight"><input type="number" step="0.5" value={layout.dynamicMaxHeight}
      onChange={(change) => update({ dynamicMaxHeight: Number(change.target.value) })} /></Field>}
    {lineSpacing && <Field label="LineSpacing"><input type="number" step="0.5" value={number('LineSpacing')}
      onChange={(change) => setNumber('LineSpacing', change.target.value)} /></Field>}
    <Field label="Выравнивание"><select value={layout[alignment] as string}
      onChange={(change) => update({ [alignment]: change.target.value } as Partial<CertificateLayout>)}>
      <option value="LEFT">Слева</option><option value="CENTER">По центру</option><option value="RIGHT">Справа</option>
    </select></Field>
  </div></fieldset>
}
