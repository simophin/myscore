---
name: github-release
description: Create a production GitHub release for the simophin/myscore repository with GitHub-generated release notes. Use when the user asks to publish, cut, create, or prepare a new release or the next minor release of this project.
---

# GitHub Release

Publish a release from `main` with `gh`, using the release identifier as both the Git tag and the GitHub release title.

## Workflow

1. Fetch tags from `origin` and find the highest stable tag matching this project's `MAJOR.MINOR.PATCH` convention. Compute the next minor version by incrementing `MINOR` and resetting `PATCH` to `0`. Preserve the repository's existing lack of a `v` prefix.

2. Before changing any remote state, ask the user to choose between:
   - the computed next minor release, showing the exact value; or
   - entering a custom release name.

   Treat the selected or entered release name as both the tag and release title. Do not create a release until the user answers. If the user chooses a custom name, require a value valid as a Git tag and reject an empty value.

3. Run every `gh` command outside sandbox mode because GitHub CLI authentication requires host access. With a shell execution tool, request escalated or unsandboxed execution for each command instead of first attempting it in the sandbox.

4. Check authentication with `gh auth status`. If multiple GitHub CLI accounts are configured, ensure `simophin` is active with:

   ```bash
   gh auth switch --user simophin
   ```

   If `simophin` is not authenticated, stop and ask the user to authenticate that account. Never create this project's release using another account.

5. Verify that the selected tag does not already exist as a GitHub release or remote tag. Stop with a clear explanation if it exists; do not overwrite or edit an existing release. Query GitHub for the current `main` branch immediately before creating the release and report its commit SHA in the preflight result.

6. Create the release from the remote `main` branch and let GitHub generate the notes. Keep `--target main` in the command so GitHub resolves the latest remote `main` during release creation; never substitute the local `HEAD`, a locally cached `main`, or the earlier preflight SHA:

   ```bash
   gh release create "<release-name>" \
     --repo simophin/myscore \
     --target main \
     --title "<release-name>" \
     --generate-notes
   ```

   Quote the release name and do not use `eval`. Do not supply hand-written notes, `--notes`, or `--notes-file`.

7. Run `gh release view "<release-name>" --repo simophin/myscore --json name,tagName,url,isDraft,isPrerelease` outside the sandbox. Report the created tag, title, and release URL to the user.

## Guardrails

- Create a normal, published release: do not add `--draft` or `--prerelease` unless the user explicitly changes the request.
- Always target the latest remote `main` on GitHub rather than relying on the current local checkout, local branch, or a previously resolved commit.
- Do not push a local tag separately; allow `gh release create` to create it from `main`.
- Do not change source files, version files, changelogs, or build artifacts as part of this workflow unless the user explicitly requests that additional work.
