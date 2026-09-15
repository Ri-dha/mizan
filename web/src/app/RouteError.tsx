import { useTranslation } from "react-i18next"
import { isRouteErrorResponse, Link, useRouteError } from "react-router"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

/** The screen behind a wrong address or a render failure: says what happened, offers a way home. */
export function RouteError() {
  const { t } = useTranslation()
  const error = useRouteError()
  const notFound = isRouteErrorResponse(error) && error.status === 404
  const detail = isRouteErrorResponse(error) ? `${error.status} ${error.statusText}` : error instanceof Error ? error.message : ""
  return (
    <main className="mx-auto flex min-h-dvh max-w-md items-center p-6">
      <Card className="w-full">
        <CardHeader>
          <CardTitle>{notFound ? t("errors.notFoundTitle") : t("common.error")}</CardTitle>
          <CardDescription>{notFound ? t("errors.notFoundBody") : detail}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          <Button asChild><Link to="/">{t("errors.goHome")}</Link></Button>
          <Button asChild variant="neutral"><a href="/docs/en/">{t("errors.openManual")}</a></Button>
          {!notFound && <Button variant="neutral" onClick={() => location.reload()}>{t("common.reload")}</Button>}
        </CardContent>
      </Card>
    </main>
  )
}
