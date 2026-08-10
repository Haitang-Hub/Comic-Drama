package com.comicdrama.workflow.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.comicdrama.workflow.entity.PromptTemplate;
import com.comicdrama.workflow.mapper.PromptTemplateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板提供者实现。
 * 从数据库 prompt_template 表加载当前启用的模板，支持 Caffeine 热生效。
 * 数据库无数据时回退到内置默认模板。
 */
@Slf4j
@Component
public class DefaultPromptTemplateProvider implements PromptTemplateProvider {

    private final PromptTemplateMapper promptTemplateMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public DefaultPromptTemplateProvider(PromptTemplateMapper promptTemplateMapper) {
        this.promptTemplateMapper = promptTemplateMapper;
    }

    @Override
    public String getTemplateContent(String templateCode) {
        // 1. 先从数据库加载
        try {
            String dbContent = loadFromDb(templateCode);
            if (dbContent != null && !dbContent.isEmpty()) {
                cache.put(templateCode, dbContent);
                return dbContent;
            }
        } catch (Exception e) {
            log.warn("从数据库加载模板失败，templateCode={}, error={}", templateCode, e.getMessage());
        }

        // 2. 从缓存返回
        String cached = cache.get(templateCode);
        if (cached != null) {
            return cached;
        }

        // 3. 回退到内置默认模板
        String fallback = getFallbackTemplate(templateCode);
        if (fallback != null) {
            cache.put(templateCode, fallback);
            return fallback;
        }

        throw new IllegalArgumentException("Prompt 模板不存在：" + templateCode);
    }

    /**
     * 强制刷新指定模板（从数据库重新加载）。
     */
    public void refreshTemplate(String templateCode) {
        cache.remove(templateCode);
        log.info("已刷新模板缓存: {}", templateCode);
    }

    /**
     * 清空所有缓存。
     */
    public void refreshAll() {
        cache.clear();
        log.info("已清空所有模板缓存");
    }

    private String loadFromDb(String templateCode) {
        QueryWrapper<PromptTemplate> wrapper = new QueryWrapper<>();
        wrapper.eq("template_code", templateCode)
               .eq("is_enabled", 1)
               .eq("deleted", 0)
               .last("LIMIT 1");
        PromptTemplate template = promptTemplateMapper.selectOne(wrapper);
        if (template != null && template.getContent() != null) {
            log.debug("从数据库加载模板成功: {}, stage={}", templateCode, template.getStage());
            return template.getContent();
        }
        return null;
    }

    private String getFallbackTemplate(String templateCode) {
        return switch (templateCode) {
            // 步骤1：摘要生成
            case "summary" -> """
                    故事需求：{{story_requirement}}
                    预估时长：{{duration}}秒
                    画风+风格（视觉定位）：{{art_style}}+{{visual_style}}
                    根据以上故事需求和预估时长，按照预估时长合理规划故事长短，编写详细的故事摘要，摘要中没有时间分配。
                    """;

            // 步骤2：分镜生成（12列格式，含场景/角色/道具及版本标识）
            case "storyboard" -> """
                    故事摘要：{{summary}}
                    故事总时长：{{duration}}秒
                    根据以上故事摘要和总时长合理分配，按照 "分镜序号（全局递增，从 1 开始）|本镜时长（秒）|场景分组 ID（从 1 开始）|组内序号（同场景组内序号，从 1 开始）|镜头角度（近景 / 远景 / 俯视等）|镜头描述（动作、运镜）|场景（格式：场景名称_版本标识）|出场角色（分号分隔，没有写”无“，格式：角色名称_版本标识）|出场道具（分号分隔，没有写”无“，格式：道具名称_版本标识）|分镜描述（场景/角色/道具要写完整名称）|台词内容（分号分隔，按时间顺序，没有写”无“）|画面描述（场景/角色/道具要写完整名称，不包含画风）" 格式要求生成分镜脚本（人物必须使用具体名称，单个分镜最多不能超过15秒，分镜以秒为单位不使用小数，每个镜头的台词和动作复杂度决定时长，台词中要包含人物和语气，不能长时间没有台词，同一场景/角色/道具名称不要变化，只有“推动剧情发展”或“被角色反复使用”的道具才写入，单个分镜出现的不作为道具，版本标识是资产的永久性/结构性改变，如场景的季节变、结构变，角色的换衣、换发、年龄变、昼夜切换、道具的功能变、形态变、颜色变，版本标识需要是一个简洁的词），使用|分割字段，不要包含表头。
                    """;

            // 步骤3：资产设计（6列格式，含基础资产名/衍生自/版本）
            case "asset_design" -> """
                    分镜脚本：{{storyboards}}
                    根据以上分镜脚本按照 "资产类型（人物/场景/道具/音色等）|资产名称（资产名称_版本标识）|基础资产名（无版本标识，用于归组）|衍生自（上一版本资产名，没有写”无“）|资产描述（资产的详细描述）|版本（由于变化产生的不同版本，从 1 开始）" 格式生成人物/场景/道具/音色等资产脚本（只在一个分镜中出现的人物/场景/道具/音色等不需要生成资产脚本，音色资产名称和基础资产名必须保持一致，版本号不为1时资产描述需要描述和上一版本的区别），使用|分割字段，不要包含表头。
                    """;

            // 步骤4：资产绘图（文生图，首次生成资产图片）
            case "asset_image" -> """
                    资产描述：{{asset_desc}}
                    画风+风格（视觉定位）：{{art_style}}+{{visual_style}}
                    根据以上内容生成资产图片
                    """;

            // 步骤5：衍生绘图（图生图，基于上一版本资产图片生成衍生版本）
            case "asset_derive" -> """
                    上一版本资产图片：{{base_image}}
                    新的资产描述：{{asset_desc}}
                    画风+风格（视觉定位）：{{art_style}}+{{visual_style}}
                    根据以上内容生成衍生资产图片
                    """;

            // 步骤6：分镜绘图
            case "storyboard_image" -> """
                    画面描述：{{visual_desc}}
                    并根据资产图片：{{asset_images}}
                    画风+风格（视觉定位）：{{art_style}}+{{visual_style}}
                    根据以上内容生成分镜图片
                    """;

            case "audio" -> """
                    音色资产描述：{{voice_asset}}
                    台词内容：{{dialogue}}
                    根据以上内容生成配音文件，用于绑定人物音色。
                    """;

            case "video" -> """
                    分镜脚本：{{storyboards}}
                    分镜图片：{{storyboard_image}}
                    资产图片：{{asset_images}}
                    配音文件：{{audio_files}}
                    画风+风格（视觉定位）：{{art_style}}+{{visual_style}}
                    视频比例：{{aspect_ratio}}
                    请根以上内容生成分镜视频。
                    """;

            default -> null;
        };
    }
}
