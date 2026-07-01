好的，这是根据中文习惯重新编写的版本，使其更符合国内玩家的阅读和理解方式：

---

# ScriptBlockPlus —— 继承经典，功能更强大的脚本方块插件

[![构建状态](https://img.shields.io/github/actions/workflow/status/rmoyulong/ScriptBlockPlus/maven-publish.yml)](https://github.com/rmoyulong/ScriptBlockPlus/actions/workflows/maven-publish.yml)
[![总下载量](https://img.shields.io/github/downloads/rmoyulong/ScriptBlockPlus/total)](https://github.com/rmoyulong/ScriptBlockPlus/releases)
[![最新版下载量](https://img.shields.io/github/downloads/rmoyulong/ScriptBlockPlus/latest/total)](https://github.com/rmoyulong/ScriptBlockPlus/releases/latest)

---

## 插件简介

**ScriptBlockPlus** 是在经典插件 [ScriptBlock](https://dev.bukkit.org/projects/scriptblock) 基础上开发的功能增强版本，不仅保留了原有功能，还加入了大量新特性和优化。

> 目前我们正在开发一个支持多平台运行的后续版本，敬请期待。

---

## 主要特色

- **完美适配 Folia 服务端**  
  全面兼容 Folia 核心，所有原有及新增功能均可在 Folia 环境下稳定运行。

- **可视化书本生成器**  
  内置图形化界面，方便你编辑文本和调整排版，书本数量可自由设定。

- **击败统计显示**  
  当奖励箱被玩家击败时，可显示该玩家造成的总攻击次数。

- **区域怪物生成控制**  
  支持对特定区域进行精细管理，包括：
  - 自定义生成点位
  - 怪物数量与生成范围
  - 支持原生生物及 MMOmobs 插件生物
  - 区域内容纳上限控制
  - 可选择性局部生成

---

## 安装方法

1. 从项目发布页下载最新版 `ScriptBlockPlus.jar`：[前往下载](https://github.com/yuttyann/FileArchive/tree/main/ScriptBlockPlus)
2. 将文件放入服务端的 `plugins` 文件夹
3. 重启或重载服务器即可

---

## Java 8 兼容版本

- 针对 **Java 8** 的旧版本已单独维护，请移步专用仓库：[ScriptBlockPlus-Java8](https://github.com/yuttyann/ScriptBlockPlus-Java8)
- 适用场景：服务器环境无法升级至 Java 11 时使用
- ⚠️ 注意：该版本已停止更新（最后版本 v2.1.2），且不提供技术支持

---

## 可选前置插件

| 插件 | 用途 |
|------|------|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | 提供权限与经济系统支持 |
| [DiscordSRV](https://www.spigotmc.org/resources/discordsrv.18494/) | 启用 Discord 消息互通功能 |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | 支持更多占位符变量 |
| [ScriptEntityPlus](https://github.com/yuttyann/ScriptEntityPlus) | 允许为实体绑定脚本行为 |

---

## 版本兼容表

| 插件版本 | 适用服务端版本 | 最低 Java 要求 |
|----------|---------------|----------------|
| **`2.5.1`** | **`1.21.8 - 26.1.2`** | **Java 17** |
| **`2.4.0`** | `1.9 - 1.21.8` | **Java 17** |
| `2.3.4` | `1.9 - 1.21.8` | Java 11 |
| `2.3.3` | `1.9 - 1.21.5` | Java 11 |
| `2.3.0 ~ 2.3.2` | `1.9 - 1.21` | Java 11 |
| `2.2.7 ~ 2.2.8` | `1.9 - 1.20.2` | Java 11 |
| `2.2.6` | `1.9 - 1.20.1` | Java 11 |
| `2.2.5` | `1.9 - 1.19.3` | Java 11 |
| `2.2.3 ~ 2.2.4` | `1.9 - 1.19.2` | Java 11 |
| `2.2.0 ~ 2.2.2` | `1.9 - 1.18` | Java 11 |
| `2.1.5 ~ 2.1.8` | `1.9 - 1.17.1` | Java 11 |
| `2.1.2 ~ 2.1.4` | `1.9 - 1.17` | Java 11 |
| `2.0.4 ~ 2.1.1` | `1.9 - 1.16.5` | Java 11 |
| [`2.x.x-JV8`](https://github.com/yuttyann/ScriptBlockPlus-Java8) | `1.9 - 1.16.5` | **Java 8** |
| `1.8.5 ~ 2.0.3` | `1.9 - 1.16.5` | Java 8 |
| `1.6.0 ~ 1.8.4` | `1.8 - 1.15.2` | Java 8 |
| `1.4.0 ~ 1.5.0` | `1.7.2 - 1.13.2` | Java 8 |
| `1.0.0 ~ 1.3.3` | `1.7.2 - 1.13.2` | Java 7 |

---

## 已知问题与排障建议

- **1.13 ~ 1.13.1 兼容性说明**  
  这些版本的服务端不支持 Java 9 及以上，运行中可能出现错误。建议直接升级至 [**1.13.2**](https://papermc.io/legacy)。  
  ~~（旧方案：修改服务端内 `org/objectweb/asm/ClassVisitor.class`，详见此[补丁](https://pastebin.com/UFBdKXJD)）~~

- **常见启动或功能异常**  
  大多数问题源于配置文件或数据损坏，可尝试以下步骤：
  1. 关闭服务器
  2. 删除 `plugins/ScriptBlockPlus/json` 目录下的 `format.sbp` 文件
  3. 重启服务器，插件会自动重建配置
  4. 如果问题仍存在，请到 [GitHub Issues](https://github.com/yuttyann/ScriptBlockPlus/issues) 提交反馈

- **插件完全无法启动**  
  请检查当前 Java 版本是否符合上表中的要求。

---

## 支持的服务端类型

只要服务端实现了 **BukkitAPI**，理论上均可运行。如果找不到 NMS 相关类，依赖 NMS 的功能会自动禁用。

以下服务端经过测试确认可用：

| 服务端 | 简介 |
|--------|------|
| [Spigot](https://www.spigotmc.org/) | 最常用的服务端，性能均衡 |
| [Paper](https://papermc.io/) | Spigot 优化版，提供更多配置项 |
| [Folia](https://papermc.io/software/folia) | 支持并行处理，完全兼容 |
| [Tuinity](https://ci.codemc.io/job/Spottedleaf/job/Tuinity/) | Paper 优化分支，适用于大型服务器 |
| [Yatopia](https://yatopiamc.org/) | Tuinity 再优化，整合多种补丁 |
| [Purpur](https://purpur.pl3x.net/) | Tuinity 分支，增加大量自定义特性 |
| [Akarin](https://github.com/Akarin-project/Akarin) | Paper 分支，专注性能提升 |
| [Mohist](https://mohistmc.com/) | Forge + Spigot（Paper）混合端 |
| [Magma](https://magmafoundation.org/) | 同样支持 Forge + Spigot（Paper） |

---

## 下载渠道

| 平台 | 语言 | 说明 |
|------|------|------|
| [FileArchive](https://github.com/yuttyann/FileArchive/tree/main/ScriptBlockPlus) | 日语 | 作者发布的编译版本存档 |
| [SpigotMC](https://www.spigotmc.org/resources/78413/) | 英语 | 面向国际用户的发布页 |
| [MCBBS](https://www.mcbbs.net/thread-691900-1-1.html) | 中文 | 国内 Minecraft 论坛，有热心玩家提供介绍与搬运 |

---

## 相关链接

- 完整功能介绍（日文）：[MCPoteton](https://mcpoteton.com/mcplugin-scriptblockplus)
