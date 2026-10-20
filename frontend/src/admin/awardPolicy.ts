import type { AwardPolicy, AwardPolicyUpdate } from './types'

export function withRankingBasis(
  policy: AwardPolicy,
  rankingBasis: AwardPolicy['rankingBasis'],
): AwardPolicy {
  if (rankingBasis !== 'NONE') return {
    ...policy,
    rankingBasis,
    categoryRankingBasis: policy.categoryRankingBasis === 'NONE' ? rankingBasis : policy.categoryRankingBasis,
  }
  return {
    ...policy,
    rankingBasis,
    categoryRankingBasis: 'NONE',
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
    categoryRankingBasis: policy.categoryRankingBasis,
    primaryStandingMode: policy.primaryStandingMode,
    absolutePrizePlaces: policy.absolutePrizePlaces,
    categoryEnabled: policy.categoryEnabled,
    ageCalculationMode: policy.ageCalculationMode,
    categoryPrizePlaces: policy.categoryPrizePlaces,
    excludeAbsoluteWinnersFromCategory: policy.excludeAbsoluteWinnersFromCategory,
  }
}

export function awardPolicyChanged(current: AwardPolicy | null, persisted: AwardPolicy | null): boolean {
  if (current === null || persisted === null) return current !== persisted
  return current.rankingBasis !== persisted.rankingBasis
    || current.categoryRankingBasis !== persisted.categoryRankingBasis
    || current.primaryStandingMode !== persisted.primaryStandingMode
    || current.absolutePrizePlaces !== persisted.absolutePrizePlaces
    || current.categoryEnabled !== persisted.categoryEnabled
    || current.ageCalculationMode !== persisted.ageCalculationMode
    || current.categoryPrizePlaces !== persisted.categoryPrizePlaces
    || current.excludeAbsoluteWinnersFromCategory !== persisted.excludeAbsoluteWinnersFromCategory
}
