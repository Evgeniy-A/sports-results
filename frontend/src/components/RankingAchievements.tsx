import type { RankingAchievement } from '../api/types'

interface Props {
  achievements: RankingAchievement[]
}

export function RankingAchievements({ achievements }: Props) {
  if (achievements.length === 0) return <span className="ranking-empty">—</span>

  return (
    <span className="ranking-achievements">
      {achievements.map((achievement) => (
        <span
          className={`ranking-achievement ranking-${achievement.type.toLowerCase()} ${achievement.prize ? 'ranking-prize' : 'ranking-standing'}`}
          key={`${achievement.type}-${achievement.code ?? achievement.name}-${achievement.place}`}
        >
          {achievement.label}
        </span>
      ))}
    </span>
  )
}
