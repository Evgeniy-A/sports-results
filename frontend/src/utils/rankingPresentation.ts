import type { RankingAchievement, RankingBasis } from '../api/types'

export type RankingPresentationMode = 'PRIMARY' | 'CATEGORY'
export type PrimaryStandingMode = 'ALL' | 'BY_GENDER' | 'NONE'

export function hasOfficialStanding(
  primaryStandingMode: PrimaryStandingMode | undefined,
  categorySelected: boolean,
): boolean {
  return categorySelected || primaryStandingMode === 'ALL' || primaryStandingMode === 'BY_GENDER'
}

export function officialStandingHeading(categorySelected: boolean): string {
  return categorySelected ? 'Место в категории' : 'Официальный зачёт'
}

export function activeRankingAchievements(
  achievements: RankingAchievement[],
  mode: RankingPresentationMode,
): RankingAchievement[] {
  return achievements.filter((achievement) => mode === 'CATEGORY'
    ? achievement.type === 'CATEGORY'
    : achievement.type === 'ABSOLUTE' || achievement.type === 'GENDER')
}

export function genderAchievementClass(achievement: RankingAchievement): string {
  if (achievement.type !== 'GENDER') return ''
  if (achievement.code?.toLowerCase() === 'female') return 'ranking-gender-female'
  if (achievement.code?.toLowerCase() === 'male') return 'ranking-gender-male'
  return ''
}

export function scoringTimeHeading(
  basis: RankingBasis,
  column: 'GUN_TIME' | 'CHIP_TIME',
  standingActive = true,
): string {
  if (standingActive && basis === column) {
    return column === 'GUN_TIME'
      ? '⏱ Зачёт (офиц. время)'
      : '⏱ Зачёт (чистое время)'
  }
  return column === 'GUN_TIME' ? 'Официальное время' : 'Чистое время'
}
