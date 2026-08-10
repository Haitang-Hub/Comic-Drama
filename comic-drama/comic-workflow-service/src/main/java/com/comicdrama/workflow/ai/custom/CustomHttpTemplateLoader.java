package com.comicdrama.workflow.ai.custom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义 HTTP 模板加载器。
 * <p>
 * 启动时扫描 {@code classpath:custom-http-templates/*.yml}，解析为 {@link CustomHttpTemplate}，
 * 按 name 建立索引。模板文件命名与 name 字段无关，以 YAML 内 name 字段为准。
 */
@Slf4j
@Component
public class CustomHttpTemplateLoader {

    private static final String TEMPLATE_LOCATION = "classpath:custom-http-templates/*.yml";

    private final Map<String, CustomHttpTemplate> templates = new HashMap<>();

    @PostConstruct
    public void init() {
        Yaml yaml = new Yaml();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(TEMPLATE_LOCATION);
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    CustomHttpTemplate template = yaml.loadAs(is, CustomHttpTemplate.class);
                    if (template == null || template.getName() == null || template.getName().isEmpty()) {
                        log.warn("自定义 HTTP 模板缺少 name 字段，跳过：{}", resource.getFilename());
                        continue;
                    }
                    templates.put(template.getName(), template);
                    log.info("加载自定义 HTTP 模板：name={}, file={}, modelTypes={}, capabilities={}",
                            template.getName(), resource.getFilename(),
                            template.getModelTypes(), template.getCapabilities());
                }
            }
        } catch (Exception e) {
            log.warn("加载自定义 HTTP 模板失败（将跳过 CustomHttpInvoker 功能）：{}", e.getMessage());
        }
        log.info("自定义 HTTP 模板加载完成：共 {} 个模板 {}", templates.size(), templates.keySet());
    }

    /**
     * 按模板名称获取模板。
     */
    public CustomHttpTemplate get(String name) {
        return templates.get(name);
    }

    /**
     * 返回所有已加载模板（不可变）。
     */
    public Map<String, CustomHttpTemplate> all() {
        return Collections.unmodifiableMap(templates);
    }
}
