export const RESULT_TIME_ERROR = 'Некорректное время. Используйте формат ММ:СС или Ч:ММ:СС.'
export const MAX_RESULT_TIME_MS = 2_592_000_000

export type ParsedResultTime =
  | { valid: true; milliseconds: number | null }
  | { valid: false; milliseconds: null; error: string }

export function parseResultTime(rawValue: string): ParsedResultTime {
  const value = rawValue.trim()
  if (!value) return { valid: true, milliseconds: null }

  const hourMatch = value.match(/^(\d+):([0-5]\d):([0-5]\d)(?:\.(\d{1,3}))?$/)
  const minuteMatch = value.match(/^(\d+):([0-5]\d)(?:\.(\d{1,3}))?$/)
  let seconds: number
  let fraction: string | undefined
  if (hourMatch) {
    const hours = Number(hourMatch[1])
    const minutes = Number(hourMatch[2])
    const trailingSeconds = Number(hourMatch[3])
    seconds = hours * 3600 + minutes * 60 + trailingSeconds
    fraction = hourMatch[4]
  } else if (minuteMatch) {
    const minutes = Number(minuteMatch[1])
    const trailingSeconds = Number(minuteMatch[2])
    seconds = minutes * 60 + trailingSeconds
    fraction = minuteMatch[3]
  } else {
    return invalid()
  }

  const milliseconds = seconds * 1000 + normalizeFraction(fraction)
  if (!Number.isSafeInteger(milliseconds) || milliseconds > MAX_RESULT_TIME_MS) return invalid()
  return { valid: true, milliseconds }
}

export function formatResultTime(milliseconds: number | null): string {
  if (milliseconds === null) return ''
  const hours = Math.floor(milliseconds / 3_600_000)
  const minutes = Math.floor((milliseconds % 3_600_000) / 60_000)
  const seconds = Math.floor((milliseconds % 60_000) / 1000)
  const fraction = milliseconds % 1000
  const base = hours > 0
    ? `${hours}:${pad(minutes)}:${pad(seconds)}`
    : `${minutes}:${pad(seconds)}`
  return fraction > 0 ? `${base}.${String(fraction).padStart(3, '0')}` : base
}

function normalizeFraction(value: string | undefined): number {
  if (!value) return 0
  return Number(value.padEnd(3, '0'))
}

function invalid(): ParsedResultTime {
  return { valid: false, milliseconds: null, error: RESULT_TIME_ERROR }
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}
