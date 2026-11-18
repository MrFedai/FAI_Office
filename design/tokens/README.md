# Design tokens

| File | Source | Edit here? |
|---|---|---|
| `fai.tokens.json` | Verbatim copy of `claude-ortak/tasarim/kit/tokens/fai.tokens.json`, kit v1.0, tokens 1.1.0 (2026-11-05), copied 2026-11-18 | No: change the kit, then copy again |
| `office.tokens.json` | FAI Office: dropped Trade meanings, renames, light-theme contrast overrides, file-type colours, type styles, extra sizes | Yes |

Compose output: `python3 tools/tokens/gen_compose.py` writes
`app/src/commonMain/kotlin/org/faioffice/app/theme/FaiTokens.kt` (committed). `--check` fails when the committed file
is out of date; `ci/lint.sh` runs it. Rules and meaning of every token: `DESIGN.md`.
