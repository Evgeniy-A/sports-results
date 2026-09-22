import type { AwardPolicyUpdate } from './types'

export function awardStatusLabel(value: AwardPolicyUpdate | null): string {
  if (!value) return 'Награждение не настроено'
  const absolute = value.rankingBasis !== 'NONE' && value.primaryStandingMode !== 'NONE'
  const categories = value.rankingBasis !== 'NONE' && value.categoryEnabled
  if (absolute && categories) return 'Награждение: Абсолют + категории'
  if (absolute) return 'Награждение: Абсолют'
  if (categories) return 'Награждение: Категории'
  return 'Награждение не настроено'
}
