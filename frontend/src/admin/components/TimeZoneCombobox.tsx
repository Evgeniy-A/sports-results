import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { filterTimeZones, suggestTimeZone, timeZoneLabel } from '../timeZones'

interface Props {
  value: string
  location?: string
  disabled?: boolean
  onChange: (value: string) => void
}

export function TimeZoneCombobox({ value, location = '', disabled = false, onChange }: Props) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const root = useRef<HTMLDivElement>(null)
  const listId = useId()
  const options = useMemo(() => filterTimeZones(query, value), [query, value])
  const suggestedValue = suggestTimeZone(location)

  useEffect(() => {
    const closeOutside = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', closeOutside)
    return () => document.removeEventListener('pointerdown', closeOutside)
  }, [])

  const select = (nextValue: string) => {
    onChange(nextValue)
    setQuery('')
    setOpen(false)
  }

  return <div className="admin-timezone" ref={root} onKeyDown={(event) => {
    if (event.key === 'Escape') { setOpen(false); setQuery('') }
  }}>
    <button
      type="button"
      className="admin-timezone-trigger"
      role="combobox"
      aria-controls={listId}
      aria-expanded={open}
      aria-haspopup="listbox"
      disabled={disabled}
      onClick={() => setOpen((current) => !current)}
    >
      <span>{timeZoneLabel(value)}</span><span aria-hidden="true">⌄</span>
    </button>
    {open && <div className="admin-timezone-popover">
      <input
        type="search"
        aria-label="Поиск часового пояса"
        autoFocus
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Найти город или регион"
      />
      <div className="admin-timezone-options" id={listId} role="listbox" aria-label="Часовые пояса">
        {options.map((option) => <button
          type="button"
          role="option"
          aria-selected={option.value === value}
          key={option.value}
          onClick={() => select(option.value)}
        >{option.label}</button>)}
        {!options.length && <p>Ничего не найдено. Попробуйте название ближайшего крупного города.</p>}
      </div>
    </div>}
    {suggestedValue && suggestedValue !== value && <button
      className="admin-timezone-suggestion"
      type="button"
      onClick={() => select(suggestedValue)}
    >Для указанного места подходит: {timeZoneLabel(suggestedValue)}. Использовать</button>}
  </div>
}
