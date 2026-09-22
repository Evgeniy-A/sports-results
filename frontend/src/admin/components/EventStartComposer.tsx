import { useRef } from 'react'
import type { ReactNode } from 'react'
import type { AwardPolicyUpdate } from '../types'
import { defaultPolicy } from '../eventStartDrafts'
import type { EventStartDraft } from '../eventStartDrafts'
import { Field } from './AdminUi'
import { awardStatusLabel } from '../awardPolicyPresentation'

let nextExtraId = 1

export function EventStartComposer({ value, onChange }: {
  value: EventStartDraft[]
  onChange: (next: EventStartDraft[]) => void
}) {
  const dragged = useRef<string | null>(null)
  const update = (clientId: string, patch: Partial<EventStartDraft>) => onChange(value.map((item) => (
    item.clientId === clientId ? { ...item, ...patch } : item
  )))
  const move = (clientId: string, direction: -1 | 1) => {
    const index = value.findIndex((item) => item.clientId === clientId)
    const target = index + direction
    if (index < 0 || target < 0 || target >= value.length) return
    const next = [...value]
    const [item] = next.splice(index, 1)
    next.splice(target, 0, item)
    onChange(next)
  }
  const dropBefore = (targetId: string) => {
    const sourceId = dragged.current
    dragged.current = null
    if (!sourceId || sourceId === targetId) return
    const sourceIndex = value.findIndex((item) => item.clientId === sourceId)
    const targetIndex = value.findIndex((item) => item.clientId === targetId)
    if (sourceIndex < 0 || targetIndex < 0) return
    const next = [...value]
    const [item] = next.splice(sourceIndex, 1)
    next.splice(targetIndex, 0, item)
    onChange(next)
  }
  const add = () => onChange([...value, {
    clientId: `extra-${Date.now()}-${nextExtraId++}`,
    templateStartId: null,
    included: true,
    origin: 'extra',
    name: '',
    distanceMeters: null,
    sourceCode: null,
    publicVisible: true,
    awardPolicy: null,
  }])
  const removeExtra = (clientId: string) => onChange(value.filter((item) => item.clientId !== clientId))

  return <section className="admin-start-composer">
    <div className="admin-section-toolbar"><div><h2>Старты мероприятия</h2><p>Изменения применятся только к создаваемому мероприятию.</p></div><button className="admin-button-secondary" type="button" onClick={add}>+ Добавить старт</button></div>
    {value.length ? <div className="admin-start-drafts">{value.map((draft, index) => <article
      className={`admin-start-draft${draft.included ? '' : ' is-excluded'}`}
      draggable
      key={draft.clientId}
      onDragStart={() => { dragged.current = draft.clientId }}
      onDragOver={(event) => event.preventDefault()}
      onDrop={() => dropBefore(draft.clientId)}
    >
      <div className="admin-start-drag" aria-label="Перетащить старт">≡</div>
      <div className="admin-start-draft-body">
        <div className="admin-card-heading"><label className="admin-check"><input type="checkbox" checked={draft.included} onChange={(event) => update(draft.clientId, { included: event.target.checked })} /><span>{draft.origin === 'template' ? 'Включить старт из шаблона' : 'Включить дополнительный старт'}</span></label><div className="admin-order-buttons"><button type="button" aria-label="Поднять старт" disabled={index === 0} onClick={() => move(draft.clientId, -1)}>↑</button><button type="button" aria-label="Опустить старт" disabled={index === value.length - 1} onClick={() => move(draft.clientId, 1)}>↓</button></div></div>
        <div className="admin-form-grid"><Field label="Название"><input required={draft.included} maxLength={255} value={draft.name} onChange={(event) => update(draft.clientId, { name: event.target.value })} /></Field><Field label="Дистанция, м" hint="Необязательно"><input type="number" min="0" step="0.001" value={draft.distanceMeters ?? ''} onChange={(event) => update(draft.clientId, { distanceMeters: event.target.value === '' ? null : Number(event.target.value) })} /></Field></div>
        <label className="admin-check"><input type="checkbox" checked={draft.publicVisible} onChange={(event) => update(draft.clientId, { publicVisible: event.target.checked })} /><span>Показывать публично</span></label>
        <AwardEditor value={draft.awardPolicy} onChange={(awardPolicy) => update(draft.clientId, { awardPolicy })} />
        {draft.origin === 'extra' && <button className="admin-link-button danger" type="button" onClick={() => removeExtra(draft.clientId)}>Удалить дополнительный старт</button>}
      </div>
    </article>)}</div> : <div className="admin-empty">В шаблоне пока нет стартов. Можно добавить старт только для этого мероприятия.</div>}
  </section>
}

export function AwardEditor({ value, onChange, categorySettings, expanded = false, allowRemove = true }: {
  value: AwardPolicyUpdate | null
  onChange: (value: AwardPolicyUpdate | null) => void
  categorySettings?: ReactNode
  expanded?: boolean
  allowRemove?: boolean
}) {
  if (!value) return <div className="admin-award-summary"><span>Награждение не настроено</span><button className="admin-link-button" type="button" onClick={() => onChange(defaultPolicy())}>Настроить награждение</button></div>
  const set = <K extends keyof AwardPolicyUpdate>(key: K, next: AwardPolicyUpdate[K]) => onChange({ ...value, [key]: next })
  const setAbsoluteEnabled = (enabled: boolean) => onChange({
    ...value,
    primaryStandingMode: enabled ? (value.primaryStandingMode === 'NONE' ? 'ALL' : value.primaryStandingMode) : 'NONE',
    absolutePrizePlaces: enabled ? value.absolutePrizePlaces : 0,
    excludeAbsoluteWinnersFromCategory: enabled ? value.excludeAbsoluteWinnersFromCategory : false,
  })
  const setCategoryEnabled = (enabled: boolean) => onChange({
    ...value,
    categoryEnabled: enabled,
    categoryPrizePlaces: enabled ? value.categoryPrizePlaces : 0,
    excludeAbsoluteWinnersFromCategory: enabled ? value.excludeAbsoluteWinnersFromCategory : false,
  })
  const content = <>
    {value.rankingBasis === 'NONE' ? <div className="admin-award-section"><div><h4>Официальный зачёт отключён</h4><p>Это сохранённая техническая конфигурация. Чтобы настроить награждение, сначала включите время зачёта.</p></div><button className="admin-button-secondary" type="button" onClick={() => onChange({ ...value, rankingBasis: 'CHIP_TIME' })}>Включить чистое время</button></div> : <>
      <div className="admin-award-section"><Field label="Время зачёта"><select value={value.rankingBasis} onChange={(event) => set('rankingBasis', event.target.value as 'GUN_TIME' | 'CHIP_TIME')}><option value="GUN_TIME">Официальное время</option><option value="CHIP_TIME">Чистое время</option></select></Field></div>
      <div className="admin-award-section"><label className="admin-check"><input type="checkbox" checked={value.primaryStandingMode !== 'NONE'} onChange={(event) => setAbsoluteEnabled(event.target.checked)} /><span><strong>Награждение — абсолют</strong></span></label>
        {value.primaryStandingMode !== 'NONE' && <div className="admin-award-section-body"><div className="admin-form-grid"><Field label="Формат"><select value={value.primaryStandingMode} onChange={(event) => set('primaryStandingMode', event.target.value as 'ALL' | 'BY_GENDER')}><option value="ALL">Общее</option><option value="BY_GENDER">По полу</option></select></Field><Field label="Призовых мест"><input type="number" min="0" max="1000" value={value.absolutePrizePlaces} onChange={(event) => set('absolutePrizePlaces', Number(event.target.value))} /></Field></div></div>}
      </div>
      <div className="admin-award-section"><label className="admin-check"><input type="checkbox" checked={value.categoryEnabled} onChange={(event) => setCategoryEnabled(event.target.checked)} /><span><strong>Награждение по категориям</strong></span></label>
        {value.categoryEnabled && <div className="admin-award-section-body"><div className="admin-form-grid"><Field label="Возраст считать"><select value={value.ageCalculationMode} onChange={(event) => set('ageCalculationMode', event.target.value as AwardPolicyUpdate['ageCalculationMode'])}><option value="EVENT_DATE">На дату мероприятия</option><option value="END_OF_EVENT_YEAR">На конец года</option></select></Field><Field label="Призовых мест в каждой категории"><input type="number" min="0" max="1000" value={value.categoryPrizePlaces} onChange={(event) => set('categoryPrizePlaces', Number(event.target.value))} /></Field></div><label className="admin-check"><input type="checkbox" disabled={value.primaryStandingMode === 'NONE'} checked={value.excludeAbsoluteWinnersFromCategory} onChange={(event) => set('excludeAbsoluteWinnersFromCategory', event.target.checked)} /><span>Не награждать в возрастных категориях призёров абсолютного награждения</span></label>{categorySettings}</div>}
      </div>
    </>}
    {allowRemove && <button className="admin-link-button danger" type="button" onClick={() => onChange(null)}>Убрать настройки награждения</button>}
  </>
  if (expanded) return <div className="admin-award-editor is-expanded">{content}</div>
  return <details className="admin-award-editor">
    <summary>{awardStatusLabel(value)}</summary>
    {content}
  </details>
}
