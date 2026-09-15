export type PublicationStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type RankingBasis = 'CHIP_TIME' | 'GUN_TIME' | 'NONE'
export type EventPhase = 'UPCOMING' | 'ONGOING' | 'PAST'

export interface EventSummary {
  id: number
  eventSeriesId: number
  eventSeriesName: string
  eventSeriesSlug: string
  name: string
  slug: string
  startsAt: string | null
  endsAt: string | null
  location: string | null
  timeZone: string
  publicationStatus: PublicationStatus
  resultsPublicationStatus: 'DRAFT' | 'PUBLISHED'
  phase: EventPhase
  shortDescription: string | null
  venueName: string | null
  resultsPublished: boolean
}

export interface EventSeriesOption { id: number; name: string }

export interface EventFilterOptions {
  years: number[]
  eventSeries: EventSeriesOption[]
  cities: string[]
}

export interface EventQuery {
  year?: number
  eventSeriesId?: number
  city?: string
  date?: string
  phase?: EventPhase
  page?: number
  size?: number
}

export interface Race {
  id: number
  eventId: number
  sportFormatId: number
  sportFormatName: string
  sourceCode: string
  name: string
  slug: string
  distanceMeters: number | null
  startsAt: string | null
  entryMode: 'INDIVIDUAL' | 'TEAM' | 'MIXED' | 'UNKNOWN'
  displayOrder: number
  publicRankingBasis: RankingBasis
  categoryStandingEnabled: boolean
  publicVisible: boolean
  resultsPublicationStatus: 'DRAFT' | 'PUBLISHED'
  resultsPublished: boolean
  resultRecalculationRequired: boolean
  effectiveName: string
  effectivePublicVisible: boolean
}

export interface StartCluster {
  id: number
  raceId: number
  code: string | null
  sourceName: string | null
  displayName: string
  displayOrder: number
  startsAt: string | null
}

export interface CategoryRule {
  id: number
  name: string
  minAge: number | null
  maxAge: number | null
  gender: 'MALE' | 'FEMALE' | null
  displayOrder: number
}

export interface RaceRules {
  rankingBasis: RankingBasis
  primaryStandingMode: 'ALL' | 'BY_GENDER' | 'NONE'
  absolutePrizePlaces: number
  categoryEnabled: boolean
  categoryPrizePlaces: number | null
  excludeAbsoluteWinnersFromCategory: boolean | null
  ageCalculationMode: 'EVENT_DATE' | 'END_OF_EVENT_YEAR' | null
  categories: CategoryRule[]
}

export interface PublicRaceDetails {
  id: number
  name: string
  slug: string
  distanceMeters: number | null
  startsAt: string | null
  entryMode: Race['entryMode']
  displayOrder: number
  clusters: StartCluster[]
  rules: RaceRules | null
  resultsPublicationStatus: 'DRAFT' | 'PUBLISHED'
  resultsPublished: boolean
}

export interface PublicSportFormat {
  id: number
  code: string | null
  sourceName: string | null
  displayName: string
  displayOrder: number
  races: PublicRaceDetails[]
}

export interface PublicRace {
  id: number
  name: string
  slug: string
  distanceMeters: number | null
  startsAt: string | null
  displayOrder: number
  publicVisible: boolean
  clusters: StartCluster[]
  rules: RaceRules | null
  resultsPublicationStatus: 'DRAFT' | 'PUBLISHED'
  resultsPublished: boolean
}

export interface EventParticipantInfo {
  id: number
  shortDescription: string | null
  venueName: string | null
  venueAddress: string | null
  locationDescription: string | null
  latitude: number | null
  longitude: number | null
  additionalInfo: string | null
}

export interface EventScheduleItem {
  id: number
  startsAt: string
  endsAt: string | null
  title: string
  description: string | null
  displayOrder: number
}

export interface EventInfoBlock { id: number; title: string; content: string; displayOrder: number }

export interface EventDocument {
  id: number
  type: 'PARTICIPANT_GUIDE' | 'REGULATIONS' | 'COURSE_MAP' | 'PROGRAM' | 'OTHER'
  displayName: string
  originalFilename: string
  contentType: string
  sizeBytes: number
  sha256: string
  displayOrder: number
  publicDocument: boolean
  uploadedAt: string
  contentUrl: string
}

export interface EventDetails extends Omit<EventSummary, 'shortDescription' | 'venueName'> {
  participantInfo: EventParticipantInfo | null
  schedule: EventScheduleItem[]
  infoBlocks: EventInfoBlock[]
  documents: EventDocument[]
  sportFormats: PublicSportFormat[]
  races: PublicRace[]
}

export interface Category {
  id: number
  raceId?: number
  raceName?: string
  name: string
}

export type RankingAchievementType = 'ABSOLUTE' | 'CATEGORY' | 'GENDER'

export interface RankingAchievement {
  place: number
  type: RankingAchievementType
  code: string | null
  name: string
  label: string
  prize: boolean
}

export interface ResultListItem {
  registrationId: number
  resultId: number
  raceId: number
  raceName: string
  displayName: string
  firstName: string | null
  lastName: string | null
  bib: string | null
  gender: string | null
  sourceCategory: string | null
  clusterId: number | null
  clusterName: string | null
  entryKind: 'PERSON' | 'TEAM' | 'OTHER' | 'UNKNOWN' | null
  category: Category | null
  status: string
  gunTimeMs: number | null
  chipTimeMs: number | null
  overallPlace: number | null
  genderPlace: number | null
  categoryPlace: number | null
  netOverallPlace: number | null
  netGenderPlace: number | null
  netCategoryPlace: number | null
  place: number | null
  displayPosition: number | null
  rankingBasis: RankingBasis
  rankingAchievements: RankingAchievement[]
}

export interface Split {
  checkpointId: number
  checkpointName: string
  elapsedTimeMs: number | null
  recordedAt: string | null
}

export interface ResultDetails extends Omit<ResultListItem, 'place' | 'displayPosition' | 'entryKind'> {
  eventId: number
  entryKind: 'PERSON' | 'TEAM' | 'OTHER' | 'UNKNOWN'
  splits: Split[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  sort: string
  direction: string
}

export interface ResultQuery {
  raceId: number
  name?: string
  bib?: string
  gender?: string
  categoryId?: number
  clusterId?: number
  status?: string
  page?: number
  size?: number
  sort?: string
  direction?: 'asc' | 'desc'
}

export type ResultInquiryLookupState =
  | 'NOT_FOUND'
  | 'NEEDS_VERIFICATION'
  | 'VERIFICATION_FAILED'
  | 'RESULT_PUBLIC'
  | 'RESULT_NOT_PUBLIC'

export type ResultInquiryAvailability = 'DISABLED' | 'NOT_OPEN_YET' | 'OPEN' | 'CLOSED'

export interface ActiveResultIssue {
  issueId: number
}

export interface ResultInquiryLookup {
  lookupState: ResultInquiryLookupState
  inquiryAvailability: ResultInquiryAvailability
  bib: string
  participantDisplayName?: string
  raceId?: number
  raceDisplayName?: string
  sportFormatDisplayName?: string
  startDisplayName?: string
  publicResultId?: number
  missingResultActionAvailable: boolean
  deadline?: string
  contactEmail?: string
  activeIssue?: ActiveResultIssue
}

export interface VerifyResultInquiryRequest {
  bib: string
  birthDate: string
}

export type ResultIssueType = 'MISSING_RESULT' | 'RESULT_CORRECTION'
export type ResultIssueStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED'
export type ResultCorrectionReason =
  | 'OFFICIAL_TIME'
  | 'CHIP_TIME'
  | 'RESULT_STATUS'
  | 'PARTICIPANT_DATA'
  | 'RACE_OR_FORMAT'
  | 'OTHER'

export interface CreateMissingResultIssueRequest {
  bib: string
  birthDate: string
  contactEmail: string
  message: string
  estimatedStartAt: string | null
  estimatedFinishAt: string | null
}

export interface CreateResultCorrectionIssueRequest {
  birthDate: string
  correctionReason: Exclude<ResultCorrectionReason, 'PARTICIPANT_DATA'>
  claimedGunTimeMs: number | null
  claimedChipTimeMs: number | null
  contactEmail: string
  message: string
}

export interface ResultIssueCreated {
  issueId: number
  type: ResultIssueType
  status: ResultIssueStatus
  createdAt: string
  attachmentUploadToken: string
  attachmentUploadTokenExpiresAt: string
}

export interface ResultIssueAttachmentCapabilities {
  directUploadAvailable: boolean
  maxFileSizeBytes: number
  maxAttachmentsPerIssue: number
}

export type ResultIssueAttachmentUploadStatus = 'PENDING_UPLOAD' | 'UPLOADED' | 'DELETED'
export type ResultIssueAttachmentScanStatus = 'PENDING' | 'CLEAN' | 'INFECTED' | 'SCAN_FAILED'

export interface ResultIssueAttachmentUpload {
  attachmentId: number
  uploadStatus: ResultIssueAttachmentUploadStatus
  scanStatus: ResultIssueAttachmentScanStatus
  uploadMethod: string
  uploadUrl: string
  requiredHeaders: Record<string, string>
  uploadExpiresAt: string
}

export interface ResultIssueAttachmentStatus {
  attachmentId: number
  uploadStatus: ResultIssueAttachmentUploadStatus
  scanStatus: ResultIssueAttachmentScanStatus
  uploadedAt: string | null
}

export interface ApiFieldViolation {
  field: string
  message: string
}
