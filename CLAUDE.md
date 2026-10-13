# Project Development Rules

## 1. Work on the Original Project
- The current project directory is the source of truth.
- Modify existing source files directly in their original locations.
- Create new files only inside the appropriate project directories.
- Never create a separate copy of the project or a replacement ZIP unless explicitly requested.
- Preserve existing business logic, API contracts, configuration, and database compatibility.

## 2. Progress Tracking
- Before starting, inspect TASK_PROGRESS.md if it exists.
- For a new task, create or update TASK_PROGRESS.md with the objective and implementation checklist.
- Update progress after each meaningful implementation milestone.
- Record modified files, completed work, pending work, errors, and exact next steps.
- Never mark unfinished work as complete.

## 3. Save Changes Incrementally
- Write changes directly to the original project files as you implement them.
- Do not wait until the entire task is complete before saving files.
- After each meaningful change, verify that the file was written successfully.
- Before finishing a work session, update TASK_PROGRESS.md with the latest state.
- If interrupted, resume from the actual files and TASK_PROGRESS.md instead of restarting the implementation.

## 4. Engineering Quality
- Inspect existing code before modifying it.
- Follow the existing project architecture and coding conventions.
- Avoid unrelated changes and unnecessary rewrites.
- Run relevant tests and builds after implementation.
- Fix errors introduced by your changes.
- Never claim that tests passed unless they actually passed.

## 5. Task Completion
- Complete the original objective rather than merely describing the solution.
- Summarize files created or modified, tests executed, outstanding issues, and the next action.
- If the task is incomplete, clearly record the exact remaining work in TASK_PROGRESS.md.