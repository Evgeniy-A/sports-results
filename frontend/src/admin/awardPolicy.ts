import type { AwardPolicy, AwardPolicyUpdate } from './types'

export function withRankingBasis(
  policy: AwardPolicy,
  rankingBasis: AwardPolicy['rankingBasis'],
): AwardPolicy {
  if (rankingBasis !== 'NONE') return { ...policy, rankingBasis }
  return {
    ...policy,
    rankingBasis,
    primaryStandingMode: 'NONE',
    absolutePrizePlaces: 0,
    categoryEnabled: false,
    categoryPrizePlaces: 0,
    excludeAbsoluteWinnersFromCategory: false,
  }
}

export function awardPolicyUpdate(policy: AwardPolicy): AwardPolicyUpdate {
  return {
    rankingBasis: policy.rankingBasis,
    primaryStandingMode: policy.primaryStandingMode,
    absolutePrizePlaces: policy.absolutePrizePlaces,
    categoryEnabled: policy.categoryEnabled,
    ageCalculationMode: policy.ageCalculationMode,
    categoryPrizePlaces: policy.categoryPrizePlaces,
    excludeAbsoluteWinnersFromCategory: policy.excludeAbsoluteWinnersFromCategory,
  }
}
