import { ApiError } from '../api/client'

const ERROR_MESSAGES: Record<string, string> = {
  UNAUTHORIZED: 'Неверное имя пользователя или пароль.',
  FORBIDDEN: 'У этой учётной записи нет доступа к административной части.',
  RACE_RESULTS_MUST_BE_DRAFT: 'Сначала верните результаты старта в черновик.',
  RESULT_RECALCULATION_REQUIRED: 'Сначала выполните и примените пересчёт категорий.',
  RECALC_PREVIEW_STALE: 'Данные или настройки изменились. Выполните Preview заново.',
  IMPORT_PREVIEW_STALE: 'Данные или настройки изменились. Выполните Preview заново.',
  IMPORT_OPERATION_STALE: 'Данные или настройки изменились. Выполните Preview заново.',
  RETIRED_REGISTRATION_NOT_MUTABLE: 'Историческую запись нельзя редактировать.',
  CATEGORY_IN_USE: 'Категория используется участниками или историческими данными и не может быть удалена.',
  SPORT_FORMAT_IN_USE: 'Сначала перенесите старты в другой формат.',
  EVENT_SLUG_EXISTS: 'Такой адрес мероприятия уже используется.',
  RACE_EXISTS: 'Старт с таким кодом или адресом уже существует.',
  DUPLICATE_BIB: 'Номер не уникален. Уточните старт и участника.',
  DUPLICATE_IN_FILE: 'В CSV найдены дублирующиеся строки.',
  AMBIGUOUS: 'Невозможно однозначно сопоставить строку с текущими данными.',
  CONFLICT: 'Данные конфликтуют с текущим состоянием.',
  VALIDATION_FAILED: 'Проверьте заполнение полей формы.',
  MALFORMED_REQUEST: 'Не удалось прочитать отправленные данные.',
  UPLOAD_TOO_LARGE: 'Файл превышает допустимый размер.',
  INTERNAL_ERROR: 'Сервер не смог выполнить операцию. Повторите позже.',
}

export function adminErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const primary = error.code ? ERROR_MESSAGES[error.code] : null
    const violations = error.violations.map(({ field, message }) => `${field}: ${message}`).join('; ')
    return [primary ?? 'Не удалось выполнить операцию.', violations].filter(Boolean).join(' ')
  }
  return error instanceof Error ? error.message : 'Не удалось выполнить операцию.'
}

export function slugify(value: string): string {
  return value
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
}

export function bytesLabel(value: number): string {
  if (value < 1024) return `${value} Б`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} КБ`
  return `${(value / 1024 / 1024).toFixed(1)} МБ`
}

export function statusRussian(value: string | null): string {
  if (!value) return '—'
  const labels: Record<string, string> = {
    DRAFT: 'Черновик',
    PUBLISHED: 'Опубликовано',
    ARCHIVED: 'Архив',
    NEW: 'Новое',
    IN_PROGRESS: 'В работе',
    RESOLVED: 'Решено',
    REJECTED: 'Отклонено',
    CURRENT: 'Текущие',
    ALL: 'Все',
    finished: 'Финишировал',
    notstarted: 'Не стартовал',
    disqualified: 'Дисквалификация',
    quarantine: 'Карантин',
    running: 'На дистанции',
  }
  return labels[value] ?? labels[value.toLowerCase()] ?? value
}
