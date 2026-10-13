export type AdminNavigationBlocker = () => boolean

let activeBlocker: AdminNavigationBlocker | null = null

export function setAdminNavigationBlocker(blocker: AdminNavigationBlocker): () => void {
  activeBlocker = blocker
  return () => {
    if (activeBlocker === blocker) activeBlocker = null
  }
}

export function confirmAdminNavigation(): boolean {
  return activeBlocker?.() ?? true
}
