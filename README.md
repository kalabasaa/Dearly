<div align="center">

# Dearly

A private, PIN-protected notes app for writing, organizing, and remembering the things that matter to you.

</div>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-Android-purple?logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/XML-Layouts-orange?logo=xml" alt="XML"/>
  <img src="https://img.shields.io/badge/Gradle-Build%20System-02303A?logo=gradle" alt="Gradle"/>
  <img src="https://img.shields.io/badge/SQLite-Database-blue?logo=sqlite" alt="SQLite"/>
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" alt="Android"/>
</p>

<br>

## 📖 About

Dearly is an Android notes app built to keep your thoughts in one calm, personal place. It combines a rich-text note editor, folders and tags, stickers, and reminders, all stored on your device and locked behind a PIN.

The application uses Kotlin for functionality, XML for layouts, Material 3 for design, and SQLite for local data storage.

<br>

## ✨ Features

- Rich-text notes with bold, italic, underline, highlight, and five text sizes.
- Bullet, numbered, and checklist formatting, with undo support.
- Stickers placed inside notes: small, left-aligned, and resizable from a built-in sticker library.
- Folders and tags to organize notes, plus search across your notes.
- Reminders with one-time, daily, weekly, and monthly repeats, delivered as notifications.
- PIN lock with optional biometric unlock and a recovery question.
- Recently Deleted, which keeps removed notes and folders for 30 days before they are cleared.
- Light and dark themes.

## 🛠️ Technology Stack

<p align="left">
  <img src="https://img.shields.io/badge/Kotlin-Programming%20Language-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Android-Platform-3DDC84?logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/XML-UI%20Layouts-E34F26?logo=xml&logoColor=white" alt="XML"/>
  <img src="https://img.shields.io/badge/Material%203-Design-757575?logo=materialdesign&logoColor=white" alt="Material 3"/>
  <img src="https://img.shields.io/badge/SQLite-Database-003B57?logo=sqlite&logoColor=white" alt="SQLite"/>
  <img src="https://img.shields.io/badge/Android%20Studio-IDE-3DDC84?logo=androidstudio&logoColor=white" alt="Android Studio"/>
  <img src="https://img.shields.io/badge/Gradle-Build%20System-02303A?logo=gradle&logoColor=white" alt="Gradle"/>
  <img src="https://img.shields.io/badge/Git-Version%20Control-F05032?logo=git&logoColor=white" alt="Git"/>
  <img src="https://img.shields.io/badge/GitHub-Repository-181717?logo=github&logoColor=white" alt="GitHub"/>
</p>

<br>

## 🚀 Download

Dearly is currently in **Early Access**, in the pre-release testing and development phase. Try the app, write a few notes, set some reminders, and help us improve the experience along the way.

<p align="left">
  <a href="https://github.com/YOUR-USERNAME/Dearly/releases/latest">
    <img src="https://img.shields.io/badge/Download%20Early%20Access-Dearly-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Download Dearly Early Access"/>
  </a>
</p>

Your feedback helps shape the future of Dearly.

### 📦 Release Information

- **Version:** 1.0
- **Release Type:** Initial Release
- **Platform:** Android 8.0 (API 26) and above
- **Language:** Kotlin
- **UI:** XML Layouts with Material 3
- **Database:** SQLite
- **Build System:** Gradle

<br>

## 🔒 Security

Dearly is private by design. Everything is stored only on your device, and nothing is sent online.

- Access is controlled by a four-digit PIN, which is stored as a hash rather than as plain text.
- Repeated wrong PIN entries trigger a temporary lock.
- Biometric unlock (fingerprint or face) is optional, with the PIN as backup.
- A recovery question is available if the PIN is forgotten.
- Recently Deleted requires your PIN to open.
- Reminder notifications hide note titles by default so they stay private on the lock screen.

The PIN controls access to the app, but the database file itself is not encrypted.

## 📸 Screenshots

Screenshots will be added as the application interface develops.

<br>

## 🗄️ Database Schema

Dearly uses the following SQLite tables:

| Table | Description |
|---|---|
| `settings` | App preferences such as theme and notification options |
| `folders` | Folders that group notes |
| `notes` | Note content, timestamps, and deletion state |
| `tags` | Tag names |
| `note_tags` | Links between notes and tags |
| `folder_tags` | Links between folders and tags |
| `stickers` | The sticker library |
| `note_stickers` | Stickers used in each note |
| `reminders` | Reminder time, repeat type, and the note it belongs to |

Deleted items are soft-deleted with a timestamp, which is what powers Recently Deleted and lets a folder be restored together with the notes it took with it.


<br>

## 👥 Development Team

### 👨‍💻 Renier Tambogon

**Lead Developer · Designer**

Responsible for developing the application, managing the database, and designing the Dearly interface.

**GitHub:** [kalabasaa](https://github.com/kalabasaa)

<br>

## 📄 License

Copyright (c) 2026 Your Name

This project is licensed under the MIT License. You are free to use, copy, modify, merge, publish, distribute, sublicense, and sell copies of the software, provided that the original copyright notice and permission notice are included.

See the [LICENSE](LICENSE) file for details.
