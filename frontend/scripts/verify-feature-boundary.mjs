import { readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"

const featureRoot = new URL("../src/features/", import.meta.url)
const forbidden = [
  { pattern: /\bfetch\s*\(/, reason: "fetch" },
  { pattern: /\baxios\b/, reason: "axios" },
  { pattern: /["'`]\/?(?:orders|inventory)(?:\/|["'`])/, reason: "backend endpoint literal" },
]

const violations = readdirSync(featureRoot, { withFileTypes: true })
  .filter((entry) => entry.isFile() && /\.tsx?$/.test(entry.name))
  .flatMap((entry) => {
    const file = join(featureRoot.pathname, entry.name)
    const source = readFileSync(file, "utf8")
    return forbidden
      .filter(({ pattern }) => pattern.test(source))
      .map(({ reason }) => `${file}: forbidden ${reason} in feature module`)
  })

if (violations.length > 0) {
  console.error(violations.join("\n"))
  process.exit(1)
}
