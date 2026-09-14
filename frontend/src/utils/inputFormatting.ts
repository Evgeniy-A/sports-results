export type DigitInputFormatter = (value: string) => string

export interface FormattedInputEdit {
  value: string
  caret: number
}

function digitCount(value: string): number {
  return value.replace(/\D/g, '').length
}

function caretAfterDigits(value: string, digits: number): number {
  if (digits === 0) return 0
  let seen = 0
  for (let index = 0; index < value.length; index += 1) {
    if (/\d/.test(value[index])) seen += 1
    if (seen === digits) return index + 1
  }
  return value.length
}

function formatSegments(digits: string, lengths: number[], separators: string[]): string {
  let offset = 0
  let formatted = ''
  lengths.forEach((length, index) => {
    const segment = digits.slice(offset, offset + length)
    if (!segment) return
    if (formatted && separators[index - 1]) formatted += separators[index - 1]
    formatted += segment
    offset += segment.length
  })
  return formatted
}

export function formatDateInput(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 8)
  return formatSegments(digits, [2, 2, 4], ['.', '.'])
}

export function formatDurationInput(value: string): string {
  const digits = value.replace(/\D/g, '')
  const explicitHourDigits = value.match(/^\D*(\d{1,3})\s*:/)?.[1].length
  const hourDigits = explicitHourDigits ?? (digits.length > 9 ? 3 : 2)
  const limitedDigits = digits.slice(0, hourDigits + 7)
  return formatSegments(limitedDigits, [hourDigits, 2, 2, 3], [':', ':', '.'])
}

export function formatInputEdit(
  rawValue: string,
  selectionStart: number,
  formatter: DigitInputFormatter,
): FormattedInputEdit {
  const digitsBeforeCaret = digitCount(rawValue.slice(0, selectionStart))
  const value = formatter(rawValue)
  return { value, caret: caretAfterDigits(value, digitsBeforeCaret) }
}

export function separatorNavigationCaret(
  value: string,
  selectionStart: number,
  selectionEnd: number,
  key: 'Backspace' | 'Delete',
): number | null {
  if (selectionStart !== selectionEnd) return null
  const adjacentIndex = key === 'Backspace' ? selectionStart - 1 : selectionStart
  const adjacent = value[adjacentIndex]
  if (!adjacent || /\d/.test(adjacent)) return null
  return key === 'Backspace' ? adjacentIndex : adjacentIndex + 1
}
