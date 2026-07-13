# QuickTodo - Android App 开发计划

## 项目概况
轻量级 Todo List Android App（Kotlin + Jetpack Compose）
- 文字录入 + 语音录入（STT + AI 精炼）+ Toggle 清理 + 桌面 Widget
- 后续：Obsidian 同步 + iOS 迁移

## Phases

### Phase 1: 项目骨架 + 核心 Todo 功能 🔴 IN PROGRESS
- [ ] 创建 Android Kotlin + Compose 项目
- [ ] 配置 Gradle（Kotlin DSL, AGP, Compose）
- [ ] Room 数据库（Todo 表: id, title, isDone, createdAt）
- [ ] MVVM 架构（Entity → DAO → Repository → ViewModel）
- [ ] 主界面：Todo 列表 + 新增文字输入 + Toggle 勾选 + 清除已完成
- [ ] 类 Apple Reminders 简洁清爽 UI
- [ ] **可测试：能在模拟器/真机上跑通，显示 Todo 列表**

### Phase 2: 语音录入 + AI 处理 ⏳ PENDING
- [ ] 长按录音（MediaRecorder）
- [ ] 设置页面：STT API + LLM API 配置
- [ ] 调用 STT API（音频 → 文字）
- [ ] 调用 LLM API（Raw text → 精炼 Todo）
- [ ] 精炼结果预览 + 编辑 → 确认添加
- [ ] 设置持久化（DataStore）
- [ ] **可测试：语音录入完整流程可跑通**

### Phase 3: 桌面 Widget ⏳ PENDING
- [ ] Android AppWidget 实现
- [ ] 显示未完成事项
- [ ] 语音快捷按钮
- [ ] 数据刷新机制
- [ ] Widget 样式适配简洁风
- [ ] **可测试：Widget 能部署到桌面并显示/交互**

### Phase 4: 数据导出 + 打磨 ⏳ PENDING
- [ ] 导出 JSON/CSV
- [ ] 导入恢复
- [ ] 边缘情况处理
- [ ] **可测试：导出文件可读，导入恢复成功**

### Phase 5 (后续): Obsidian 同步
- [ ] SAF 选择 Obsidian Vault 目录
- [ ] 完成 Todo → 同步 .md 文件
- [ ] 格式模板自定义

### Phase 6 (未来): iOS 迁移
- [ ] KMP 共享数据层
- [ ] SwiftUI UI
- [ ] WidgetKit 独立实现

## 技术栈
- 语言: Kotlin
- UI: Jetpack Compose
- 数据库: Room
- 网络: OkHttp + Retrofit
- 录音: MediaRecorder
- 设置: DataStore Preferences
- 架构: MVVM
- Widget: AppWidgetProvider + RemoteViews
- 最低支持: API 26 (Android 8.0)
- 目标: API 34 (Android 14)
