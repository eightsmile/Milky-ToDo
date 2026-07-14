# Milky ToDo

[![Download APK](https://img.shields.io/github/v/release/eightsmile/Milky-ToDo?label=Download%20APK&color=FF69B4&logo=android)](https://github.com/eightsmile/Milky-ToDo/releases/latest)
[![Build](https://github.com/eightsmile/Milky-ToDo/actions/workflows/build.yml/badge.svg)](https://github.com/eightsmile/Milky-ToDo/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

A lightweight, cute Android to-do app with voice input, AI-powered task refinement, and Obsidian sync.

## Features

- **Text & Voice Input** — Type or speak your tasks
- **AI Refinement** — Doubao ASR for speech-to-text + DeepSeek LLM for smart task extraction
- **Smart Date/Repeat** — Automatically extracts dates and repeat intervals from natural language
- **Inline Editing** — Tap to edit title, date, and repeat on the fly
- **Archive** — Completed tasks stored with completion timestamps, restore or clear
- **Obsidian Sync** — Export completed tasks as markdown files to your vault
- **Home Screen Widget** — Shows pending tasks with date and repeat indicators
- **Swipe to Delete** — Quick deletion with left swipe
- **Material Design 3** — Clean Apple Reminders-inspired UI with dark mode support

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM (Room + Repository + ViewModel)
- **Database:** Room (SQLite)
- **STT:** Doubao ASR (Volcengine)
- **LLM:** DeepSeek Chat API
- **Audio:** MediaRecorder → MPEG_4/AAC

## Getting Started

1. Clone the repo
2. Open in Android Studio
3. Build and run on Android 8.0+ (API 26+)
4. Configure API keys in Settings:
   - **STT:** Doubao ASR or OpenAI Whisper
   - **LLM:** DeepSeek Chat or OpenAI

## License

MIT
