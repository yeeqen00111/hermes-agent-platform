package com.hermes.agent.api;

import com.hermes.agent.entity.KnowledgeNode;
import com.hermes.agent.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 运维知识库 API（白板·业务层·智能运维）：目录层级 markdown 文档 + 上传转 markdown。
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService service;

    @GetMapping("/tree")
    public List<Map<String, Object>> tree() {
        return service.tree();
    }

    @GetMapping("/nodes")
    public List<KnowledgeNode> listNodes() {
        return service.listMeta();
    }

    @GetMapping("/nodes/{id}")
    public KnowledgeNode getNode(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/nodes")
    public KnowledgeNode saveNode(@RequestBody KnowledgeNode node) {
        return service.saveNode(node);
    }

    @DeleteMapping("/nodes/{id}")
    public Map<String, Object> deleteNode(@PathVariable Long id) {
        return Map.of("success", true, "deleted", service.deleteNode(id));
    }

    @PostMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q) {
        return service.search(q);
    }

    /** 上传文件并转换为 markdown 入库 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeNode upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(required = false) Long parentId) throws IOException {
        return service.upload(file.getOriginalFilename(), file.getBytes(), parentId);
    }
}
