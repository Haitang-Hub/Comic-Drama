package com.comicdrama.workflow.handler;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 图片尺寸解析器。
 * 根据用户选择的画幅比例（aspectRatio）和分辨率（resolution）计算图片的宽高。
 * 当计算出的尺寸不满足模型最小像素要求时，自动提升分辨率等级。
 *
 * <p>尺寸映射规则（以短边为基准）：
 * <pre>
 *   480p  → 短边 480px
 *   720p  → 短边 720px
 *   1080p → 短边 1080px
 *   2K    → 短边 1440px
 *   4K    → 短边 2160px
 * </pre>
 *
 * <p>比例换算：
 * <pre>
 *   16:9 → width  = short × 16/9, height = short
 *   9:16 → width  = short,        height = short × 16/9
 *   1:1  → width  = short,        height = short
 * </pre>
 */
@Slf4j
public class ImageSizeResolver {

    /** seedream 模型最小像素要求：3,686,400（2560×1440） */
    private static final long MIN_PIXELS = 3686400L;

    /** 分辨率等级，按短边像素升序排列（用于自动提升） */
    private static final String[] RESOLUTION_ORDER = {"480p", "720p", "1080p", "2K", "4K"};

    /** 分辨率 → 短边像素 */
    private static final Map<String, Integer> SHORT_SIDE_MAP = Map.of(
            "480p", 480,
            "720p", 720,
            "1080p", 1080,
            "2K", 1440,
            "4K", 2160
    );

    /** 默认画幅比例 */
    private static final String DEFAULT_ASPECT_RATIO = "16:9";

    /** 默认分辨率 */
    private static final String DEFAULT_RESOLUTION = "2K";

    /**
     * 图片尺寸（宽 × 高）。
     */
    public static class ImageSize {

        private final int width;
        private final int height;

        public ImageSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        /** 总像素数 */
        public long pixels() {
            return (long) width * height;
        }

        /** 转换为 API 请求格式，如 "2560x1440" */
        public String toSizeString() {
            return width + "x" + height;
        }

        @Override
        public String toString() {
            return toSizeString() + " (" + pixels() + "px)";
        }
    }

    /**
     * 根据 aspectRatio + resolution 计算图片尺寸。
     * 如果不满足模型最小像素要求，自动提升分辨率等级。
     *
     * @param aspectRatio 画幅比例（16:9 / 9:16 / 1:1），为空时默认 16:9
     * @param resolution  分辨率（480p / 720p / 1080p / 2K / 4K），为空时默认 2K
     * @return 图片尺寸
     */
    public static ImageSize resolve(String aspectRatio, String resolution) {
        String ar = (aspectRatio == null || aspectRatio.isEmpty()) ? DEFAULT_ASPECT_RATIO : aspectRatio;
        String res = (resolution == null || resolution.isEmpty()) ? DEFAULT_RESOLUTION : resolution;

        int startIndex = resolutionIndex(res);

        // 从当前分辨率开始尝试，不满足最小像素要求则提升等级
        for (int i = startIndex; i < RESOLUTION_ORDER.length; i++) {
            ImageSize size = calculate(ar, RESOLUTION_ORDER[i]);
            if (size.pixels() >= MIN_PIXELS) {
                if (i > startIndex) {
                    log.info("[ImageSizeResolver] 分辨率 {} 在 {} 比例下像素不足({}), 已自动提升至 {}({})",
                            res, ar, formatPixels(calculate(ar, res).pixels()),
                            RESOLUTION_ORDER[i], size);
                }
                return size;
            }
        }

        // 理论上 4K 一定满足要求，走到这里说明比例异常，返回兜底尺寸
        ImageSize fallback = calculate(DEFAULT_ASPECT_RATIO, "2K");
        log.warn("[ImageSizeResolver] 无法为 aspectRatio={}, resolution={} 计算满足要求的尺寸，使用兜底值 {}",
                aspectRatio, resolution, fallback);
        return fallback;
    }

    /**
     * 根据比例和分辨率计算尺寸（不做像素检查）。
     */
    private static ImageSize calculate(String aspectRatio, String resolution) {
        int shortSide = SHORT_SIDE_MAP.getOrDefault(resolution, 1080);

        if ("9:16".equals(aspectRatio)) {
            int height = (int) Math.round(shortSide * 16.0 / 9);
            return new ImageSize(shortSide, height);
        } else if ("1:1".equals(aspectRatio)) {
            return new ImageSize(shortSide, shortSide);
        }
        // 默认 16:9
        int width = (int) Math.round(shortSide * 16.0 / 9);
        return new ImageSize(width, shortSide);
    }

    /**
     * 获取分辨率在等级数组中的索引。
     */
    private static int resolutionIndex(String resolution) {
        for (int i = 0; i < RESOLUTION_ORDER.length; i++) {
            if (RESOLUTION_ORDER[i].equals(resolution)) {
                return i;
            }
        }
        return 2; // 未知分辨率默认从 1080p 开始
    }

    private static String formatPixels(long pixels) {
        if (pixels >= 1_000_000) {
            return String.format("%.2fM", pixels / 1_000_000.0);
        }
        if (pixels >= 1_000) {
            return String.format("%.1fK", pixels / 1_000.0);
        }
        return String.valueOf(pixels);
    }
}
