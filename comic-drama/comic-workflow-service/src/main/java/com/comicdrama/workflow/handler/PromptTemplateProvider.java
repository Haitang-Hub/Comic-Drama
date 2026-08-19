package com.comicdrama.workflow.handler;

/**
 * Prompt 模板提供者。
 * 根据模板编码获取模板内容（含变量占位符）。
 * Phase-2 实现：从 prompt_template 表加载（通过 Feign 或本地 Service），支持 Caffeine 热生效。
 */
public interface PromptTemplateProvider {

    /**
     * 按模板编码获取当前生效的模板内容。
     *
     * @param templateCode 模板编码（如 summary / storyboard / asset_design）
     * @return 模板内容字符串（含 {variable} 占位符）
     */
    String getTemplateContent(String templateCode);
}