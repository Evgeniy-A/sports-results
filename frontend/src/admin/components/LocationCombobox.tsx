import { useId, useMemo, useRef, useState } from 'react'
import { filterLocations } from '../timeZones'

export function LocationCombobox({ value, required = false, placeholder = 'Например, Казань', onChange }: {
  value: string
  required?: boolean
  placeholder?: string
  onChange: (value: string, suggestedTimeZone?: string) => void
}) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const listId = useId()
  const options = useMemo(() => filterLocations(value).slice(0, 8), [value])
  return <div className="admin-location-combobox" ref={root} onBlur={(event) => {
    if (!root.current?.contains(event.relatedTarget as Node)) setOpen(false)
  }}>
    <input
      role="combobox"
      aria-autocomplete="list"
      aria-controls={listId}
      aria-expanded={open}
      required={required}
      maxLength={255}
      value={value}
      placeholder={placeholder}
      onFocus={() => setOpen(true)}
      onChange={(event) => { onChange(event.target.value); setOpen(true) }}
    />
    {open && value.trim() && <div className="admin-location-options" id={listId} role="listbox" aria-label="Города и места">
      {options.map((option) => <button type="button" role="option" aria-selected={option.name === value} key={`${option.country}-${option.name}`} onMouseDown={(event) => event.preventDefault()} onClick={() => { onChange(option.name, option.timeZone); setOpen(false) }}><strong>{option.name}</strong><span>{option.country}</span></button>)}
      {!options.length && <p>Город не найден в подсказках. Название можно оставить вручную и выбрать часовой пояс отдельно.</p>}
    </div>}
  </div>
}
