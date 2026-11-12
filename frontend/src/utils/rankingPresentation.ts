import type { RankingAchievement, RankingBasis } from '../api/types'

export type RankingPresentationMode = 'PRIMARY' | 'CATEGORY'

export interface PublicStandingPresentation {
  primaryStandingEnabled: boolean
  categoryStandingSelected: boolean
  showStanding: boolean
  mode: RankingPresentationMode
  title: 'Официальный зачёт' | 'Место в категории'
}

export function publicStandingPresentation(
  primaryStandingMode: 'ALL' | 'BY_GENDER' | 'NONE' | undefined,
  categoryEnabled: boolean,
  categoryId: string,
): PublicStandingPresentation {
  const primaryStandingEnabled = primaryStandingMode === 'ALL' || primaryStandingMode === 'BY_GENDER'
  const categoryStandingSelected = categoryEnabled && categoryId !== ''

  return {
    primaryStandingEnabled,
    categoryStandingSelected,
    showStanding: primaryStandingEnabled || categoryStandingSelected,
    mode: categoryStandingSelected ? 'CATEGORY' : 'PRIMARY',
    title: categoryStandingSelected ? 'Место в категории' : 'Официальный зачёт',
  }
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

export function scoringTimeHeading(basis: RankingBasis, column: 'GUN_TIME' | 'CHIP_TIME'): string {
  if (basis === column) {
    return column === 'GUN_TIME'
      ? '⏱ Зачёт (офиц. время)'
      : '⏱ Зачёт (чистое время)'
  }
  return column === 'GUN_TIME' ? 'Официальное время' : 'Чистое время'
}
