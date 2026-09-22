import { ApiError } from '../api/client'

const ERROR_MESSAGES: Record<string, string> = {
  UNAUTHORIZED: 'Неверное имя пользователя или пароль.',
  FORBIDDEN: 'У этой учётной записи нет доступа к административной части.',
  RACE_RESULTS_MUST_BE_DRAFT: 'Сначала верните результаты старта в черновик.',
  RESULT_RECALCULATION_REQUIRED: 'Сначала выполните и примените пересчёт категорий.',
  RECALC_PREVIEW_STALE: 'Данные или настройки изменились. Повторите проверку изменений.',
  IMPORT_PREVIEW_STALE: 'Данные или настройки изменились. Повторите проверку данных.',
  IMPORT_OPERATION_STALE: 'Данные или настройки изменились. Повторите проверку данных.',
  STALE_EVENT_TEMPLATE: 'Структура мероприятия изменилась после создания этого файла. Скачайте актуальный шаблон результатов.',
  UNSUPPORTED_TEMPLATE_VERSION: 'Версия файла устарела или не поддерживается. Скачайте актуальный шаблон результатов.',
  TEMPLATE_EVENT_MISMATCH: 'Этот шаблон создан для другого мероприятия.',
  RETIRED_REGISTRATION_NOT_MUTABLE: 'Историческую запись нельзя редактировать.',
  CATEGORY_IN_USE: 'Категория используется участниками или историческими данными и не может быть удалена.',
  EVENT_SERIES_NOT_FOUND: 'Шаблон не найден.',
  BULK_EVENT_DUPLICATE: 'Исправьте отмеченные дубли перед созданием.',
  BULK_EVENT_CONFLICT: 'Не удалось создать мероприятия: данные изменились. Выполните Preview заново.',
  EVENT_SLUG_EXISTS: 'Такой адрес мероприятия уже используется.',
  RACE_EXISTS: 'Старт с таким кодом или адресом уже существует.',
  RACE_DELETE_PUBLISHED: 'Опубликованный старт нельзя удалить. Сначала верните результаты в черновик.',
  RACE_DELETE_HAS_RESULTS: 'Старт нельзя удалить: у него уже есть результаты.',
  RACE_DELETE_HAS_REGISTRATIONS: 'Старт нельзя удалить: у него уже есть участники.',
  RACE_DELETE_HAS_DEPENDENCIES: 'Старт нельзя удалить: с ним связаны импорт, настройки или история публикации.',
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
    ACTIVE: 'Активен',
    INACTIVE: 'Неактивен',
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
    READY: 'Готово',
    CHECK: 'Требуется проверка',
    PREVIEWED: 'Готово к применению',
    BLOCKING: 'Есть блокирующие ошибки',
    CONFLICT: 'Конфликт',
    EXISTING_UNCHANGED: 'Без изменений',
    EXISTING_CHANGED: 'Будет обновлён',
    RETIRED: 'Будет перенесён в историю',
    AMBIGUOUS: 'Нужно уточнение',
    DUPLICATE_IN_FILE: 'Дубликат в файле',
    OUT_OF_SCOPE: 'Вне выбранных стартов',
    INVALID: 'Ошибка в строке',
    INSERT: 'Будет добавлен',
    UPDATE: 'Будет обновлён',
    CREATE_RESULT: 'Будет создан результат',
    RETIRE: 'Будет перенесён в историю',
    SKIP: 'Без изменений',
    BLOCKED: 'Заблокировано',
  }
  return labels[value] ?? labels[value.toLowerCase()] ?? value
}
