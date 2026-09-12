import { StrictMode } from "react"
import { createRoot } from "react-dom/client"

import "./i18n"
import "./index.css"
import App from "./app/App"
import { registerPwa } from "./pwa/register"
import { applyStoredTheme } from "./app/theme"

applyStoredTheme()
registerPwa()

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
