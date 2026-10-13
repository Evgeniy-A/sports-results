/* oxlint-disable react/only-export-components -- this tiny router intentionally colocates link, navigation and location state */
import { useEffect, useRef, useState } from 'react'
import type { AnchorHTMLAttributes } from 'react'
import { confirmAdminNavigation } from './navigationGuard'

export interface AdminLocation {
  pathname: string
  search: URLSearchParams
}

const ADMIN_HISTORY_INDEX = '__sportsResultsAdminIndex'
const ADMIN_NAVIGATION_EVENT = 'sportsresults:admin-navigation'

export function navigateAdmin(path: string): boolean {
  const currentPath = `${window.location.pathname}${window.location.search}`
  if (path === currentPath) return true
  if (!confirmAdminNavigation()) return false
  const nextIndex = ensureAdminHistoryIndex() + 1
  window.history.pushState(adminHistoryState(nextIndex), '', path)
  window.dispatchEvent(new Event(ADMIN_NAVIGATION_EVENT))
  return true
}

export function useAdminLocation(): AdminLocation {
  const read = () => ({ pathname: window.location.pathname, search: new URLSearchParams(window.location.search) })
  const [location, setLocation] = useState<AdminLocation>(read)
  const currentIndex = useRef(ensureAdminHistoryIndex())
  const currentUrl = useRef(window.location.href)
  useEffect(() => {
    let restoring = false
    const update = () => {
      currentIndex.current = ensureAdminHistoryIndex()
      currentUrl.current = window.location.href
      setLocation(read())
    }
    const handleHistoryNavigation = (event: PopStateEvent) => {
      const targetIndex = adminHistoryIndex(event.state)
      if (restoring) {
        restoring = false
        update()
        return
      }
      if (!confirmAdminNavigation()) {
        if (targetIndex !== null) {
          const delta = currentIndex.current - targetIndex
          if (delta !== 0) {
            restoring = true
            window.history.go(delta)
            return
          }
        }
        window.history.pushState(adminHistoryState(currentIndex.current), '', currentUrl.current)
        return
      }
      update()
    }
    window.addEventListener(ADMIN_NAVIGATION_EVENT, update)
    window.addEventListener('popstate', handleHistoryNavigation)
    return () => {
      window.removeEventListener(ADMIN_NAVIGATION_EVENT, update)
      window.removeEventListener('popstate', handleHistoryNavigation)
    }
  }, [])
  return location
}

export function AdminLink(props: AnchorHTMLAttributes<HTMLAnchorElement>) {
  return <a {...props} onClick={(event) => {
    props.onClick?.(event)
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || !props.href) return
    event.preventDefault()
    navigateAdmin(props.href)
  }} />
}

function ensureAdminHistoryIndex(): number {
  const existing = adminHistoryIndex(window.history.state)
  if (existing !== null) return existing
  window.history.replaceState(adminHistoryState(0), '', window.location.href)
  return 0
}

function adminHistoryIndex(state: unknown): number | null {
  if (!state || typeof state !== 'object') return null
  const value = (state as Record<string, unknown>)[ADMIN_HISTORY_INDEX]
  return typeof value === 'number' ? value : null
}

function adminHistoryState(index: number): Record<string, unknown> {
  const current = window.history.state
  return {
    ...(current && typeof current === 'object' ? current as Record<string, unknown> : {}),
    [ADMIN_HISTORY_INDEX]: index,
  }
}
