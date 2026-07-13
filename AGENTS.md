# QuickTodo Project Context

## About
轻量级 Android Todo List App (Kotlin + Jetpack Compose)
- 文字 + 语音录入 Todo
- STT + LLM API 可配置
- Toggle 清理
- 桌面 Widget 显示待办 + 语音快捷按钮
- 未来：Obsidian 同步 + iOS 迁移

## Key Decisions
- 先 Android 原生（Kotlin），功能跑通后再考虑 iOS
- 用户自备 API Key（STT + LLM），设置页配置
- 长按录音 → 松手停止 → 自动处理
- 本地存储 + 手动导出备份
- Apple Reminders 简洁清爽风格
- 未来 Obsidian 同步：直接读写 .md 文件

## Project Structure
```
QuickTodo/
├── app/
│   ├── src/main/
│   │   ├── java/com/quicktodo/
│   │   │   ├── data/          # Room DB, DAO, Repository
│   │   │   ├── ui/            # Compose Screens
│   │   │   ├── voice/         # STT + LLM logic
│   │   │   ├── widget/        # AppWidget
│   │   │   └── QuickTodoApp.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
└── build.gradle.kts
```
