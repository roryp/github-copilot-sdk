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
 *   2. Fan out 3 Copilot code-generation agents in parallel (StringUtils, DateUtils, FileUtils)
 *   3. Write all generated files into the worktree
 *   4. Fan out 3 Copilot review agents in parallel
 *   5. Commit all changes in the worktree
 *   6. Merge the feature branch back into main and clean up
 *
 * The parallel fan-out uses AgenticServices.parallelBuilder() from langchain4j-agentic,
 * where each sub-agent wraps a Copilot SDK session that generates one utility class.
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
            System.out.println("[1/6] Creating feature branch and worktree...");
            git("branch", featureBranch);
            git("worktree", "add", worktreePath.toString(), featureBranch);

            // Step 2: Parallel code generation — 3 agents run concurrently via parallelBuilder()
            System.out.println("[2/6] Generating 3 utility classes in parallel...");

            var generateStringUtils = AgenticServices.agentAction(() -> {
                var code = generateWithCopilot("""
                    Generate a Java utility class called StringUtils in package com.example.copilot
                    with these static methods:
                    - reverse(String s) — reverses a string, handles null by returning null
                    - isPalindrome(String s) — checks if string is a palindrome (case-insensitive)
                    - truncate(String s, int maxLen) — truncates with "..." if longer than maxLen
                    Use Java 21 features. No external dependencies.
                    Output ONLY the raw Java source code. No markdown fences, no explanation.
                    """);
                writeToWorktree(worktreePath, GENERATED_FILES[0], code);
                System.out.println("       ✓ StringUtils.java generated");
            });

            var generateDateUtils = AgenticServices.agentAction(() -> {
                var code = generateWithCopilot("""
                    Generate a Java utility class called DateUtils in package com.example.copilot
                    with these static methods:
                    - daysUntil(LocalDate target) — returns days from today to target date
                    - isWeekend(LocalDate date) — returns true if Saturday or Sunday
                    - formatRelative(LocalDate date) — returns "today", "yesterday", "3 days ago", etc.
                    Use Java 21 features (java.time). No external dependencies.
                    Output ONLY the raw Java source code. No markdown fences, no explanation.
                    """);
                writeToWorktree(worktreePath, GENERATED_FILES[1], code);
                System.out.println("       ✓ DateUtils.java generated");
            });

            var generateFileUtils = AgenticServices.agentAction(() -> {
                var code = generateWithCopilot("""
                    Generate a Java utility class called FileUtils in package com.example.copilot
                    with these static methods:
                    - readLines(Path file) — reads all lines, returns List<String>
                    - humanReadableSize(long bytes) — returns "1.2 KB", "3.4 MB", etc.
                    - extension(Path file) — returns file extension without dot, or empty string
                    Use Java 21 features (java.nio.file). No external dependencies.
                    Output ONLY the raw Java source code. No markdown fences, no explanation.
                    """);
                writeToWorktree(worktreePath, GENERATED_FILES[2], code);
                System.out.println("       ✓ FileUtils.java generated");
            });

            // Fan out all 3 generators in parallel using LangChain4j's parallelBuilder()
            UntypedAgent parallelGenerator = AgenticServices.parallelBuilder()
                    .name("parallelCodeGenerator")
                    .subAgents(generateStringUtils, generateDateUtils, generateFileUtils)
                    .build();

            parallelGenerator.invoke(Map.of("input", "generate utility classes"));
            System.out.println("       All 3 files generated in parallel.\n");

            // Step 3: Parallel code review — 3 review agents run concurrently
            System.out.println("[3/6] Reviewing all 3 files in parallel...");

            var reviewStringUtils = buildReviewAction(worktreePath, GENERATED_FILES[0]);
            var reviewDateUtils = buildReviewAction(worktreePath, GENERATED_FILES[1]);
            var reviewFileUtils = buildReviewAction(worktreePath, GENERATED_FILES[2]);

            UntypedAgent parallelReviewer = AgenticServices.parallelBuilder()
                    .name("parallelCodeReviewer")
                    .subAgents(reviewStringUtils, reviewDateUtils, reviewFileUtils)
                    .build();

            parallelReviewer.invoke(Map.of("input", "review utility classes"));
            System.out.println();

            // Step 4: Commit in the worktree
            System.out.println("[4/6] Committing changes in worktree...");
            gitInDir(worktreePath, "add", "-A");
            gitInDir(worktreePath, "commit", "-m",
                "feat: add Copilot-generated utility classes (parallel generation)");

            // Step 5: Merge feature branch into main
            System.out.println("[5/6] Merging " + featureBranch + " into main...");
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
                        - Reply LGTM if the code is correct, or list issues briefly.
                        - Focus on null safety, edge cases, and correctness.
                        </rules>
                        """))
        ).get();
        return collectResponse(session,
            "Review this Java code for correctness and edge cases:\n```java\n" + code + "\n```");
    }

    /** Builds a review action that reads a file from the worktree and reviews it. */
    private static AgenticServices.AgentAction buildReviewAction(Path worktreePath, String filePath) {
        return AgenticServices.agentAction(() -> {
            var code = Files.readString(worktreePath.resolve(filePath));
            var review = reviewWithCopilot(code);
            var fileName = Path.of(filePath).getFileName();
            System.out.println("       --- " + fileName + " Review ---");
            System.out.println("       " + review.replace("\n", "\n       "));
            System.out.println("       --- End " + fileName + " Review ---");
        });
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
