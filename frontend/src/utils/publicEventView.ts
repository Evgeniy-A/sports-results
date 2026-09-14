import type { PublicSportFormat, RankingBasis } from '../api/types'

export type PublicEventSection = 'results' | 'info'

export function defaultPublicEventSection(
  resultsPublicationStatus: 'DRAFT' | 'PUBLISHED',
): PublicEventSection {
  return resultsPublicationStatus === 'PUBLISHED' ? 'results' : 'info'
}

export interface ProtocolSelection {
  sportFormatId: string
  raceId: string
}

export type PublicResultSort = 'gunTime' | 'chipTime' | 'displayName' | 'bib'

export function defaultPublicResultSort(rankingBasis: RankingBasis | undefined): PublicResultSort {
  return rankingBasis === 'CHIP_TIME' ? 'chipTime' : 'gunTime'
}

export function publicCategoryName(
  categoryEnabled: boolean,
  category: { name: string } | null | undefined,
): string | null {
  return categoryEnabled && category ? category.name : null
}

export function publicResultFormats(formats: PublicSportFormat[]): PublicSportFormat[] {
  return formats
    .map((format) => ({
      ...format,
      races: format.races.filter((race) => race.resultsPublished),
    }))
    .filter((format) => format.races.length > 0)
}

export function initialProtocolSelection(formats: PublicSportFormat[]): ProtocolSelection {
  const publicFormats = formats.filter((format) => format.races.length > 0)
  if (publicFormats.length !== 1) return { sportFormatId: '', raceId: '' }

  return {
    sportFormatId: String(publicFormats[0].id),
    raceId: publicFormats[0].races[0] ? String(publicFormats[0].races[0].id) : '',
  }
}

export function selectionForSportFormat(
  formats: PublicSportFormat[],
  sportFormatId: string,
): ProtocolSelection {
  const format = formats.find((candidate) => String(candidate.id) === sportFormatId)
  return {
    sportFormatId,
    raceId: format?.races[0] ? String(format.races[0].id) : '',
  }
}
