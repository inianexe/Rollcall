# Rollcall Codex skills

Project skills live in `.agents/skills/` and are discovered by supported Agent Skills clients, including Codex. Installations below are scoped to this repository; the existing `text-to-lottie` skill remains in the user's Codex skill directory and was not copied or replaced.

## Installed skills

| Skill | Source | Use |
|---|---|---|
| `android-profiler` | `android/skills` | Perfetto and Android CPU, memory, scheduling, and frame analysis |
| `jetpack-compose-performance` | `android/skills` | Compose recomposition, layout, lazy list, and rendering work |
| `testing-setup` | `android/skills` | Android unit, instrumentation, Compose UI, and test harness guidance |
| `edge-to-edge` | `android/skills` | System bars, window/IME insets, and adaptive layouts |
| `migrate-xml-views-to-jetpack-compose` | `android/skills` | Incremental View/Compose interop; apply selectively because Rollcall builds many Views programmatically |
| `r8-analyzer` | `android/skills` | R8/ProGuard keep rules and release size analysis |
| `android-intent-security` | `android/skills` | Manifest components and untrusted Intent handling |
| `kotlin-tooling-java-to-kotlin` | `Kotlin/kotlin-agent-skills` | Explicitly requested Java-to-Kotlin conversion; does not authorize converting Rollcall business logic |
| `rollcall-migration-runner` | Local | Rollcall's incremental migration workflow |
| `rollcall-regression-guardian` | Local | Protect functionality and data compatibility |
| `rollcall-performance-gate` | Local | Reproducible Macrobenchmark and Perfetto workflow |
| `text-to-lottie` | Existing user-level skill | Lottie scene authoring and troubleshooting; existing copy preserved |

## Activation

Choose only task-relevant skills. For screen migration, use the Rollcall migration and regression skills plus applicable Android migration guidance. For runtime profiling, use the Rollcall performance gate, Android profiler, and Compose performance skills. See the task routing in `AGENTS.md`.

## Development workflow

```text
Task → relevant project context → smallest applicable skill set → inspect current code
     → targeted implementation → regression checks → performance checks when relevant
     → document evidence and limitations → commit when authorized
```

## Updating and validating

List available skills with `npx skills add <owner/repository> --list`; install only verified names using `npx skills add ... --skill <name> -a codex --copy -y`. Review downloaded instructions and scripts before execution. Validate local skills with the bundled Codex `skill-creator` validator when available, and check frontmatter, discovery path, duplicate names, and Git diff.

## Benchmark evidence policy

Keep reusable benchmark code and configuration in the repository. Save large traces and raw measurements under `/home/inian/Projects/Rollcall-performance-qa/` so they survive temporary worktree cleanup. The previous Phase 6B.1R work and traces were lost before commit. Its numbers are preliminary and must not be represented as reproduced; collect a fresh run before making performance claims.

## Installation scope and limitations

External skills were copied into this repository's `.agents/skills/` directory through Skills CLI using Node 22.23.4; the system Node 18.19.1 cannot run the current CLI because `node:util.styleText` is unavailable. Their upstream revisions are those fetched at installation time; `skills-lock.json` records source paths and content hashes. The existing Lottie skill was already present in both `/home/inian/.codex/skills/text-to-lottie/` and `/home/inian/.agents/skills/text-to-lottie/`; their `SKILL.md` files are byte-identical (SHA-256 `d2e5e615…8442fd`). Both were left unchanged and no project copy was added. Project skill discovery was confirmed with `npx skills list`; the three custom skills passed the bundled `quick_validate.py` check.
