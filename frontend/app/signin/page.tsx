import { SignInView } from '@/components/auth/sign-in-view'
import { getSafeReturnPath } from '@/lib/auth-api'

type SignInPageProps = {
  searchParams: Promise<Record<string, string | string[] | undefined>>
}

export default async function SignInPage({ searchParams }: SignInPageProps) {
  const params = await searchParams
  const error = typeof params.error === 'string' ? params.error : null
  const next = typeof params.next === 'string' ? getSafeReturnPath(params.next) : null

  return (
    <SignInView
      oauthError={error === 'AUTHENTICATION_FAILED'}
      returnPath={next}
    />
  )
}
