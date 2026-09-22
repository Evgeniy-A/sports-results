import type { AwardPolicyUpdate, EventStartCommand, TemplateStart } from './types'

export interface EventStartDraft extends EventStartCommand {
  clientId: string
  included: boolean
  origin: 'template' | 'extra'
}

export function draftsFromTemplate(starts: TemplateStart[]): EventStartDraft[] {
  return starts.map((start) => ({
    clientId: `template-${start.id}`,
    templateStartId: start.id,
    included: true,
    origin: 'template',
    name: start.name,
    distanceMeters: start.distanceMeters,
    sourceCode: null,
    publicVisible: start.publicVisible,
    awardPolicy: start.awardPolicy ? { ...start.awardPolicy } : null,
  }))
}

export function startCommands(drafts: EventStartDraft[]): EventStartCommand[] {
  return drafts.filter((draft) => draft.included).map((draft) => ({
    templateStartId: draft.templateStartId,
    name: draft.name.trim(),
    distanceMeters: draft.distanceMeters,
    sourceCode: draft.sourceCode?.trim() || null,
    publicVisible: draft.publicVisible,
    awardPolicy: draft.awardPolicy,
  }))
}

export function defaultPolicy(): AwardPolicyUpdate {
  return {
    rankingBasis: 'CHIP_TIME', primaryStandingMode: 'ALL', absolutePrizePlaces: 0,
    categoryEnabled: false, ageCalculationMode: 'EVENT_DATE', categoryPrizePlaces: 0,
    excludeAbsoluteWinnersFromCategory: false,
  }
}
