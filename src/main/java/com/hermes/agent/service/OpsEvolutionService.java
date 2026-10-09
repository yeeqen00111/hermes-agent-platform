package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.OpsExperience;
import com.hermes.agent.mapper.OpsExperienceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 运维智能体「自我进化」引擎（白板·Agent 层）。
 *
 * <p>闭环四步：
 * <ol>
 *   <li><b>记录</b> {@link #capture}：把「问题 → 方案」沉淀；同一问题指纹自动归并强化；</li>
 *   <li><b>复用</b> {@link #retrieve}：按相关性检索并计入复用次数；</li>
 *   <li><b>反馈</b> {@link #feedback}：有效/无效反馈重算置信度（Laplace 平滑）；</li>
 *   <li><b>巩固</b> {@link #evolve}：久未复用衰减置信度，低置信度自动淘汰。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsEvolutionService {

    /** 判定淘汰所需的最少反馈次数 */
    private static final int MIN_FEEDBACK = 5;
    /** 置信度淘汰线 */
    private static final double DEPRECATE_BELOW = 0.2;
    /** 久未复用衰减窗口（天） */
    private static final int DECAY_AFTER_DAYS = 30;

    private final OpsExperienceMapper experienceMapper;

    // ---------- 1. 记录 ----------

    public OpsExperience capture(OpsExperience in) {
        if (in.getProblem() == null || in.getProblem().isBlank()) {
            throw new IllegalArgumentException("problem 必填");
        }
        String agentCode = blankTo(in.getAgentCode(), "assistant");
        String key = problemKey(in.getProblem());
        OpsExperience existing = experienceMapper.selectOne(new LambdaQueryWrapper<OpsExperience>()
                .eq(OpsExperience::getAgentCode, agentCode)
                .eq(OpsExperience::getProblemKey, key));

        if (existing != null) {
            // 归并强化：补充/更新方案，视为一次“再观测”的正向证据
            if (notBlank(in.getSolution())) {
                existing.setSolution(in.getSolution());
            }
            if (notBlank(in.getCause())) {
                existing.setCause(in.getCause());
            }
            if (notBlank(in.getTags())) {
                existing.setTags(mergeTags(existing.getTags(), in.getTags()));
            }
            if (notBlank(in.getSystemName())) {
                existing.setSystemName(in.getSystemName());
            }
            existing.setSuccessCount(nvl(existing.getSuccessCount()) + 1);
            existing.setConfidence(confidence(nvl(existing.getSuccessCount()), nvl(existing.getFailCount())));
            existing.setStatus("ACTIVE");
            experienceMapper.updateById(existing);
            return existing;
        }

        OpsExperience exp = new OpsExperience();
        exp.setExpCode("exp-" + UUID.randomUUID().toString().substring(0, 8));
        exp.setAgentCode(agentCode);
        exp.setProblem(in.getProblem());
        exp.setProblemKey(key);
        exp.setCause(in.getCause());
        exp.setSolution(in.getSolution());
        exp.setTags(in.getTags());
        exp.setSystemName(in.getSystemName());
        exp.setSourceType(blankTo(in.getSourceType(), "MANUAL"));
        exp.setSourceRef(in.getSourceRef());
        exp.setHits(0);
        exp.setSuccessCount(in.getSuccessCount() == null ? 0 : in.getSuccessCount());
        exp.setFailCount(in.getFailCount() == null ? 0 : in.getFailCount());
        exp.setConfidence(confidence(exp.getSuccessCount(), exp.getFailCount()));
        exp.setStatus("ACTIVE");
        experienceMapper.insert(exp);
        return exp;
    }

    // ---------- 2. 复用 ----------

    public List<OpsExperience> list(String agentCode, String keyword, String status) {
        LambdaQueryWrapper<OpsExperience> wrapper = new LambdaQueryWrapper<OpsExperience>()
                .orderByDesc(OpsExperience::getConfidence)
                .orderByDesc(OpsExperience::getHits)
                .orderByDesc(OpsExperience::getId);
        if (notBlank(agentCode)) {
            wrapper.eq(OpsExperience::getAgentCode, agentCode);
        }
        if (notBlank(status)) {
            wrapper.eq(OpsExperience::getStatus, status);
        }
        if (notBlank(keyword)) {
            wrapper.and(w -> w.like(OpsExperience::getProblem, keyword)
                    .or().like(OpsExperience::getTags, keyword)
                    .or().like(OpsExperience::getSolution, keyword));
        }
        return experienceMapper.selectList(wrapper);
    }

    /**
     * 按相关性检索可复用经验，并计入复用次数（命中即强化曝光）。
     */
    public List<Map<String, Object>> retrieve(String query, String agentCode, int topK) {
        List<String> tokens = tokens(query);
        LambdaQueryWrapper<OpsExperience> wrapper = new LambdaQueryWrapper<OpsExperience>()
                .eq(OpsExperience::getStatus, "ACTIVE");
        if (notBlank(agentCode)) {
            wrapper.eq(OpsExperience::getAgentCode, agentCode);
        }
        List<OpsExperience> all = experienceMapper.selectList(wrapper);

        List<double[]> scored = new ArrayList<>(); // [index, score]
        List<OpsExperience> ranked = new ArrayList<>();
        for (OpsExperience e : all) {
            double match = matchScore(e, tokens);
            if (!tokens.isEmpty() && match <= 0) {
                continue;
            }
            scored.add(new double[]{ranked.size(), match + nvlD(e.getConfidence())});
            ranked.add(e);
        }
        scored.sort((a, b) -> Double.compare(b[1], a[1]));

        int limit = Math.max(1, Math.min(topK, 20));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            OpsExperience e = ranked.get((int) scored.get(i)[0]);
            e.setHits(nvl(e.getHits()) + 1);
            e.setLastUsedTime(LocalDateTime.now());
            experienceMapper.updateById(e);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("expCode", e.getExpCode());
            row.put("problem", e.getProblem());
            row.put("cause", e.getCause());
            row.put("solution", e.getSolution());
            row.put("tags", e.getTags());
            row.put("confidence", e.getConfidence());
            row.put("score", Math.round(scored.get(i)[1] * 100) / 100.0);
            row.put("hits", e.getHits());
            out.add(row);
        }
        return out;
    }

    // ---------- 3. 反馈 ----------

    public OpsExperience feedback(Long id, boolean useful) {
        OpsExperience exp = experienceMapper.selectById(id);
        if (exp == null) {
            return null;
        }
        if (useful) {
            exp.setSuccessCount(nvl(exp.getSuccessCount()) + 1);
        } else {
            exp.setFailCount(nvl(exp.getFailCount()) + 1);
        }
        exp.setConfidence(confidence(nvl(exp.getSuccessCount()), nvl(exp.getFailCount())));
        if (exp.getConfidence() < DEPRECATE_BELOW
                && nvl(exp.getSuccessCount()) + nvl(exp.getFailCount()) >= MIN_FEEDBACK) {
            exp.setStatus("DEPRECATED");
        }
        experienceMapper.updateById(exp);
        return exp;
    }

    // ---------- 4. 巩固（定时自进化） ----------

    public Map<String, Object> evolve() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(DECAY_AFTER_DAYS);
        List<OpsExperience> actives = experienceMapper.selectList(new LambdaQueryWrapper<OpsExperience>()
                .eq(OpsExperience::getStatus, "ACTIVE"));
        int decayed = 0;
        int deprecated = 0;
        for (OpsExperience e : actives) {
            LocalDateTime ref = e.getLastUsedTime() != null ? e.getLastUsedTime() : e.getCreateTime();
            boolean stale = ref == null || ref.isBefore(threshold);
            if (!stale) {
                continue;
            }
            double next = Math.max(0.0, round4(nvlD(e.getConfidence()) * 0.9));
            e.setConfidence(next);
            decayed++;
            if (next < DEPRECATE_BELOW
                    && nvl(e.getSuccessCount()) + nvl(e.getFailCount()) >= MIN_FEEDBACK) {
                e.setStatus("DEPRECATED");
                deprecated++;
            }
            experienceMapper.updateById(e);
        }
        log.info("自进化巩固完成: scanned={}, decayed={}, deprecated={}", actives.size(), decayed, deprecated);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scanned", actives.size());
        out.put("decayed", decayed);
        out.put("deprecated", deprecated);
        return out;
    }

    // ---------- 统计 / 注入 ----------

    public Map<String, Object> stats() {
        List<OpsExperience> all = experienceMapper.selectList(new LambdaQueryWrapper<>());
        int active = 0;
        int deprecated = 0;
        double confSum = 0;
        long hits = 0;
        for (OpsExperience e : all) {
            if ("DEPRECATED".equals(e.getStatus())) {
                deprecated++;
            } else {
                active++;
            }
            confSum += nvlD(e.getConfidence());
            hits += nvl(e.getHits());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("active", active);
        out.put("deprecated", deprecated);
        out.put("totalHits", hits);
        out.put("avgConfidence", all.isEmpty() ? 0.0 : round4(confSum / all.size()));
        return out;
    }

    /**
     * 供人格包装配注入的“运维经验”块（取置信度最高的 topK 条 ACTIVE），不计复用次数。
     */
    public List<String> experienceBlocks(String agentCode, int topK) {
        List<OpsExperience> list = experienceMapper.selectList(new LambdaQueryWrapper<OpsExperience>()
                .eq(OpsExperience::getStatus, "ACTIVE")
                .eq(OpsExperience::getAgentCode, agentCode)
                .orderByDesc(OpsExperience::getConfidence)
                .orderByDesc(OpsExperience::getHits)
                .last("LIMIT " + Math.max(1, Math.min(topK, 10))));
        List<String> blocks = new ArrayList<>();
        for (OpsExperience e : list) {
            StringBuilder sb = new StringBuilder("- 问题: ").append(e.getProblem());
            if (notBlank(e.getCause())) {
                sb.append("；原因: ").append(e.getCause());
            }
            if (notBlank(e.getSolution())) {
                sb.append("；方案: ").append(e.getSolution());
            }
            sb.append("（置信度 ").append(e.getConfidence()).append("，复用 ").append(nvl(e.getHits())).append(" 次）");
            blocks.add(sb.toString());
        }
        return blocks;
    }

    // ---------- 内部 ----------

    private double matchScore(OpsExperience e, List<String> tokens) {
        double s = 0;
        String problem = lower(e.getProblem());
        String tags = lower(e.getTags());
        String body = lower(e.getCause()) + " " + lower(e.getSolution());
        for (String t : tokens) {
            if (problem.contains(t)) {
                s += 2;
            }
            if (tags.contains(t)) {
                s += 2;
            }
            if (body.contains(t)) {
                s += 1;
            }
        }
        return s;
    }

    private static List<String> tokens(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String t : query.toLowerCase(Locale.ROOT).split("[\\s,，。；;、/|()（）\\[\\]{}]+")) {
            if (t.length() >= 2) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private static String problemKey(String problem) {
        String normalized = problem.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "");
        if (normalized.length() > 160) {
            normalized = normalized.substring(0, 160);
        }
        return normalized;
    }

    /** Laplace 平滑：新条目 0.5，正向累积趋近 1。 */
    private static double confidence(int success, int fail) {
        return round4((success + 1.0) / (success + fail + 2.0));
    }

    private static String mergeTags(String existing, String incoming) {
        List<String> merged = new ArrayList<>();
        for (String part : (existing + "," + incoming).split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && !merged.contains(t)) {
                merged.add(t);
            }
        }
        return String.join(",", merged);
    }

    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private static double nvlD(Double v) {
        return v == null ? 0.0 : v;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankTo(String s, String fallback) {
        return notBlank(s) ? s : fallback;
    }
}
