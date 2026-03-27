package com.example.copilot;

import com.github.copilot.sdk.*;
import com.github.copilot.sdk.events.*;
import com.github.copilot.sdk.json.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Worktree Auto-Merge Workflow
 *
 * Demonstrates using Git worktrees + Copilot SDK to:
 *   1. Create a Git worktree for a feature branch
 *   2. Use Copilot to generate code in the worktree
 *   3. Write generated code to the worktree
 *   4. Use Copilot to review the generated code
 *   5. Commit changes in the worktree
 *   6. Merge the feature branch back into main and clean up
 *
 * Idempotent: can be run repeatedly without manual cleanup.
 */
public class WorktreeAutoMergeExample {

    private static final Path REPO_ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final String GENERATED_FILE = "src/main/java/com/example/copilot/StringUtils.java";

    public static void main(String[] args) throws Exception {
        var featureBranch = "feature/copilot-generated-" + System.currentTimeMillis();
        var worktreePath = REPO_ROOT.resolve("../wt-" + featureBranch.replace("/", "-"));

        System.out.println("=== Worktree Auto-Merge Workflow ===\n");
        System.out.println("Repo root:      " + REPO_ROOT);
        System.out.println("Feature branch: " + featureBranch);
        System.out.println("Worktree path:  " + worktreePath);
        System.out.println();

        try (var client = new CopilotClient()) {
            client.start().get();

            // Step 0: Clean up StringUtils.java from any previous run so merges don't conflict
            cleanupPreviousRun();

            // Step 1: Create feature branch and worktree
            System.out.println("[1/6] Creating feature branch and worktree...");
            git("branch", featureBranch);
            git("worktree", "add", worktreePath.toString(), featureBranch);

            // Step 2: Ask Copilot to generate code
            System.out.println("[2/6] Asking Copilot to generate code...");
            var generatedCode = generateCodeWithCopilot(client, """
                Generate a Java utility class called StringUtils in package com.example.copilot
                with these static methods:
                - reverse(String s) — reverses a string, handles null by returning null
                - isPalindrome(String s) — checks if string is a palindrome (case-insensitive)
                - truncate(String s, int maxLen) — truncates with "..." if longer than maxLen
                Use Java 21 features. No external dependencies.
                Output ONLY the raw Java source code. No markdown fences, no explanation.
                """);

            // Step 3: Write generated code into the worktree
            System.out.println("[3/6] Writing generated code to worktree...");
            generatedCode = extractJavaSource(generatedCode);
            var targetFile = worktreePath.resolve(GENERATED_FILE);
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, generatedCode);
            System.out.println("       Wrote " + generatedCode.length() + " chars to " + targetFile.getFileName());

            // Step 4: Ask Copilot to review the code
            System.out.println("[4/6] Asking Copilot to review the generated code...");
            var review = reviewCodeWithCopilot(client, generatedCode);
            System.out.println("\n--- Code Review ---");
            System.out.println(review);
            System.out.println("--- End Review ---\n");

            // Step 5: Commit in the worktree
            System.out.println("[5/6] Committing changes in worktree...");
            gitInDir(worktreePath, "add", "-A");
            gitInDir(worktreePath, "commit", "-m",
                "feat: add Copilot-generated StringUtils utility class");

            // Step 6: Merge feature branch into main
            System.out.println("[6/6] Merging " + featureBranch + " into main...");
            // Ensure no local StringUtils.java blocks the merge (belt-and-suspenders)
            Files.deleteIfExists(REPO_ROOT.resolve(GENERATED_FILE));
            git("merge", "--no-ff", "-X", "theirs", featureBranch, "-m",
                "Merge " + featureBranch + " (auto-merge via Copilot workflow)");

            System.out.println("\n=== Merge Complete ===");
        } finally {
            // Clean up: remove worktree and delete feature branch
            System.out.println("\nCleaning up worktree and branch...");
            tryGit("worktree", "remove", worktreePath.toString(), "--force");
            tryGit("branch", "-D", featureBranch);
            System.out.println("Done.");
        }
    }

    /**
     * Removes StringUtils.java if it exists (tracked or untracked) so repeated runs
     * start from a clean state.
     */
    private static void cleanupPreviousRun() throws IOException, InterruptedException {
        var file = REPO_ROOT.resolve(GENERATED_FILE);
        if (Files.exists(file)) {
            System.out.println("[0] Cleaning up StringUtils.java from a previous run...");
            // Try git rm (for tracked files), then delete manually (for untracked files)
            if (!tryGit("rm", "-f", GENERATED_FILE)) {
                Files.deleteIfExists(file);
            }
            // Commit the removal if there are staged changes
            tryGit("commit", "-m", "chore: remove StringUtils.java before regeneration");
        }
    }

    /**
     * Uses Copilot to generate code with retry. Returns raw Java source.
     */
    private static String generateCodeWithCopilot(CopilotClient client, String prompt)
            throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            var session = client.createSession(
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

    /**
     * Uses Copilot to review code and return a concise verdict.
     */
    private static String reviewCodeWithCopilot(CopilotClient client, String code)
            throws Exception {
        var session = client.createSession(
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

    /**
     * Streams a Copilot session response and collects it into a single String.
     */
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

    // ── Helpers ──────────────────────────────────────────────────

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

    /** Runs a git command, returning true on success or false on non-zero exit. */
    private static boolean tryGit(String... args) throws IOException, InterruptedException {
        try {
            gitInDir(REPO_ROOT, args);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void gitInDir(Path dir, String... args)
            throws IOException, InterruptedException {
        var cmd = new String[args.length + 1];
        cmd[0] = "git";
        System.arraycopy(args, 0, cmd, 1, args.length);

        var process = new ProcessBuilder(cmd)
            .directory(dir.toFile())
            .inheritIO()
            .start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(
                "git command failed (exit " + exitCode + "): " + String.join(" ", cmd));
        }
    }
}
