import type { PublicRace, RankingBasis } from '../api/types'

export type PublicEventSection = 'results' | 'info'

export function defaultPublicEventSection(
  resultsPublished: boolean,
): PublicEventSection {
  return resultsPublished ? 'results' : 'info'
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

export function publicResultRaces(races: PublicRace[]): PublicRace[] {
  return races.filter((race) => race.resultsPublished)
}

export function initialRaceSelection(races: PublicRace[]): string {
  return races.length === 1 ? String(races[0].id) : ''
}
