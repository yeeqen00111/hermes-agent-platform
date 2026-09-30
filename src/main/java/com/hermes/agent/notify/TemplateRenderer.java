package com.hermes.agent.notify;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {{var}} 模板渲染。缺失变量保留占位符，便于肉眼发现配置问题。
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    public String render(String template, Map<String, Object> vars) {
        if (template == null) {
            return "";
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object value = vars.get(m.group(1));
            m.appendReplacement(sb, value == null ? Matcher.quoteReplacement(m.group(0)) : Matcher.quoteReplacement(String.valueOf(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
