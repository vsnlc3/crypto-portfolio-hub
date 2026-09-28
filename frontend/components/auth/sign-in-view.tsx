'use client'

import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  authQueryKey,
  authReturnPathStorageKey,
  getCurrentUser,
  getSafeReturnPath,
} from '@/lib/auth-api'
import { demoUser } from '@/lib/demo-fixtures'
import { enableDemoMode } from '@/lib/demo-mode'

export function SignInView({
  oauthError,
  returnPath,
}: {
  oauthError: boolean
  returnPath: string | null
}) {
  const [isStarting, setIsStarting] = useState(false)
  const router = useRouter()
  const queryClient = useQueryClient()
  const userQuery = useQuery({
    queryKey: authQueryKey,
    queryFn: getCurrentUser,
    enabled: typeof window !== 'undefined',
    staleTime: 30_000,
  })

  function startGoogleLogin() {
    if (isStarting || userQuery.data) return
    const safePath =
      getSafeReturnPath(returnPath) ??
      getSafeReturnPath(window.sessionStorage.getItem(authReturnPathStorageKey))
    if (safePath) window.sessionStorage.setItem(authReturnPathStorageKey, safePath)
    else window.sessionStorage.removeItem(authReturnPathStorageKey)
    setIsStarting(true)
    window.location.assign('/auth/google')
  }

  function startDemo() {
    if (userQuery.data) return
    let storedReturnPath: string | null = null
    try {
      storedReturnPath = window.sessionStorage.getItem(authReturnPathStorageKey)
    } catch {
      // The demo can still open the Dashboard when browser storage is blocked.
    }
    const safePath = getSafeReturnPath(returnPath)
      ?? getSafeReturnPath(storedReturnPath)
      ?? '/'
    enableDemoMode()
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'auth' })
    queryClient.setQueryData(authQueryKey, demoUser)
    router.replace(safePath)
  }

  const sessionError = userQuery.isError

  return (
    <main className="relative grid min-h-svh place-items-center overflow-hidden px-5 py-12">
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(0.8_0.15_164/0.12),transparent_50%)]" />
      <section className="relative w-full max-w-md rounded-2xl border border-border bg-card/95 p-7 shadow-2xl shadow-black/20 sm:p-9">
        <div className="mb-9 flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <span className="font-mono text-lg font-bold">M</span>
          </div>
          <div className="leading-tight">
            <p className="text-base font-semibold">Meridian</p>
            <p className="text-xs text-muted-foreground">Crypto Portfolio</p>
          </div>
        </div>

        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">Your portfolio, together</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight">Sign in to continue</h1>
        <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
          See your read-only crypto portfolio across bitbank, Solana, and Hyperliquid in one place.
        </p>

        {oauthError && (
          <p role="alert" className="mt-6 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-sm text-destructive">
            Google sign-in did not complete. Please try again.
          </p>
        )}

        {sessionError && (
          <div role="alert" className="mt-6 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-3 text-sm">
            <p>We couldn’t check your session. Try again before signing in.</p>
            <Button variant="link" className="mt-1 h-auto px-0 py-1 text-sm" onClick={() => void userQuery.refetch()}>
              Retry session check
            </Button>
          </div>
        )}

        <Button
          className="mt-8 h-11 w-full gap-3 text-sm"
          disabled={isStarting || userQuery.isPending || sessionError || Boolean(userQuery.data)}
          onClick={startGoogleLogin}
        >
          {isStarting || userQuery.data ? (
            <span className="size-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-hidden="true" />
          ) : (
            <GoogleMark />
          )}
          {isStarting
            ? 'Connecting to Google…'
            : userQuery.data
              ? 'Opening your portfolio…'
              : 'Continue with Google'}
        </Button>

        <Button
          variant="outline"
          className="mt-3 h-10 w-full"
          disabled={userQuery.isPending || Boolean(userQuery.data)}
          onClick={startDemo}
        >
          View read-only demo
        </Button>

        {userQuery.isPending && (
          <p role="status" className="mt-3 text-center text-xs text-muted-foreground">
            Checking your sign-in status…
          </p>
        )}

        <p className="mt-6 text-center text-xs leading-relaxed text-muted-foreground">
          Demo uses sample data only. Personal Connections require Google sign-in; the demo cannot change or sync data.
        </p>
      </section>
      <p className="relative mt-6 text-xs text-muted-foreground">Personal portfolio access · Google account required</p>
    </main>
  )
}

function GoogleMark() {
  return (
    <svg aria-hidden="true" viewBox="0 0 48 48" className="size-5">
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5Z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.91c-.58 2.96-2.26 5.48-4.73 7.18l7.65 5.94c4.46-4.12 7.15-10.18 7.15-17.59Z" />
      <path fill="#FBBC05" d="M10.53 28.59A14.4 14.4 0 0 1 9.75 24c0-1.59.27-3.13.76-4.59l-7.98-6.19A23.9 23.9 0 0 0 0 24c0 3.89.93 7.57 2.56 10.78l7.97-6.19Z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.91-5.86l-7.65-5.94c-2.13 1.43-4.86 2.28-8.26 2.28-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48Z" />
      <path fill="none" d="M0 0h48v48H0z" />
    </svg>
  )
}
