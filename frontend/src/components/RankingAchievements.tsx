import type { RankingAchievement } from '../api/types'
import {
  activeRankingAchievements,
  genderAchievementClass,
  type RankingPresentationMode,
} from '../utils/rankingPresentation'

interface Props {
  achievements: RankingAchievement[]
  mode: RankingPresentationMode
}

export function RankingAchievements({ achievements, mode }: Props) {
  const activeAchievements = activeRankingAchievements(achievements, mode)
  if (activeAchievements.length === 0) return <span className="ranking-empty">—</span>

  return (
    <span className="ranking-achievements">
      {activeAchievements.map((achievement) => (
        <span
          className={`ranking-achievement ranking-${achievement.type.toLowerCase()} ${genderAchievementClass(achievement)} ${achievement.prize ? 'ranking-prize' : 'ranking-standing'}`}
          key={`${achievement.type}-${achievement.code ?? achievement.name}-${achievement.place}`}
        >
          {achievement.label}
        </span>
      ))}
    </span>
  )
}
