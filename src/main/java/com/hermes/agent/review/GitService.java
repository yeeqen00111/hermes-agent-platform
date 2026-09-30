package com.hermes.agent.review;

import com.hermes.agent.entity.CrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 代码仓库下载/更新：平台侧隔离层持有凭据，Agent只拿到提交信息与diff摘要
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitService {

    /** git 空树哈希，作为无起始revision时的diff基线 */
    private static final String EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";

    private final CredentialService credentialService;

    public Path workspaceOf(CrRepository repo) {
        String dir = repo.getWorkspaceDir();
        if (dir == null || dir.isBlank()) {
            dir = "./data/repos/" + repo.getRepoCode();
        }
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    /**
     * clone 或 fetch+reset 到远端分支头，返回HEAD revision
     */
    public String sync(CrRepository repo) {
        Path dir = workspaceOf(repo);
        CredentialService.ResolvedCredential cred = credentialService.resolve(repo.getCredentialId());
        String url = authedUrl(repo.getRepoUrl(), cred);
        try {
            if (!Files.exists(dir.resolve(".git"))) {
                Files.createDirectories(dir.getParent());
                git(null, "clone", "--branch", repo.getDefaultBranch(), url, dir.toString());
            } else {
                git(dir, "fetch", "origin");
                git(dir, "checkout", repo.getDefaultBranch());
                git(dir, "reset", "--hard", "origin/" + repo.getDefaultBranch());
            }
            String head = git(dir, "rev-parse", "HEAD").trim();
            repo.setLastRevision(head);
            repo.setLastSyncTime(LocalDateTime.now());
            return head;
        } catch (Exception e) {
            throw new IllegalStateException("仓库同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提交区间信息：提交列表 + 变更文件统计
     */
    public String commitRangeInfo(CrRepository repo, String branch, String startRevision) {
        Path dir = workspaceOf(repo);
        StringBuilder sb = new StringBuilder();
        boolean hasStart = startRevision != null && !startRevision.isBlank();
        sb.append("## 提交列表\n");
        sb.append(hasStart
                ? git(dir, "log", "--pretty=format:%h %an %ad %s", "--date=iso", startRevision + "..HEAD")
                : git(dir, "log", "-n", "50", "--pretty=format:%h %an %ad %s", "--date=iso"));
        sb.append("\n\n## 变更文件统计\n");
        sb.append(git(dir, "diff", "--numstat", hasStart ? startRevision : EMPTY_TREE, "HEAD"));
        String info = sb.toString();
        return info.length() > 20000 ? info.substring(0, 20000) + "\n...[truncated]" : info;
    }

    private String authedUrl(String url, CredentialService.ResolvedCredential cred) {
        if (cred == null || !url.startsWith("https://")) {
            return url;
        }
        String user = cred.username() == null ? "" : cred.username();
        return url.replaceFirst("https://", "https://" + user + ":" + cred.secret() + "@");
    }

    private String git(Path workDir, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workDir != null) {
                pb.directory(new File(workDir.toString()));
            }
            pb.redirectErrorStream(false);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("git命令超时");
            }
            if (p.exitValue() != 0) {
                throw new IllegalStateException(sanitize(err.isBlank() ? out : err));
            }
            return out;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("git执行异常: " + e.getMessage(), e);
        }
    }

    /**
     * 错误信息可能回显带凭据的URL，必须脱敏
     */
    private String sanitize(String s) {
        return s.replaceAll("://[^@\\s]+@", "://***@");
    }
}
