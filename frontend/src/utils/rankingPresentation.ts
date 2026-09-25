import type { RankingAchievement, RankingBasis } from '../api/types'

export type RankingPresentationMode = 'PRIMARY' | 'CATEGORY'

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
