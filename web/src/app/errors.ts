import { ApiError } from "@/api/client"
import i18n from "@/i18n"

/** Maps a problem+json code to a translated message, falling back to the server detail. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const key = `errors.${error.code}`
    return i18n.exists(key) ? i18n.t(key) : error.detail
  }
  return i18n.t("common.error")
}
