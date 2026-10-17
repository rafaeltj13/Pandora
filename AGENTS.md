# Pandora

Pandora is a mobile platform for productivity. In pandora we can manage some productivity tools disposed in a modular approach, the idea is that he user will be able to toggle what we want to use in the app.

## Note from the development team

We like ambitious ideas, simple systems, and software that feels obvious. Do not preserve complexity just because it already exists. Do not introduce machinery because it looks architecturally impressive. Understand the real constrait, then then fight for the smaller solution that makes the curren behavior correct.

Channel both "measure twice, cut once" and "yagni". Fight scope creep. Try ti honor the dev's intent in both a minimal and realistic fashion.

## How the repo was created

- `apps/android`: native Kotlin/Compose app; UI calls a repository through a
  ViewModel. Keep networking and parsing out of composables.
- Android UI follows Material 3 (see the `material-3` skill), styled like
  Android system settings. Build screens from the shared parts in
  `ui/PandoraPage.kt`: `PandoraScaffold`/`PandoraPage` (top app bar over a
  rounded sheet), `PandoraSection` (labelled tonal group), and
  `PandoraListItem` (row; clickable rows get a chevron). Use
  `MaterialTheme` colors, type, shapes, and `PandoraSpacing` instead of raw
  values. The Appearance screen keeps its own custom layout.
- There is no Pandora backend. Each module talks directly to the service it
  integrates (Android Calendar provider, GitHub over HTTPS) from a repository
  in `data/`. Do not add a server unless we ask for one.
- Use the committed Gradle wrapper. Root Make commands coordinate it.
- Releases require HTTPS-only traffic and separate signing configuration.
- Run `make check` for implementation changes and `make test-device` for
  changes to screen behavior. Do not claim phone verification without a device.
- Do not commit toolchain downloads, SDKs, local config, APKs, or credentials.

## A small glossary

We need to be on the same page with terminology. When communicating, use this language:

- you means the agent reading this file and changing the pandora platform.
- we, us and maitainers mean the people building the platform, who are you talking now.
- user means the person using the pandora platform.
- client means the android app.
- services mean the external platforms a module connects to.
- project means the pandora platform.

### Development approaches

- Start a simulation environment when you are developing a new feature, validate your changes and close and clean this simulated test environment to
free the PC usage.
- When working in a change, we need to make sure that the smallest proof that the change works. Run the tests for the files you touched, targeted lint and typecheck for the scope you changed.
- Every regression fix starts with a failing behavior test at the lowest stable boundary. After the fix, run the smallest targeted validation command that proves the behavior.
- Do not run repo-wide checks unless I ask.
- Database changes follow an expand-and-contract policy. Destructive renames or drops require a separate release so the previous Worker remains compatible if deployment fails after migration.

### Agent delegation

- Delegate bounded tasks to cheaper subagents when this reduces the main agent's workload. Handle trivial tasks directly when delegation would cost more than the work.
- Give each subagent a concrete objective, relevant paths, constraints, acceptance criteria, and the smallest appropriate validation command. Pass a compact task brief instead of the full conversation when possible, and explicitly select the model and reasoning effort.
- Require a concise result with evidence, changed files, validation results, and unresolved concerns. Use that evidence without repeating the delegated exploration unless it is incomplete or contradictory.
- Escalate to a stronger model after one unsuccessful repair attempt or when the task exceeds its assigned scope. Include the failed attempt and evidence in the handoff.
- Use at most two subagents concurrently. Delegate independent work only, assign disjoint files to concurrent editors, and do not let subagents delegate further.
- The main agent owns integration and final verification. Delegation must follow the development and validation rules above, including restrictions on bench runs, browser checks, and deployments.

### Additional tips

- Performance is important, we should make sure we are creating performant solutions.

### Token-efficient tool usage (RTK)

- `rtk` is installed on this machine and shell commands are auto-rewritten through it to save tokens.
- Prefer `rtk read <file>` over the Read tool for exploration, `rtk grep <pattern>` over Grep, and `rtk find` / `rtk ls` over Glob. Built-in tools bypass the token filter.
- Only fall back to `rtk proxy <cmd>` when the condensed output is unusable.
