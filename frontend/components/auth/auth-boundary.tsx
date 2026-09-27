'use client'

import { useQuery, useQueryClient } from '@tanstack/react-query'
import { usePathname, useRouter } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'
import { AppShell } from '@/components/app-shell'
import { Button } from '@/components/ui/button'
import {
  authQueryKey,
  authReturnPathStorageKey,
  getCurrentUser,
  getSafeReturnPath,
  logout,
} from '@/lib/auth-api'

function AuthStatus({ message }: { message: string }) {
  return (
    <main className="grid min-h-svh place-items-center px-6">
      <p role="status" className="text-sm text-muted-foreground">
        {message}
      </p>
    </main>
  )
}

export function AuthBoundary({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const router = useRouter()
  const queryClient = useQueryClient()
  const [logoutError, setLogoutError] = useState(false)
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const isSignIn = pathname === '/signin'
  const userQuery = useQuery({
    queryKey: authQueryKey,
    queryFn: getCurrentUser,
    enabled: typeof window !== 'undefined',
    staleTime: 30_000,
  })
  const user = userQuery.data

  useEffect(() => {
    if (!user) return

    if (isSignIn) {
      const requested = new URLSearchParams(window.location.search).get('next')
      const stored = window.sessionStorage.getItem(authReturnPathStorageKey)
      const target = getSafeReturnPath(requested) ?? getSafeReturnPath(stored) ?? '/'
      window.sessionStorage.removeItem(authReturnPathStorageKey)
      router.replace(target)
      return
    }

    if (pathname === '/') {
      const stored = window.sessionStorage.getItem(authReturnPathStorageKey)
      const target = getSafeReturnPath(stored)
      if (target) router.replace(target)
      window.sessionStorage.removeItem(authReturnPathStorageKey)
    }
  }, [isSignIn, pathname, router, user])

  useEffect(() => {
    if (isSignIn || userQuery.isPending || userQuery.isError || user) return

    const returnPath = `${pathname}${window.location.search}`
    const next = getSafeReturnPath(returnPath) ?? '/'
    router.replace(`/signin?next=${encodeURIComponent(next)}`)
  }, [isSignIn, pathname, router, user, userQuery.isError, userQuery.isPending])

  const handleLogout = useCallback(async () => {
    setLogoutError(false)
    setIsLoggingOut(true)
    try {
      await logout()
      queryClient.clear()
      window.sessionStorage.removeItem(authReturnPathStorageKey)
      router.replace('/signin?reason=logged-out')
    } catch {
      setLogoutError(true)
    } finally {
      setIsLoggingOut(false)
    }
  }, [queryClient, router])

  if (isSignIn) return children

  if (userQuery.isPending) return <AuthStatus message="Checking your session…" />

  if (userQuery.isError) {
    return (
      <main className="grid min-h-svh place-items-center px-6">
        <section role="alert" className="max-w-md rounded-xl border border-border bg-card p-6 text-center">
          <h1 className="text-base font-semibold">We couldn’t verify your session</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Check your connection and try again. Your portfolio stays hidden until your session is confirmed.
          </p>
          <Button className="mt-5" onClick={() => void userQuery.refetch()}>
            Try again
          </Button>
        </section>
      </main>
    )
  }

  if (!user) {
    return <AuthStatus message="Redirecting to sign in…" />
  }

  return (
    <AppShell
      user={user}
      onLogout={() => void handleLogout()}
      isLoggingOut={isLoggingOut}
      logoutError={logoutError}
    >
      {children}
    </AppShell>
  )
}
