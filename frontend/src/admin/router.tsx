/* oxlint-disable react/only-export-components -- this tiny router intentionally colocates link, navigation and location state */
import { useEffect, useState } from 'react'
import type { AnchorHTMLAttributes } from 'react'

export interface AdminLocation {
  pathname: string
  search: URLSearchParams
}

export function navigateAdmin(path: string): void {
  window.history.pushState({}, '', path)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export function useAdminLocation(): AdminLocation {
  const read = () => ({ pathname: window.location.pathname, search: new URLSearchParams(window.location.search) })
  const [location, setLocation] = useState<AdminLocation>(read)
  useEffect(() => {
    const update = () => setLocation(read())
    window.addEventListener('popstate', update)
    return () => window.removeEventListener('popstate', update)
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
