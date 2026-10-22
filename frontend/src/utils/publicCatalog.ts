import type { EventPhase } from '../api/types'

export type PublicCatalogMode = 'calendar' | 'results'

export interface PublicCatalogConfig {
  phase: EventPhase
  route: string
  eyebrow: string
  title: string
  copy: string
  sectionEyebrow: string
  sectionTitle: string
  emptyTitle: string
  emptyCopy: string
}

export const PUBLIC_CATALOGS: Record<PublicCatalogMode, PublicCatalogConfig> = {
  calendar: {
    phase: 'UPCOMING',
    route: '/events',
    eyebrow: 'Календарь стартов',
    title: 'Предстоящие мероприятия',
    copy: 'Даты, программа, информация для участников и документы будущих стартов.',
    sectionEyebrow: 'Ближайшие старты',
    sectionTitle: 'Календарь',
    emptyTitle: 'Предстоящих мероприятий не найдено',
    emptyCopy: 'Измените или сбросьте фильтры.',
  },
  results: {
    phase: 'PAST',
    route: '/results',
    eyebrow: 'Официальные результаты',
    title: 'Протоколы завершённых стартов',
    copy: 'Архив мероприятий, опубликованные протоколы и поиск результатов участников.',
    sectionEyebrow: 'Архив протоколов',
    sectionTitle: 'Результаты',
    emptyTitle: 'Завершённых мероприятий не найдено',
    emptyCopy: 'Измените или сбросьте фильтры.',
  },
}

export function publicCatalogMode(pathname: string): PublicCatalogMode | null {
  if (/^\/events\/?$/.test(pathname) || /^\/calendar\/?$/.test(pathname)) return 'calendar'
  if (/^\/results\/?$/.test(pathname)) return 'results'
  return null
}
