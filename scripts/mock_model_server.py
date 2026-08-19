#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
漫剧生成工作流 —— 测试专用假文本模型服务（单文件、零依赖）。

实现 OpenAI 兼容的 `POST /v1/chat/completions` 端点，供现有 `DeepSeekInvoker`
在不修改任何代码的情况下直接调用：只需在管理后台新增一条 AI 模型配置指向本服务，
并把步骤 1-3（SUMMARY / STORYBOARD / ASSET_DESIGN）绑定到该配置即可。

工作原理：
  1. 接收 `DeepSeekInvoker` 发来的 POST 请求（Header `Authorization: Bearer <key>`）。
  2. 校验 key，不匹配返回 401。
  3. 从 messages 中取用户消息内容，按关键词识别属于哪一步：
       - 含「资产类型」 -> ASSET_DESIGN（步骤3，必须最先判断，因其 prompt 内嵌分镜 CSV 表头）
       - 否则含「故事摘要」 -> STORYBOARD（步骤2）
       - 否则含「故事需求」 -> SUMMARY（步骤1）
       - 否则 -> 兜底文本
  4. 返回预设的最小化测试内容（2 条分镜 + 人物/场景/道具/音色 各 1 条资产），
     格式严格匹配 CsvStoryboardParser / AssetDesignStepHandler / SummaryStepHandler 的解析逻辑。
  5. 响应体为 OpenAI 兼容结构，choices[0].message.content 为正文。

下游产物（假模型产出 2 分镜 + 5 资产，其中 1 条人物资产含衍生版本 v2）：
  - 步骤4 资产绘图：3 张（人物_原版 / 场景_原版 / 道具_原版）
  - 步骤5 衍生绘图：1 张（人物_衍生版，基于_原版 图生图，验证 base_image 参数）
  - 步骤6 分镜绘图：2 张（对应2条分镜，验证 asset_images 参数）
  - 步骤7 配音合成：1 个音频
  - 步骤8 视频生成：2 个视频（对应2条分镜）
  - 步骤9 视频合并：1 个最终视频

输出格式严格对齐（Phase-5 新格式，带版本标识与衍生关系）：
  - 步骤1 SUMMARY：直接输出，不包含任何分隔符
  - 步骤2 STORYBOARD：12 列管道 CSV（含「场景」「出场道具」两列，带 _原版 / _衍生版 版本标识）
  - 步骤3 ASSET_DESIGN：6 列管道 CSV（资产类型|资产名称|基础资产名|衍生自|资产描述|版本）

运行：
  python scripts/mock_model_server.py
  python scripts/mock_model_server.py --port 9876 --key mock-test-key-12345
  # 或用环境变量 MOCK_PORT / MOCK_KEY
"""

import argparse
import json
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# ==================== 预设返回内容（新故事：男子意外获得女装系统） ====================

# Phase-5 新格式：三段式输出，SummaryStepHandler.parseSummaryContent 按分隔符解析。
# 正/负面提示词（英文）写入 story_summary，供步骤 4/5/6 图像模型复用。
SUMMARY_CONTENT = (
    "大学生林宇某天早上醒来，手机里突然多了一个名为‘女装系统’的APP，强制要求他换上女装，否则将受到惩罚。"
    "系统化身成一位可爱的女性虚拟形象，不断发出换装指令。林宇试图反抗，但系统以各种方式威胁，最终他无奈穿上了一套女装，"
    "看着镜中陌生的自己，内心五味杂陈。"
)

# ==================== 2 条分镜（新故事） ====================
# 12 列管道符 CSV（无表头），按 CsvStoryboardParser 新格式：
# 序号|时长|group_id|local_seq|镜头角度|镜头描述|场景|出场角色|出场道具|分镜描述|台词|画面描述
# 名称统一使用 名称_vN 后缀，保证资产名称与分镜引用严格匹配（步骤6 ImageStepHandler.resolveAssetImages）。
# 时长=5 对应测试约定（duration=5s）。
# 第1条：系统弹出；第2条：镜前震惊。
STORYBOARD_CONTENT = (
    "1|5|1|1|中景|林宇躺在床上，系统面板突然弹出|男主卧室_原版|林宇_原版,系统_原版|系统面板_原版|"
    "清晨，阳光透过窗帘，林宇刚醒，手机震动，一个全息面板凭空出现，上面闪烁‘强制换装’字样，系统虚拟形象浮现|"
    "系统（女声）：叮！检测到宿主未完成每日任务，即刻执行强制换装！|"
    "林宇坐在床上，一脸惊恐，手机发出蓝光，半透明的系统女孩悬浮在面板旁，周围有数据流\n"
    "2|5|1|2|特写|林宇穿着女装站在镜子前，震惊不已|男主卧室_原版|林宇_女装版|系统面板_原版|"
    "林宇身穿粉色连衣裙，头戴蝴蝶结，站在落地镜前，双手捂住脸，镜中映出他害羞又惊讶的表情|"
    "林宇（内心）：这...这是我吗？！|"
    "卧室一角，林宇穿着裙子，镜子映出他的全身，系统面板在一旁显示‘任务完成’字样"
)

# ==================== 资产设计（新故事配套） ====================
# 6 列管道符 CSV（无表头），按 AssetDesignStepHandler 新格式：
# 资产类型|资产名称（含_vN）|基础资产名（无后缀，用于归组）|衍生自（上一版全名，v1 写"无"）|资产描述|版本号
# 出场角色名、场景名、道具名 与分镜列 7-9 完全一致（含 _vN），保证步骤 6 匹配成功。
# 人物 v2 版本的 derived_from 指向人物 v1 全名，供步骤 5 AssetDeriveStepHandler 查找 base_image。
# 音色资产 基础资产名 与人物资产一致（规范要求）。
ASSET_DESIGN_CONTENT = (
    "人物|林宇_原版|林宇|无|普通男大学生，短发，穿白色T恤，睡眼惺忪，身材匀称|1\n"
    "人物|系统_原版|系统|无|可爱的女性虚拟形象，身高约30厘米，半透明，蓝色长发，穿未来感服饰，眼睛是电子屏|1\n"
    "人物|林宇_女装版|林宇|林宇_原版|林宇穿上粉色连衣裙，头戴蝴蝶结，脸上有红晕，身材不变，但姿态扭捏|2\n"
    "场景|男主卧室_原版|男主卧室|无|普通男生卧室，有床、书桌、落地镜，窗帘半开，阳光洒入，墙上贴有海报|1\n"
    "道具|系统面板_原版|系统面板|无|半透明的全息屏幕，显示‘女装系统’界面，有任务列表和进度条，泛着蓝光|1\n"
    "音色|系统|系统|无|甜美可爱的女声，带电子音效，语速较快，略带调皮|1"
)

FALLBACK_CONTENT = "[mock] 未识别到对应步骤，返回兜底文本。" + SUMMARY_CONTENT


def detect_step(prompt_text: str) -> str:
    """根据 prompt 内容识别步骤，按第一行开头的关键词匹配。"""
    first_line = prompt_text.strip().split('\n')[0] if prompt_text.strip() else ""
    if first_line.startswith("分镜脚本"):
        return "ASSET_DESIGN"
    if first_line.startswith("故事摘要"):
        return "STORYBOARD"
    if first_line.startswith("故事需求"):
        return "SUMMARY"
    # 兜底：全文搜索（适用于非标准格式）
    if "分镜脚本" in prompt_text:
        return "ASSET_DESIGN"
    if "故事摘要" in prompt_text:
        return "STORYBOARD"
    if "故事需求" in prompt_text:
        return "SUMMARY"
    return "UNKNOWN"


def content_for_step(step: str) -> str:
    return {
        "SUMMARY": SUMMARY_CONTENT,
        "STORYBOARD": STORYBOARD_CONTENT,
        "ASSET_DESIGN": ASSET_DESIGN_CONTENT,
    }.get(step, FALLBACK_CONTENT)


def extract_prompt(body: dict) -> str:
    """从请求体 messages 中提取用户 prompt（取最后一条 user 消息，兜底拼接全部内容）。"""
    messages = body.get("messages") or []
    user_content = ""
    for msg in messages:
        if isinstance(msg, dict) and msg.get("role") == "user":
            c = msg.get("content")
            if isinstance(c, str):
                user_content = c
            elif isinstance(c, list):
                # OpenAI 多模态格式：content 为 [{type, text}]
                user_content = "".join(
                    p.get("text", "") for p in c if isinstance(p, dict)
                )
    if user_content:
        return user_content
    # 兜底：拼接所有消息内容
    parts = []
    for msg in messages:
        if isinstance(msg, dict):
            c = msg.get("content")
            if isinstance(c, str):
                parts.append(c)
    return "\n".join(parts)


def build_openai_response(content: str, prompt_text: str, model: str,
                          enable_thinking: bool = True) -> str:
    """构造 OpenAI 兼容响应体。token 按内容长度估算。

    模拟魔搭推理模型行为：
      - enable_thinking=True（默认）：message 含 reasoning_content（推理过程）+ content（最终回答）
      - enable_thinking=False：message 仅含 content（关闭思维链，直接返回最终回答）
    与魔搭 Qwen3 / DeepSeek-V4-Pro 的响应结构一致，供 DeepSeekInvoker.parseContent 解析。
    """
    prompt_tokens = max(1, len(prompt_text) // 3)
    completion_tokens = max(1, len(content) // 3)

    message = {"role": "assistant", "content": content}
    if enable_thinking:
        # 模拟推理过程（魔搭推理模型会先输出 reasoning_content 再输出 content）
        reasoning = (
            f"[mock reasoning] 分析请求内容，识别为{model}调用。"
            f"提取 prompt 关键信息，按步骤生成对应结构化内容。"
        )
        message["reasoning_content"] = reasoning
        completion_tokens += max(1, len(reasoning) // 3)

    resp = {
        "id": f"chatcmpl-mock-{int(time.time() * 1000)}",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": model,
        "choices": [
            {
                "index": 0,
                "message": message,
                "finish_reason": "stop",
            }
        ],
        "usage": {
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "total_tokens": prompt_tokens + completion_tokens,
        },
    }
    return json.dumps(resp, ensure_ascii=False)


class MockHandler(BaseHTTPRequestHandler):
    server_version = "MockTextModel/1.0"

    # ---- 基础工具 ----
    def _send_json(self, status: int, payload: dict):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_openai_error(self, status: int, message: str):
        self._send_json(status, {"error": {"message": message, "type": "invalid_request_error"}})

    def _check_auth(self) -> bool:
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            return False
        return auth[len("Bearer "):].strip() == self.server.mock_key

    # ---- 路由 ----
    def do_GET(self):
        # 健康检查
        self._send_json(
            200,
            {
                "status": "ok",
                "service": "mock-text-model",
                "endpoints": ["POST /v1/chat/completions"],
            },
        )

    def do_POST(self):
        # 匹配以 /chat/completions 结尾的路径（兼容 /v1/chat/completions）
        if not self.path.rstrip("/").endswith("/chat/completions"):
            self._send_openai_error(404, f"路径不存在: {self.path}")
            return

        if not self._check_auth():
            self._send_openai_error(401, "API 密钥无效（mock-test 模式）：请在管理后台检查 API Key 配置。")
            return

        # 读取请求体
        try:
            length = int(self.headers.get("Content-Length", 0))
        except ValueError:
            length = 0
        raw = self.rfile.read(length) if length > 0 else b""

        # 调试日志：打印收到的原始请求信息
        ct = self.headers.get("Content-Type", "")
        print(
            f"[DEBUG] Content-Length={length}, raw_len={len(raw)}, Content-Type={ct}",
            flush=True,
        )
        if raw:
            print(f"[DEBUG] raw前300: {raw[:300]}", flush=True)

        try:
            body = json.loads(raw.decode("utf-8")) if raw else {}
        except Exception as e:
            self._send_openai_error(400, f"请求体 JSON 解析失败: {e}")
            return
        print(f"[DEBUG] body keys={list(body.keys()) if isinstance(body, dict) else type(body)}", flush=True)

        prompt_text = extract_prompt(body)
        model = body.get("model", "mock-test-text")
        # 兼容魔搭 Qwen3 的 enable_thinking 参数（DeepSeekInvoker 会传 enable_thinking=false）
        enable_thinking = body.get("enable_thinking", True)
        # 兼容 max_tokens 参数（mock 忽略上限，仅记录用于调试）
        max_tokens = body.get("max_tokens")
        step = detect_step(prompt_text)
        content = content_for_step(step)

        # 简洁日志
        input_preview = prompt_text[:10]
        output_preview = content[:10]
        print(
            f"[{time.strftime('%H:%M:%S')}] step={step} model={model} "
            f"thinking={enable_thinking} max_tokens={max_tokens} "
            f"| input({len(prompt_text)}): '{input_preview}' "
            f"| output({len(content)}): '{output_preview}'",
            flush=True,
        )

        resp_json = build_openai_response(content, prompt_text, model, enable_thinking)
        self._send_json(200, json.loads(resp_json))

    def log_message(self, fmt, *args):
        # 静默默认访问日志，使用上面自定义打印
        return


def main():
    parser = argparse.ArgumentParser(description="漫剧工作流测试专用假文本模型服务")
    parser.add_argument("--port", type=int, default=int(os.environ.get("MOCK_PORT", "9876")),
                        help="监听端口（默认 9876，可用环境变量 MOCK_PORT）")
    parser.add_argument("--key", type=str, default=os.environ.get("MOCK_KEY", "mock-test-key-12345"),
                        help="API 测试密钥（默认 mock-test-key-12345，可用环境变量 MOCK_KEY）")
    parser.add_argument("--host", type=str, default="127.0.0.1",
                        help="监听地址（默认 127.0.0.1）")
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), MockHandler)
    server.mock_key = args.key  # type: ignore[attr-defined]

    print("=" * 64, flush=True)
    print("漫剧工作流 —— 测试专用假文本模型服务", flush=True)
    print(f"监听: http://{args.host}:{args.port}", flush=True)
    print(f"端点: POST http://{args.host}:{args.port}/v1/chat/completions", flush=True)
    print(f"密钥: {args.key}", flush=True)
    print("管理后台接入: 模型服务商=modelscope, API地址="
          f"http://{args.host}:{args.port}/v1, 模型类型=文本", flush=True)
    print("绑定步骤: SUMMARY / STORYBOARD / ASSET_DESIGN", flush=True)
    print("按 Ctrl+C 停止。", flush=True)
    print("=" * 64, flush=True)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止。", flush=True)
        server.shutdown()
        sys.exit(0)


if __name__ == "__main__":
    main()