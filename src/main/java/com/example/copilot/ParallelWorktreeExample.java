package com.example.copilot;

import com.github.copilot.sdk.*;
import com.github.copilot.sdk.events.*;
import com.github.copilot.sdk.json.*;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Parallel Worktree Workflow
 *
 * Demonstrates using LangChain4j's parallelBuilder() + Git worktrees + Copilot SDK to:
 *   1. Create a Git worktree for a feature branch
 *   2. Fan out 3 parallel generate→review workflows (StringUtils, DateUtils, FileUtils)
 *      Each workflow loops: generate code → review → retry if review fails (up to 3 attempts)
 *   3. Commit all changes in the worktree
 *   4. Merge the feature branch back into main and clean up
 *
 * The parallel fan-out uses AgenticServices.parallelBuilder() from langchain4j-agentic,
 * where each sub-agent is a generate→review loop wrapping Copilot SDK sessions.
 * Inspired by LangChain4j's loop workflow pattern: if a review fails, the code is
 * regenerated and re-reviewed, ensuring quality gates are enforced before proceeding.
 *
 * All Git operations are LOCAL ONLY — no push or remote calls.
 * Idempotent: can be run repeatedly without manual cleanup.
 */
public class ParallelWorktreeExample {

    private static final Path REPO_ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final String PKG_DIR = "src/main/java/com/example/copilot";

    private static final String[] GENERATED_FILES = {
        PKG_DIR + "/StringUtils.java",
        PKG_DIR + "/DateUtils.java",
        PKG_DIR + "/FileUtils.java"
    };

    // Shared CopilotClient — initialized in main, used by agent actions
    private static CopilotClient copilotClient;

    public static void main(String[] args) throws Exception {
        var featureBranch = "feature/parallel-generated-" + System.currentTimeMillis();
        var worktreePath = REPO_ROOT.resolve("../wt-" + featureBranch.replace("/", "-"));

        System.out.println("=== Parallel Worktree Workflow (LangChain4j + Copilot SDK) ===\n");
        System.out.println("Repo root:      " + REPO_ROOT);
        System.out.println("Feature branch: " + featureBranch);
        System.out.println("Worktree path:  " + worktreePath);
        System.out.println();

        try (var client = new CopilotClient()) {
            copilotClient = client;
            client.start().get();

            // Step 0: Clean up generated files from any previous run
            cleanupPreviousRun();

            // Step 1: Create feature branch and worktree
            System.out.println("[1/4] Creating feature branch and worktree...");
            git("branch", featureBranch);
            git("worktree", "add", worktreePath.toString(), featureBranch);

            // Step 2: Parallel generate→review — each class runs its own generate→review loop
            System.out.println("[2/4] Running 3 generate→review workflows in parallel...");

            var stringUtilsWorkflow = buildGenerateAndReviewAction(worktreePath, GENERATED_FILES[0],
                    """
                    Generate a Java utility class called StringUtils in package com.example.copilot
                    with a private constructor that throws AssertionError, and these static methods:

                    - reverse(String s) — returns null if s is null. Returns "" if s is empty.
                      Implementation: collect s.codePoints().toArray(), reverse the int array manually
                      with a for-loop, then return new String(reversed, 0, reversed.length).
                      Do NOT use StringBuilder.reverse() as it breaks surrogate pairs.

                    - isPalindrome(String s) — returns false if s is null. Returns true if s is empty.
                      Implementation: convert s.toLowerCase(Locale.ROOT), then collect codePoints()
                      into an int array. Compare array[i] with array[length-1-i] in a for-loop
                      from 0 to length/2. Do NOT use charAt or offsetByCodePoints.

                    - truncate(String s, int maxLen) — returns null if s is null.
                      Throws IllegalArgumentException if maxLen < 0.
                      Implementation: use s.codePointCount(0, s.length()) to get the codepoint count.
                      If count <= maxLen, return s unchanged.
                      If maxLen < 3, return new String(s.codePoints().toArray(), 0, maxLen).
                      Otherwise, return new String(s.codePoints().toArray(), 0, maxLen - 3) + "...".

                    Use Java 21 features. Import java.util.Locale. No external dependencies.
                    Output ONLY the raw Java source code. No markdown fences, no explanation.
                    """, "StringUtils.java");

            var dateUtilsWorkflow = buildGenerateAndReviewAction(worktreePath, GENERATED_FILES[1],
                    """
                    Generate a Java utility class called DateUtils in package com.example.copilot
                    with these static methods:
                    - daysUntil(LocalDate target) — returns days from today to target date.
                      Throws NullPointerException via Objects.requireNonNull if target is null.
                    - isWeekend(LocalDate date) — returns true if Saturday or Sunday.
                      Throws NullPointerException via Objects.requireNonNull if date is null.
                    - formatRelative(LocalDate date) — returns "today", "yesterday", "3 days ago", etc.
                      Throws NullPointerException via Objects.requireNonNull if date is null.
                      Use long (not int) for day difference calculations throughout. No casts.
                    Use Java 21 features (java.time). No external dependencies.
                    Output ONLY the raw Java source code. No markdown fences, no explanation.
                    """, "DateUtils.java");

            var fileUtilsWorkflow = buildGenerateAndReviewAction(worktreePath, GENERATED_FILES[2],
                    """
                    Generate a Java utility class called FileUtils in package com.example.copilot
                    with these static methods:
                    - readLines(Path file) — reads all lines, returns List<String>.
                      Throws NullPointerException via Objects.requireNonNull if file is null.
                    - humanReadableSize(long bytes) — returns "1.2 KB", "3.4 MB", etc.
                      Throws IllegalArgumentException if bytes is negative.
                    - extension(Path file) — returns file extension without dot, or empty string.
                      Throws NullPointerException via Objects.requireNonNull if file is null.
                      Returns "" for hidden files like ".bashrc" (when lastDot index is 0).
                      Handles root paths where getFileName() may return null.
                    Use Java 21 features (java.nio.file). No external dependencies.
                    Output ONLY the raw Java source code. No markdown fences, no explanation.
                    """, "FileUtils.java");

            // Fan out all 3 generate→review workflows in parallel
            UntypedAgent parallelWorkflow = AgenticServices.parallelBuilder()
                    .name("parallelGenerateAndReview")
                    .subAgents(stringUtilsWorkflow, dateUtilsWorkflow, fileUtilsWorkflow)
                    .build();

            parallelWorkflow.invoke(Map.of("input", "generate and review utility classes"));
            System.out.println("       All 3 files generated and reviewed.\n");

            // Step 3: Commit in the worktree
            System.out.println("[3/4] Committing changes in worktree...");
            gitInDir(worktreePath, "add", "-A");
            gitInDir(worktreePath, "commit", "-m",
                "feat: add Copilot-generated utility classes (parallel generation)");

            // Step 4: Merge feature branch into main
            System.out.println("[4/4] Merging " + featureBranch + " into main...");
            for (var file : GENERATED_FILES) {
                Files.deleteIfExists(REPO_ROOT.resolve(file));
            }
            git("merge", "--no-ff", "-X", "theirs", featureBranch, "-m",
                "Merge " + featureBranch + " (parallel auto-merge via Copilot + LangChain4j)");

            System.out.println("\n=== Parallel Merge Complete ===");
            System.out.println("Generated files now on main:");
            for (var file : GENERATED_FILES) {
                System.out.println("  • " + file);
            }
        } finally {
            copilotClient = null;
            System.out.println("\nCleaning up worktree and branch...");
            tryGit("worktree", "remove", worktreePath.toString(), "--force");
            tryGit("branch", "-D", featureBranch);
            System.out.println("Done.");
        }
    }

    // ── Copilot SDK integration ─────────────────────────────────

    /** Generates code using a Copilot session with retry. Returns raw Java source. */
    private static String generateWithCopilot(String prompt) throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            var session = copilotClient.createSession(
                new SessionConfig()
                    .setModel("claude-sonnet-4.5")
                    .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                    .setSystemMessage(new SystemMessageConfig()
                        .setMode(SystemMessageMode.APPEND)
                        .setContent("""
                            <rules>
                            - You are a code generator. Output ONLY valid Java source code.
                            - No explanations, no markdown fences, no commentary.
                            - Your response MUST begin with "package com.example.copilot;"
                            - Do NOT describe what you created. Just output the code.
                            </rules>
                            """))
            ).get();

            var response = collectResponse(session, prompt);
            try {
                return extractJavaSource(response);
            } catch (RuntimeException e) {
                System.out.println("       Attempt " + attempt + " returned non-Java response, retrying...");
                if (attempt == 3) throw e;
            }
        }
        throw new RuntimeException("Unreachable");
    }

    /** Reviews code using a Copilot session and prints the verdict. */
    private static String reviewWithCopilot(String code) throws Exception {
        var session = copilotClient.createSession(
            new SessionConfig()
                .setModel("claude-sonnet-4.5")
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setSystemMessage(new SystemMessageConfig()
                    .setMode(SystemMessageMode.APPEND)
                    .setContent("""
                        <rules>
                        - You are a concise code reviewer.
                        - Your first word MUST be either "LGTM" or "FAIL".
                        - Reply "LGTM" if the code is correct, followed by a brief note.
                        - Reply "FAIL" if there are issues, followed by a brief list of problems.
                        - Focus on null safety, edge cases, and correctness.
                        </rules>
                        """))
        ).get();
        return collectResponse(session,
            "Review this Java code for correctness and edge cases:\n```java\n" + code + "\n```");
    }

    /**
     * Builds a combined generate→review workflow for a single file.
     * Loops up to 3 times: generate code via Copilot, review it, retry if review fails.
     * Throws if the review still fails after all attempts — blocking the workflow.
     */
    private static AgenticServices.AgentAction buildGenerateAndReviewAction(
            Path worktreePath, String filePath, String generatePrompt, String className) {
        return AgenticServices.agentAction(() -> {
            String lastReviewFeedback = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                // On retry, append the review feedback so Copilot can fix the issues
                var prompt = generatePrompt;
                if (lastReviewFeedback != null) {
                    prompt += "\nThe previous attempt was rejected by a code reviewer. "
                            + "Fix ALL of these issues:\n" + lastReviewFeedback
                            + "\nOutput ONLY the corrected Java source code.";
                }

                var code = generateWithCopilot(prompt);
                writeToWorktree(worktreePath, filePath, code);
                System.out.println("       ✓ " + className + " generated (attempt " + attempt + ")");

                var review = reviewWithCopilot(code);
                System.out.println("       --- " + className + " Review (attempt " + attempt + ") ---");
                System.out.println("       " + review.replace("\n", "\n       "));
                System.out.println("       --- End " + className + " Review ---");

                if (isReviewPassed(review)) {
                    System.out.println("       ✓ " + className + " passed review");
                    return;
                }
                lastReviewFeedback = review;
                System.out.println("       ✗ " + className + " failed review");
                if (attempt < 3) {
                    System.out.println("       ↻ Regenerating " + className + " with review feedback...");
                }
            }
            throw new RuntimeException(className + " failed code review after 3 attempts");
        });
    }

    /** Checks if a Copilot review response indicates approval. */
    private static boolean isReviewPassed(String review) {
        var lower = review.strip().toLowerCase();
        if (lower.startsWith("fail")) return false;
        return lower.startsWith("lgtm") || lower.startsWith("looks good") || lower.contains("lgtm");
    }

    /** Writes generated code to a file in the worktree. */
    private static void writeToWorktree(Path worktreePath, String relativePath, String code)
            throws IOException {
        var targetFile = worktreePath.resolve(relativePath);
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, code);
    }

    /** Streams a Copilot session response and collects it into a single String. */
    private static String collectResponse(CopilotSession session, String prompt)
            throws Exception {
        var result = new StringBuilder();
        var done = new CompletableFuture<Void>();

        session.on(evt -> {
            if (evt instanceof AssistantMessageEvent msg) {
                result.append(msg.getData().content());
            } else if (evt instanceof SessionErrorEvent err) {
                done.completeExceptionally(new RuntimeException(err.getData().message()));
            } else if (evt instanceof SessionIdleEvent) {
                done.complete(null);
            }
        });

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        return result.toString();
    }

    // ── Cleanup ─────────────────────────────────────────────────

    /** Removes generated files from previous runs so merges don't conflict. */
    private static void cleanupPreviousRun() throws IOException, InterruptedException {
        boolean cleaned = false;
        for (var file : GENERATED_FILES) {
            var path = REPO_ROOT.resolve(file);
            if (Files.exists(path)) {
                if (!cleaned) {
                    System.out.println("[0] Cleaning up files from a previous run...");
                    cleaned = true;
                }
                tryGit("rm", "-f", file);
                Files.deleteIfExists(path);
            }
        }
        if (cleaned) {
            tryGit("commit", "-m", "chore: remove generated utils before regeneration");
        }
    }

    // ── Git helpers ─────────────────────────────────────────────

    /**
     * Extracts valid Java source code from a Copilot response.
     * Handles markdown fences, conversational preamble/postamble, and mixed text.
     */
    private static String extractJavaSource(String response) {
        var text = response.strip();

        // 1. Try extracting from markdown code fences
        int fenceStart = text.indexOf("```");
        if (fenceStart >= 0) {
            int codeStart = text.indexOf('\n', fenceStart);
            if (codeStart >= 0) {
                int fenceEnd = text.indexOf("```", codeStart);
                if (fenceEnd > codeStart) {
                    text = text.substring(codeStart + 1, fenceEnd).strip();
                }
            }
        }

        // 2. Find the start of actual Java source (package or import declaration)
        int pkgIdx = text.indexOf("package ");
        int impIdx = text.indexOf("import ");
        int javaStart = -1;
        if (pkgIdx >= 0 && impIdx >= 0) javaStart = Math.min(pkgIdx, impIdx);
        else if (pkgIdx >= 0) javaStart = pkgIdx;
        else if (impIdx >= 0) javaStart = impIdx;

        if (javaStart > 0) {
            text = text.substring(javaStart);
        }

        // 3. Trim any trailing conversational text after the last closing brace
        int lastBrace = text.lastIndexOf('}');
        if (lastBrace >= 0 && lastBrace < text.length() - 1) {
            text = text.substring(0, lastBrace + 1);
        }

        // 4. Validate we have something that looks like Java
        if (!text.startsWith("package ") && !text.startsWith("import ")) {
            throw new RuntimeException(
                "Copilot did not return valid Java source. Response starts with: "
                + text.substring(0, Math.min(100, text.length())));
        }

        return text;
    }

    private static void git(String... args) throws IOException, InterruptedException {
        gitInDir(REPO_ROOT, args);
    }

    private static boolean tryGit(String... args) throws IOException, InterruptedException {
        try { gitInDir(REPO_ROOT, args); return true; }
        catch (RuntimeException e) { return false; }
    }

    private static void gitInDir(Path dir, String... args)
            throws IOException, InterruptedException {
        var cmd = new String[args.length + 1];
        cmd[0] = "git";
        System.arraycopy(args, 0, cmd, 1, args.length);
        var process = new ProcessBuilder(cmd).directory(dir.toFile()).inheritIO().start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("git command failed (exit " + exitCode + "): " + String.join(" ", cmd));
        }
    }
}
