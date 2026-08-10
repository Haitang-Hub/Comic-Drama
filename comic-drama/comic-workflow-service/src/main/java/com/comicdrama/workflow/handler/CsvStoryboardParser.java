package com.comicdrama.workflow.handler;

import com.comicdrama.workflow.entity.Storyboard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV格式分镜脚本解析器。
 *
 * <p>将 AI 模型输出的管道符分隔 CSV 文本解析为 {@link Storyboard} 实体列表。
 * 支持 RFC4180 规范的引号字段（双引号包裹、"" 转义），自动剥离 Markdown 围栏和非 CSV 文本。</p>
 *
 * <h3>预期 CSV 格式（12列，管道符分隔）</h3>
 * <pre>
 * 分镜序号|本镜时长|场景分组ID|组内序号|镜头角度|镜头描述|场景|出场角色|出场道具|分镜描述|台词内容|画面描述
 * 1|5|1|1|近景|女孩站在走廊|教室|小红|无|女孩表情紧张地看着前方|怎么办，我该怎么办|走廊灯光昏暗，女孩独自站立
 * </pre>
 */
@Slf4j
public class CsvStoryboardParser {

    private static final String DELIMITER = "\\|";
    private static final int MIN_COLUMNS = 5;
    private static final int EXPECTED_COLUMNS = 12;

    private static final String[] HEADER_KEYWORDS = {
            "分镜序号", "本镜时长", "场景分组", "组内序号", "镜头角度", "镜头描述",
            "场景", "出场角色", "出场道具", "分镜描述", "台词", "画面描述",
            "seq", "duration", "group", "local", "angle", "desc",
            "scene", "character", "props", "storyboard", "dialogue", "visual"
    };

    /**
     * 解析 AI 返回的 CSV 文本为分镜实体列表。
     *
     * @param csvText  AI 返回的原始文本（可能包含 Markdown 围栏、思考过程等）
     * @param taskId   任务ID
     * @param baseSeq  全局序号起始值（从0开始，首个分镜序号=baseSeq+1）
     * @return 解析成功的分镜列表
     */
    public static List<Storyboard> parse(String csvText,
                                          Long taskId,
                                          int baseSeq) {
        String cleaned = stripNonCsvContent(csvText);
        List<String[]> rows = parseRows(cleaned);

        if (rows.isEmpty()) {
            log.warn("[CsvStoryboardParser] CSV解析结果为空，原始文本长度={}",
                    csvText != null ? csvText.length() : 0);
            return new ArrayList<>();
        }

        List<Storyboard> result = new ArrayList<>();
        int globalSeq = baseSeq;
        int localSeq = 0;
        int currentGroupId = 1;

        for (String[] row : rows) {
            Storyboard sb = buildStoryboard(row, taskId,
                    ++globalSeq, ++localSeq, currentGroupId);
            if (sb != null) {
                // 更新场景分组ID
                if (row.length >= 3 && StringUtils.hasText(row[2])) {
                    try {
                        currentGroupId = Integer.parseInt(row[2].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                result.add(sb);
            }
        }

        log.info("[CsvStoryboardParser] 解析完成，共{}个分镜", result.size());
        return result;
    }

    /**
     * 剥离非 CSV 内容：Markdown 围栏、思考过程文本、空行等。
     */
    static String stripNonCsvContent(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String text = raw.trim();

        // Strip markdown code fences: ```csv ... ``` or ``` ... ```
        text = text.replaceAll("(?s)```[a-zA-Z0-9]*\\s*", "");
        text = text.replaceAll("```", "");

        // Strip <thinking> blocks (common in reasoning models)
        text = text.replaceAll("(?s)<thinking>.*?</thinking>", "");

        // Strip lines that don't contain the delimiter (likely prose/analysis)
        StringBuilder cleaned = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Keep line only if it looks like a CSV row (contains | or starts with a number pattern)
            if (trimmed.contains("|") || trimmed.matches("^\\d+.*")) {
                cleaned.append(trimmed).append("\n");
            }
        }

        return cleaned.toString().trim();
    }

    /**
     * 将清洗后的文本按行解析为字符串数组列表，跳过表头行。
     */
    static List<String[]> parseRows(String cleaned) {
        List<String[]> rows = new ArrayList<>();
        String[] lines = cleaned.split("\\r?\\n");

        boolean headerSkipped = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            ParsedRow parsed = parseCsvLine(trimmed);

            // Skip header row (first row with text content like "分镜序号|本镜时长|...")
            if (!headerSkipped && isHeaderRow(parsed.fields)) {
                headerSkipped = true;
                continue;
            }

            // Validate minimum columns (check actual count before padding)
            if (parsed.actualCount < MIN_COLUMNS) {
                log.debug("[CsvStoryboardParser] 跳过列数不足的行: {} (需要>={}, 实际={})",
                        trimmed, MIN_COLUMNS, parsed.actualCount);
                continue;
            }

            rows.add(parsed.fields);
        }

        return rows;
    }

    /**
     * 解析单行 CSV，支持双引号包裹字段和 "" 转义。
     * 遵循 RFC 4180 规范。
     *
     * @return ParsedRow 包含填充后的字段数组和实际列数
     */
    static ParsedRow parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    // Check for escaped quote: "" → single "
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == '|') {
                    fields.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }

        fields.add(current.toString().trim());

        int actualCount = fields.size();

        // Pad to expected columns if fewer
        while (fields.size() < EXPECTED_COLUMNS) {
            fields.add("");
        }

        return new ParsedRow(fields.toArray(new String[0]), actualCount);
    }

    /** 解析结果：包含填充后的字段数组和实际列数 */
    static record ParsedRow(String[] fields, int actualCount) {
    }

    /**
     * 判断是否为表头行（包含列名关键字）。
     * 数字开头的行不可能是表头，直接返回false。
     */
    static boolean isHeaderRow(String[] fields) {
        if (fields.length == 0) {
            return false;
        }

        // Data rows start with a number (shot number), exclude them
        String firstField = fields[0].trim();
        if (!firstField.isEmpty() && Character.isDigit(firstField.charAt(0))) {
            return false;
        }

        int keywordCount = 0;
        for (String field : fields) {
            String lower = field.toLowerCase().trim();
            for (String keyword : HEADER_KEYWORDS) {
                if (lower.contains(keyword.toLowerCase())) {
                    keywordCount++;
                    break;
                }
            }
        }
        // If more than half the fields match header keywords, treat as header
        return keywordCount >= Math.max(2, fields.length / 2);
    }

    /**
     * 从解析的字段数组构建 Storyboard 实体。
     * 列顺序（12列）：分镜序号|本镜时长|场景分组ID|组内序号|镜头角度|镜头描述|场景|出场角色|出场道具|分镜描述|台词内容|画面描述
     */
    static Storyboard buildStoryboard(String[] fields,
                                      Long taskId,
                                      int seq,
                                      int localSeq,
                                      int groupId) {
        Storyboard sb = new Storyboard();
        sb.setTaskId(taskId);
        sb.setSeq(seq);
        sb.setLocalSeq(localSeq);

        // Column 0: 分镜序号 (shot number) - informational, already set via seq
        // Column 1: 本镜时长 (duration)
        sb.setDuration(parseInt(getField(fields, 1), 3));

        // Column 2: 场景分组ID (scene group ID)
        sb.setGroupId(parseInt(getField(fields, 2), groupId));

        // Column 3: 组内序号 (local sequence within group)
        sb.setLocalSeq(parseInt(getField(fields, 3), localSeq));

        // Column 4: 镜头角度 (camera angle)
        sb.setCameraAngle(getField(fields, 4));

        // Column 5: 镜头描述 (shot description - action, camera movement)
        sb.setShotDesc(getField(fields, 5));

        // Column 6: 场景 (scene, 格式：场景名称_版本标识)
        sb.setScene(getField(fields, 6));

        // Column 7: 出场角色 (characters appearing, 格式：角色名称_版本标识)
        sb.setCharacter(getField(fields, 7));

        // Column 8: 出场道具 (props, 格式：道具名称_版本标识)
        sb.setProps(getField(fields, 8));

        // Column 9: 分镜描述 (storyboard description)
        sb.setStoryboardDesc(getField(fields, 9));

        // Column 10: 台词内容 (dialogue)
        sb.setDialogue(getField(fields, 10));

        // Column 11: 画面描述 (visual description)
        sb.setVisualDesc(getField(fields, 11));

        sb.setIsEdited(0);

        return sb;
    }

    private static String getField(String[] fields, int index) {
        if (index < fields.length) {
            return fields[index];
        }
        return "";
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
