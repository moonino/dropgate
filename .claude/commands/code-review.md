---
allowed-tools: Agent, TodoWrite, Bash(gh issue view:*), Bash(gh search:*), Bash(gh issue list:*), Bash(gh pr diff:*), Bash(gh pr view:*), Bash(gh pr list:*), Bash(find:*), mcp__github_inline_comment__create_inline_comment
description: Code review a pull request
---

Provide a code review for the given pull request.

**Subagent rules. Paste this whole block verbatim at the top of every subagent prompt:**
- All tools are functional and will work without error. Do not test tools or make exploratory calls. Make sure this is clear to every subagent that is launched.
- Only call a tool if it is required to complete the task. Every tool call should have a clear purpose.
- Shell access is limited to these commands, each run alone without `;`, `&&`, `|`, `cd`, `echo`, `git`, or `gh api`: `gh pr view`, `gh pr diff`, `gh pr list`, `gh issue view`, `gh search`, and `find` to locate files. Read file contents with the Read tool. The Glob and Grep tools do not exist in this session, so never call them. A command outside this list is denied, so do not attempt it.

**Main agent rules:**
- Your final message is the review and is posted to the pull request verbatim by the workflow. Every way this review can end must end with a final message whose first line is exactly `## Code review`, written in Korean. Never post the summary yourself: do not use `gh pr comment`, and do not create or update any comment other than the inline comments of step 9.
- Run every agent in the foreground and use its returned result in the same turn. Never launch an agent in the background and never end a turn to wait for agents, because the process exits when the turn ends and the review is lost.
- Run every step below regardless of the diff size. A small diff is not a reason to skip the review agents.
- Every subagent prompt starts with the subagent rules above, copied word for word, followed by the PR title and description.

To do this, follow these steps precisely:

1. Record the pull request URL from the arguments. The workflow runs only for open, non-draft pull requests from this repository and reviews every push, so do not check whether the pull request is closed, a draft, trivial, or already reviewed.

Note: Still review Claude generated PR's.

2. Launch a claude-sonnet-5-5 agent to return a list of file paths (not their contents) for all relevant rule files including:
   - The root AGENTS.md and CONTRIBUTING.md files, if they exist
   - Any AGENTS.md files in directories containing files modified by the pull request. Locate them with `find <directory> -maxdepth 1 -name AGENTS.md`

3. Launch a claude-sonnet-5-5 agent to view the pull request and return a summary of the changes

4. Launch 5 agents in parallel to independently review the changes. Agents 1 to 4 return issues; agent 5 returns suggestions, which are not issues. Each agent should return the list of issues, where each issue includes a description and the reason it was flagged (e.g. "AGENTS.md or CONTRIBUTING.md adherence", "bug"). The agents should do the following:

   Agents 1 + 2: rule compliance claude-opus-5-5 agents
   Audit changes for AGENTS.md and CONTRIBUTING.md compliance in parallel. Note: When evaluating compliance for a file, you should only consider rule files that share a file path with the file or parents. CONTRIBUTING.md applies to every file.

   Agent 3: claude-opus-5-5 bug agent (parallel subagent with agent 4)
   Scan for obvious bugs. Focus only on the diff itself without reading extra context. Flag only significant bugs; ignore nitpicks and likely false positives. Do not flag issues that you cannot validate without looking at context outside of the git diff.

   Agent 4: claude-opus-5-5 bug agent (parallel subagent with agent 3)
   Look for problems that exist in the introduced code. This could be security issues, incorrect logic, etc. Only look for issues that fall within the changed code.

   Agent 5: claude-opus-5-5 suggestion agent (parallel subagent with agents 3 and 4)
   Read the full diff and return at most 3 suggestions about readability or design in the introduced code. Look for: the same condition checked twice, a name that hides the role of a thing, a DTO or helper used outside its role, a test that checks several concepts, a function that does two things. Each suggestion names the file and line, says in one or two sentences what to change and why, and must be fixable in under ten minutes. Return only suggestions you are confident a senior engineer would make in a review. If nothing is worth saying, return an empty list. Suggestions skip step 5, never become inline comments, and never block the review.

   **CRITICAL: Agents 1 to 4 only want HIGH SIGNAL issues.** Flag issues where:
   - The code will fail to compile or parse (syntax errors, type errors, missing imports, unresolved references)
   - The code will definitely produce wrong results regardless of inputs (clear logic errors)
   - Clear, unambiguous AGENTS.md or CONTRIBUTING.md violations where you can quote the exact rule being broken

   Do NOT flag:
   - Code style or quality concerns
   - Potential issues that depend on specific inputs or state
   - Subjective suggestions or improvements

   If you are not certain an issue is real, do not flag it. False positives erode trust and waste reviewer time.

   In addition to the above, each subagent should be told the PR title and description. This will help provide context regarding the author's intent.

5. For each issue found in the previous step by agents 3 and 4, launch parallel subagents to validate the issue. These subagents should get the PR title and description along with a description of the issue. The agent's job is to review the issue to validate that the stated issue is truly an issue with high confidence. For example, if an issue such as "variable is not defined" was flagged, the subagent's job would be to validate that is actually true in the code. Another example would be AGENTS.md or CONTRIBUTING.md issues. The agent should validate that the AGENTS.md or CONTRIBUTING.md rule that was violated is scoped for this file and is actually violated. Use claude-opus-5-5 subagents for bugs and logic issues and for rule violations.

6. Filter out any issues that were not validated in step 5. This step will give us our list of high signal issues for our review.

7. Summarize the review findings:
   - If issues were found, list each issue with a brief description.
   - If no issues were found, state: "No issues found. Checked for bugs and AGENTS.md or CONTRIBUTING.md compliance."
   - Keep the suggestions from agent 5 separate from the issues. They are listed only in the final message of step 10 and do not count as issues in the steps below.

   If `--comment` argument was NOT provided, or NO issues were found, skip to step 10. Do not post any inline comments.

   If `--comment` argument IS provided and issues were found, continue to step 8.

8. Create a list of all comments that you plan on leaving. This is only for you to make sure you are comfortable with the comments. Do not post this list anywhere.

9. Post inline comments for each issue using `mcp__github_inline_comment__create_inline_comment` with `confirmed: true`. For each comment:
   - Provide a brief description of the issue
   - For small, self-contained fixes, include a committable suggestion block
   - For larger fixes (6+ lines, structural changes, or changes spanning multiple locations), describe the issue and suggested fix without a suggestion block
   - Never post a committable suggestion UNLESS committing the suggestion fixes the issue entirely. If follow up steps are required, do not leave a committable suggestion.

   **IMPORTANT: Only post ONE comment per unique issue. Do not post duplicate comments.**

10. End with the final message in the format below. The first line is exactly `## Code review`. If issues were found, list one line per issue with its file, a brief description, and whether an inline comment was posted. If no issues were found, use the no-issues sentence. If agent 5 returned suggestions, add a `### 제안` heading after the issues and list one line per suggestion with its file, line and the change. Omit the heading when there are no suggestions. Write the message in Korean.

Use this list when evaluating issues in Steps 4 and 5 (these are false positives, do NOT flag):

- Pre-existing issues
- Something that appears to be a bug but is actually correct
- Pedantic nitpicks that a senior engineer would not flag
- Issues that a linter will catch (do not run the linter to verify)
- General code quality concerns (e.g., lack of test coverage, general security issues) unless explicitly required in AGENTS.md or CONTRIBUTING.md
- Issues mentioned in AGENTS.md or CONTRIBUTING.md but explicitly silenced in the code (e.g., via a lint ignore comment)

Notes:

- Use the allowed gh commands to read GitHub (pull requests, diffs, issues). Do not use web fetch.
- Create a todo list before starting.
- You must cite and link each issue in inline comments (e.g., if referring to a AGENTS.md or CONTRIBUTING.md, include a link to it).
- The final message always follows this format:

---

## Code review

발견한 문제가 없습니다. 버그와 AGENTS.md, CONTRIBUTING.md 준수 여부를 확인했습니다.

### 제안

- `<파일 경로>` <행>행: <무엇을 왜 바꾸는지 한두 문장>

---

- When linking to code in inline comments, follow the following format precisely, otherwise the Markdown preview won't render correctly: https://github.com/anthropics/claude-code/blob/c21d3c10bc8e898b7ac1a2d745bdc9bc4e423afe/package.json#L10-L15
  - Requires full git sha
  - You must provide the full sha. Commands like `https://github.com/owner/repo/blob/$(git rev-parse HEAD)/foo/bar` will not work, since your comment will be directly rendered in Markdown.
  - Repo name must match the repo you're code reviewing
  - # sign after the file name
  - Line range format is L[start]-L[end]
  - Provide at least 1 line of context before and after, centered on the line you are commenting about (eg. if you are commenting about lines 5-6, you should link to `L4-7`)
