import { NextResponse, type NextRequest } from "next/server";

/**
 * Next.js 16 route gate (replaces middleware.ts). Defense-in-depth only: it checks for the
 * non-sensitive `nn_session` presence cookie set on login and redirects to /login when missing.
 * Real JWT verification happens at the API; client guards (useAuth) remain in place.
 */
export function proxy(request: NextRequest) {
  const hasSession = request.cookies.has("nn_session");
  if (!hasSession) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set(
      "next",
      request.nextUrl.pathname + request.nextUrl.search,
    );
    return NextResponse.redirect(loginUrl);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/candidate/:path*", "/employer/:path*"],
};
