# QuickTodo - 开发进展日志

## Session 2: 2026-07-13 — Phase 2 测试通过 ✅

### Phase 2 完成内容
- ApiService 封装 STT（Doubao ASR / OpenAI Whisper）+ LLM（DeepSeek）调用
- VoiceInputScreen：长按录音 → 松手 → ASR 转文字 → LLM 精炼 → 预览 → 确认添加
- 短触检测（<1s 提示重新录入）
- 录音按钮放大至 180dp，录音界面布局优化
- 设置页预设：Doubao ASR / DeepSeek / OpenAI Whisper

### 遗留问题
- Vivo OriginOS Widget 仍无法显示（已知 Issue）
- `MediaRecorder()` 构造器已废弃（API 31+）
- 如需增加更多 STT 提供商，扩展 ApiService 的 when 分支即可

### 关键技术决策
- STT 使用火山引擎豆包语音 ASR（openspeech.bytedance.com），非 ARK API
- LLM 使用 DeepSeek Chat Completions API
- 音频格式：MPEG_4/AAC (.m4a)，44.1kHz 采样率
- 录音 → base64 → HTTP POST ASR 端点 → 文字 → LLM精炼 → Todo
