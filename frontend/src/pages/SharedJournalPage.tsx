import { useEffect, useMemo, useRef, useState } from 'react'
import { SharedJournalWorkspacePage } from '../admin/pages/AdminJournalPage'
import type { JournalShareContext } from '../admin/types'
import { createSharedJournalApi, exchangeJournalShare, loadJournalShareContext } from '../sharedJournalApi'
import '../admin/admin.css'

const TOKEN_KEY = 'sports-results-journal-share-session'
const CONTEXT_KEY = 'sports-results-journal-share-context'

export function SharedJournalPage({ grantToken }: { grantToken: string | null }) {
  const [sessionToken, setSessionToken] = useState<string | null>(() => sessionStorage.getItem(TOKEN_KEY))
  const [context, setContext] = useState<JournalShareContext | null>(() => {
    try { return JSON.parse(sessionStorage.getItem(CONTEXT_KEY) ?? 'null') as JournalShareContext | null }
    catch { return null }
  })
  const [error, setError] = useState<string | null>(() => grantToken || sessionToken
    ? null
    : 'Для доступа к журналу нужна действующая защищённая ссылка.')
  const exchangeStarted = useRef(false)
  const api = useMemo(() => sessionToken ? createSharedJournalApi(sessionToken) : null, [sessionToken])

  useEffect(() => {
    document.title = 'Журнал обращений — Sports Results'
    if (grantToken) {
      if (exchangeStarted.current) return
      exchangeStarted.current = true
      sessionStorage.removeItem(TOKEN_KEY)
      sessionStorage.removeItem(CONTEXT_KEY)
      setSessionToken(null)
      setContext(null)
      window.history.replaceState({}, '', '/journal-share')
      exchangeJournalShare(grantToken).then((exchange) => {
        sessionStorage.setItem(TOKEN_KEY, exchange.sessionToken)
        sessionStorage.setItem(CONTEXT_KEY, JSON.stringify(exchange.context))
        setSessionToken(exchange.sessionToken); setContext(exchange.context)
      }).catch(() => setError('Ссылка недействительна, истекла или была отозвана.'))
      return
    }
    if (sessionToken) {
      loadJournalShareContext(sessionToken).then((next) => {
        setContext(next); sessionStorage.setItem(CONTEXT_KEY, JSON.stringify(next))
      }).catch(() => {
        sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(CONTEXT_KEY)
        setSessionToken(null); setContext(null); setError('Сеанс доступа завершён. Откройте исходную ссылку снова.')
      })
    }
  }, [grantToken, sessionToken])

  if (error) return <main className="admin-root"><div className="admin-content"><section className="admin-empty"><p className="admin-eyebrow">Защищённый доступ</p><h1>Журнал недоступен</h1><p>{error}</p></section></div></main>
  if (!api || !context) return <main className="admin-root"><div className="admin-content"><section className="admin-empty"><h1>Открываем журнал…</h1></section></div></main>
  return <main className="admin-root shared-journal-root"><div className="admin-content"><SharedJournalWorkspacePage api={api} context={context} /></div></main>
}
