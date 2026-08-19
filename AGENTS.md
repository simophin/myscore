# Repository agent instructions

## Git workflow

- Pull the latest `main` in the primary checkout before starting a new task.
- Create a dedicated Git worktree and a new task-specific branch from the updated `main` before modifying files.
- Keep the primary checkout clean; make all task changes, validation runs, and commits from the task worktree.
- Reuse an existing task worktree only when the user explicitly asks to continue that same branch of work.
- After completing and validating the code, commit the changes, push the task branch, and create a pull request.
