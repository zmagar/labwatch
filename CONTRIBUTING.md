# Working agreements

- Read this file and MILESTONES.md at session start.
- Plan mode first. Wait for numbered approval before editing.
- No new dependencies in pom.xml without asking. Explain why.
- `mvn test` green before session end.
- Commit on the milestone branch; pause for IntelliJ review before merge.
- Merge with `git merge --no-ff`. Never fast-forward.
- Use the default `~/.m2` repository. No `-Dmaven.repo.local` override.
- Stay in milestone scope. Flag out-of-scope findings, don't fix them.
- Mutate the critical predicate of each milestone before merging; confirm a test fails.
