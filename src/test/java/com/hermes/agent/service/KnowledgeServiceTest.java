package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.KnowledgeNode;
import com.hermes.agent.mapper.KnowledgeNodeMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运维知识库测试：目录层级、正文存取、级联删除、上传转换（md 直存 / csv 转表 / html 转文本）、全文检索。
 */
class KnowledgeServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private KnowledgeService service;
    private KnowledgeNodeMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(KnowledgeNodeMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        mapper = sqlSession.getMapper(KnowledgeNodeMapper.class);
        service = new KnowledgeService(mapper);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    private KnowledgeNode node(String name, String type, Long parentId, String content) {
        KnowledgeNode n = new KnowledgeNode();
        n.setName(name);
        n.setTitle(name);
        n.setNodeType(type);
        n.setParentId(parentId);
        n.setContent(content);
        return service.saveNode(n);
    }

    @Test
    @SuppressWarnings("unchecked")
    void treeNestsChildren() {
        KnowledgeNode folder = node("运维手册", "FOLDER", 0L, null);
        KnowledgeNode doc = node("告警处置", "DOC", folder.getId(), "# 处置\n步骤");
        node("提级流程", "FOLDER", folder.getId(), null);
        node("部署文档", "DOC", 0L, "内容");

        List<Map<String, Object>> roots = service.tree();

        assertThat(roots).hasSize(2);
        Map<String, Object> folderNode = roots.stream()
                .filter(r -> "运维手册".equals(r.get("name"))).findFirst().orElseThrow();
        List<Map<String, Object>> children = (List<Map<String, Object>>) folderNode.get("children");
        assertThat(children).hasSize(2);
        assertThat(children.get(0)).containsEntry("name", "告警处置").containsEntry("nodeType", "DOC");
        assertThat(doc.getCode()).startsWith("kb-");
    }

    @Test
    void saveAndGetContentRoundtrip() {
        KnowledgeNode saved = node("规范", "DOC", 0L, "# 命名规范\n- 小写下划线");
        KnowledgeNode loaded = service.get(saved.getId());
        assertThat(loaded.getContent()).contains("小写下划线");
        assertThat(loaded.getCode()).isNotBlank();
    }

    @Test
    void deleteRemovesDescendants() {
        KnowledgeNode folder = node("目录A", "FOLDER", 0L, null);
        node("子文档", "DOC", folder.getId(), "x");
        node("孙文档", "DOC", folder.getId(), "y");
        node("旁支", "DOC", 0L, "z");

        int deleted = service.deleteNode(folder.getId());

        assertThat(deleted).isEqualTo(3);
        assertThat(service.listMeta()).hasSize(1);
    }

    @Test
    void uploadMarkdownPassthrough() {
        byte[] bytes = "# 已有标题\n正文".getBytes(StandardCharsets.UTF_8);
        assertThat(service.toMarkdown("runbook.md", bytes)).isEqualTo("# 已有标题\n正文");
    }

    @Test
    void uploadCsvBecomesMarkdownTable() {
        byte[] bytes = "名称,级别,处置\n超时,ERROR,重启".getBytes(StandardCharsets.UTF_8);
        String md = service.toMarkdown("matrix.csv", bytes);
        assertThat(md).contains("| 名称 | 级别 | 处置 |").contains("| --- |").contains("| 超时 | ERROR | 重启 |");
    }

    @Test
    void uploadStripsUtf8Bom() {
        byte[] bytes = "\uFEFF# 标题".getBytes(StandardCharsets.UTF_8);
        assertThat(service.toMarkdown("doc.md", bytes)).isEqualTo("# 标题");
    }

    @Test
    void uploadHtmlStrippedToText() {
        byte[] bytes = "<html><body><h1>标题</h1><p>第一段</p><script>x()</script></body></html>"
                .getBytes(StandardCharsets.UTF_8);
        String md = service.toMarkdown("page.html", bytes);
        assertThat(md).contains("标题").contains("第一段").doesNotContain("<h1>").doesNotContain("x()");
    }

    @Test
    void searchFindsDocsByContent() {
        node("告警手册", "DOC", 0L, "当出现 OutOfMemory 时重启服务");
        node("无关文档", "DOC", 0L, "今天天气不错");

        List<Map<String, Object>> hits = service.search("OutOfMemory");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0)).containsEntry("name", "告警手册");
        assertThat(String.valueOf(hits.get(0).get("snippet"))).contains("OutOfMemory");
    }

    @Test
    void folderNotReturnedInSearch() {
        node("空目录", "FOLDER", 0L, null);
        assertThat(service.search("空目录")).isEmpty();
    }
}
