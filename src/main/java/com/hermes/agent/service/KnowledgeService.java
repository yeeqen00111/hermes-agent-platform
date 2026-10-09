package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.KnowledgeNode;
import com.hermes.agent.mapper.KnowledgeNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 运维知识库（白板·业务层·智能运维）：
 * 目录层级 markdown 文档（FOLDER/DOC）+ 上传转 markdown（md/txt 直存、html 转文本、csv 转表格、其余入代码块）。
 * <p>检索数据面仍走 {@code knowledge.search} 工具（Java 平台，见 interface-contract §7.5）；此处为文档管理面。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeNodeMapper nodeMapper;

    // ---------- 目录 / 文档 ----------

    /** 仅取元信息（不含正文），用于树与列表 */
    public List<KnowledgeNode> listMeta() {
        return nodeMapper.selectList(new LambdaQueryWrapper<KnowledgeNode>()
                .select(KnowledgeNode::getId, KnowledgeNode::getCode, KnowledgeNode::getName,
                        KnowledgeNode::getParentId, KnowledgeNode::getNodeType, KnowledgeNode::getTitle,
                        KnowledgeNode::getSortNo, KnowledgeNode::getEnabled, KnowledgeNode::getRemark,
                        KnowledgeNode::getCreateTime, KnowledgeNode::getUpdateTime)
                .orderByAsc(KnowledgeNode::getSortNo)
                .orderByAsc(KnowledgeNode::getId));
    }

    /** 目录层级树 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> tree() {
        List<KnowledgeNode> all = listMeta();
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (KnowledgeNode n : all) {
            byId.put(n.getId(), toMap(n));
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (KnowledgeNode n : all) {
            Map<String, Object> node = byId.get(n.getId());
            Long pid = n.getParentId();
            if (pid == null || pid == 0 || !byId.containsKey(pid)) {
                roots.add(node);
            } else {
                ((List<Map<String, Object>>) byId.get(pid).get("children")).add(node);
            }
        }
        return roots;
    }

    public KnowledgeNode get(Long id) {
        return nodeMapper.selectById(id);
    }

    public KnowledgeNode saveNode(KnowledgeNode node) {
        if (node.getParentId() == null) {
            node.setParentId(0L);
        }
        if (node.getNodeType() == null || node.getNodeType().isBlank()) {
            node.setNodeType("DOC");
        } else {
            node.setNodeType(node.getNodeType().toUpperCase());
        }
        if (node.getSortNo() == null) {
            node.setSortNo(0);
        }
        if (node.getEnabled() == null) {
            node.setEnabled(1);
        }
        if (node.getCode() == null || node.getCode().isBlank()) {
            node.setCode("kb-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (node.getId() != null) {
            nodeMapper.updateById(node);
        } else {
            nodeMapper.insert(node);
        }
        return node;
    }

    /** 删除节点及其全部子孙 */
    public int deleteNode(Long id) {
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        collectDescendants(id, ids);
        return nodeMapper.deleteBatchIds(ids);
    }

    private void collectDescendants(Long parentId, List<Long> acc) {
        List<KnowledgeNode> children = nodeMapper.selectList(new LambdaQueryWrapper<KnowledgeNode>()
                .select(KnowledgeNode::getId)
                .eq(KnowledgeNode::getParentId, parentId));
        for (KnowledgeNode c : children) {
            acc.add(c.getId());
            collectDescendants(c.getId(), acc);
        }
    }

    // ---------- 上传转 markdown ----------

    public KnowledgeNode upload(String filename, byte[] bytes, Long parentId) {
        String name = (filename == null || filename.isBlank()) ? "上传文档" : filename;
        KnowledgeNode node = new KnowledgeNode();
        node.setName(name);
        node.setTitle(name);
        node.setContent(toMarkdown(name, bytes));
        node.setNodeType("DOC");
        node.setParentId(parentId);
        return saveNode(node);
    }

    /** 上传内容转 markdown：md/txt 直存、html 转文本、csv 转表格、其余入代码块 */
    public String toMarkdown(String filename, byte[] bytes) {
        String text = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        String ext = extension(filename);
        return switch (ext) {
            case "md", "markdown", "txt" -> text;
            case "html", "htm" -> htmlToText(text);
            case "csv" -> csvToMarkdownTable(text);
            default -> "# " + (filename == null ? "上传内容" : filename) + "\n\n```" + ext + "\n" + text + "\n```\n";
        };
    }

    private String htmlToText(String html) {
        String s = html.replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li|tr)>", "\n")
                .replaceAll("<[^>]+>", "");
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        return s.replaceAll("\n{3,}", "\n\n").trim();
    }

    private String csvToMarkdownTable(String csv) {
        String[] lines = csv.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] cells = lines[i].split(",", -1);
            sb.append("|");
            for (String c : cells) {
                sb.append(" ").append(c.trim().replace("|", "\\|")).append(" |");
            }
            sb.append("\n");
            if (i == 0) {
                sb.append("|").append(" --- |".repeat(cells.length)).append("\n");
            }
        }
        return sb.toString();
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "txt";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    // ---------- 检索（本地全文，补 knowledge.search 数据面） ----------

    public List<Map<String, Object>> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<KnowledgeNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<KnowledgeNode>()
                .eq(KnowledgeNode::getNodeType, "DOC")
                .and(w -> w.like(KnowledgeNode::getTitle, keyword).or().like(KnowledgeNode::getContent, keyword))
                .orderByDesc(KnowledgeNode::getUpdateTime)
                .last("LIMIT 50"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (KnowledgeNode n : nodes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", n.getId());
            row.put("name", n.getName());
            row.put("title", n.getTitle());
            String content = n.getContent() == null ? "" : n.getContent();
            int idx = content.toLowerCase().indexOf(keyword.toLowerCase());
            int from = Math.max(0, idx - 40);
            row.put("snippet", content.length() > from + 160 ? content.substring(from, from + 160) : content.substring(from));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> toMap(KnowledgeNode n) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", n.getId());
        map.put("code", n.getCode());
        map.put("name", n.getName());
        map.put("parentId", n.getParentId());
        map.put("nodeType", n.getNodeType());
        map.put("title", n.getTitle());
        map.put("sortNo", n.getSortNo());
        map.put("enabled", n.getEnabled());
        map.put("children", new ArrayList<Map<String, Object>>());
        return map;
    }
}
