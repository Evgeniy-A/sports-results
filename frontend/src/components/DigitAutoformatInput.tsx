import {
  type ChangeEvent,
  type InputHTMLAttributes,
  type KeyboardEvent,
  useRef,
} from 'react'
import {
  type DigitInputFormatter,
  formatInputEdit,
  separatorNavigationCaret,
} from '../utils/inputFormatting'

interface Props extends Omit<InputHTMLAttributes<HTMLInputElement>, 'inputMode' | 'onChange' | 'value'> {
  formatter: DigitInputFormatter
  onValueChange: (value: string) => void
  value: string
}

export function DigitAutoformatInput({
  formatter,
  onValueChange,
  value,
  ...inputProps
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null)

  const restoreCaret = (caret: number) => {
    requestAnimationFrame(() => {
      const input = inputRef.current
      if (input && document.activeElement === input) input.setSelectionRange(caret, caret)
    })
  }

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const rawValue = event.currentTarget.value
    const selectionStart = event.currentTarget.selectionStart ?? rawValue.length
    const edit = formatInputEdit(rawValue, selectionStart, formatter)
    onValueChange(edit.value)
    restoreCaret(edit.caret)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== 'Backspace' && event.key !== 'Delete') return
    const input = event.currentTarget
    const selectionStart = input.selectionStart ?? 0
    const selectionEnd = input.selectionEnd ?? selectionStart
    const caret = separatorNavigationCaret(
      value,
      selectionStart,
      selectionEnd,
      event.key,
    )
    if (caret === null) return
    event.preventDefault()
    input.setSelectionRange(caret, caret)
  }

  return <input
    {...inputProps}
    ref={inputRef}
    inputMode="numeric"
    value={value}
    onChange={handleChange}
    onKeyDown={handleKeyDown}
  />
}
