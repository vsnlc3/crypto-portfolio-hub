import { NextResponse } from 'next/server'

export function GET() {
  const authorizationUrl =
    process.env.GOOGLE_OAUTH_AUTHORIZATION_URL ??
    'http://localhost:8080/oauth2/authorization/google'

  return NextResponse.redirect(new URL(authorizationUrl))
}
