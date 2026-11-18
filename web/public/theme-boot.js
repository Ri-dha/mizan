// Runs before the first paint so a dark device never flashes the light theme (ADR ui-1).
;(function () {
  var choice = "system"
  try {
    choice = localStorage.getItem("mizan.theme") || "system"
  } catch (e) {}
  var dark = choice === "dark" || (choice !== "light" && window.matchMedia("(prefers-color-scheme: dark)").matches)
  document.documentElement.classList.toggle("dark", dark)
  var meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute("content", dark ? "#0a0a0a" : "#ffffff")
})()
