[Java17] ScriptBlockPlus [MC1.21.8-26.1.2] [![build](https://img.shields.io/github/actions/workflow/status/rmoyulong/ScriptBlockPlus/maven-publish.yml)](https://github.com/rmoyulong/ScriptBlockPlus/actions/workflows/maven-publish.yml) [![downloads](https://img.shields.io/github/downloads/rmoyulong/ScriptBlockPlus/total)](https://github.com/rmoyulong/ScriptBlockPlus/releases) [![downloads@latest](https://img.shields.io/github/downloads/rmoyulong/ScriptBlockPlus/latest/total)](https://github.com/rmoyulong/ScriptBlockPlus/releases/latest)<br>

==========

概述
-----------
这是一个继承了 [ScriptBlock](https://dev.bukkit.org/projects/scriptblock) 功能的插件，并添加了新的特性和改进。

（我们目前正在开发一个可在多个平台上运行的后续插件。）

### 主要特性

- **Folia 兼容**：完全兼容 Folia 服务端，确保原有功能和新增功能在 Folia 服务端均可用。

- **辅助书箱生成**：所有功能（文章编辑和近似调整）的图形用户界面实现，书籍数量可完全调整

- **总攻击次数显示**：当鼓励箱被击败时，显示玩家受到的攻击次数

- **区域打印体验**：可完全控制怪物点位的放置、数量、探测半径、生物ID（原始生物和mmmobs插件生物）、区域内最大怪物数量以及局部放置。

安装
-----------

只需将下载的`ScriptBlockPlus`（https://github.com/yuttyann/FileArchive/tree/main/ScriptBlockPlus）保存到`plugins`文件夹即可。

### Java 8 版本

**ScriptBlockPlus** 的 **Java 8 版本** 位于单独的[仓库](https://github.com/yuttyann/ScriptBlockPlus-Java8) (https://github.com/yuttyann/ScriptBlockPlus-Java8/releases)。

如果 **Java 11** (https://adoptopenjdk.net/?variant=openjdk11) 在旧平台服务器上无法运行，请使用此版本。

但是，请注意，我们不提供此插件的支持，并且将在不久的将来停止更新。

**自插件版本 v2.1.2 起，已停止更新。**

### 集成插件

| 插件 | 描述 |

|:---|:---|

| [Vault](https://www.spigotmc.org/resources/vault.34315/) |允许您使用权限和经济插件的功能。|

| [DiscordSRV](https://www.spigotmc.org/resources/discordsrv.18494/) | 允许您使用 Discord 功能。|

| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | 允许您扩展占位符的功能。|

| [ScriptEntityPlus](https://github.com/yuttyann/ScriptEntityPlus) | 允许您为实体设置脚本。|

**== 各版本运行范围 ==**

| 插件 | 服务器 | Java |

|:---|:---|:---:|

|**`2.5.1`**|**`1.21.8-6.1.2`**|**Java17**|

|**`2.4.0`**|**`1.9-1.21.8`**|**Java17**|
|`2.3.4`|`1.9-1.21.8`|**Java11**|
|`2.3.3`|`1.9-1.21.5`|**Java11**|
|`2.3.0-2.3.2`|`1. 9-1.21`|**Java11**|
|`2.2.7-2.2.8`|`1.9-1.20.2`|**Java11**|
|`2.2.6`|`1.9-1.20.1`|**Java11**|
|`2.2.5`|`1.9-1.19.3`|**Java11**|
|`2.2.3-2.2.4`|`1.9-1.19.2`|**Java11**|

|`2.2.0-2.2.2`|`1.9-1.18`|**Java11**|

|`2.1.5-2.1.8`|`1.9-1.17.1`|**Java11**|

|`2.1.2-2.1.4`|`1.9-1.17`|**Java11**|

|`2.0.4-2.1.1`|`1.9-1.16.5`|**Java11**|

|[`2.x.x-JV8`](https://github.com/yuttyann/ScriptBlockPlus-Java8)|`1.9-1.16.5`|**Java8**|

|`1.8.5-2.0.3`|`1.9-1.16.5`|**Java8**|

|`1.6.0-1.8.4`|`1.8-1.15.2`|**Java8**|

|`1.4.0-1.5.0`|`1.7.2-1.13.2`|**Java8**|

|`1.0.0-1.3.3`|`1.7.2-1.13.2`|**Java7**|

**== 关于版本“1.13-1.13.1”的运行情况 ==**

由于服务器不支持**Java9**或更高版本，因此存在导致错误的漏洞。 **插件本身运行正常，但可能存在问题，因此请使用[**1.13.2**](https://papermc.io/legacy)”。**解决方案（已弃用）：**修改`<Server>.jar`中的`org/objectweb/asm/ClassVisitor.class`文件（https://pastebin.com/UFBdKXJD）即可使其正常工作。

**== 插件问题 ==**

大多数报告的问题都与数据文件和配置文件有关。

报告问题时，请删除`plugins/ScriptBlockPlus/json`中的`format.sbp`文件并重新生成插件。

此外，由于配置文件通常会随着更新而更改，因此建议重新生成。

**（如果重新生成或删除数据和配置文件后问题仍然存在，请将其作为 Issue 提交（https://github.com/yuttyann/ScriptBlockPlus/issues）。）**

如果插件完全无法启动，请检查您的 Java 环境。版本。` `

支持的平台
-----------

**如果您已实现 `BukkitAPI`(https://hub.spigotmc.org/javadocs/bukkit/overview-summary.html)，**它基本上可以正常工作。

如果找不到 `net.minecraft.server`**(https://sodocumentation.net/ja/bukkit/topic/9576/nms)，**依赖于 NMS 的功能将被强制禁用。**

以下是已测试可运行的服务器列表。

| 服务器 | 描述 |

|:---|:---|

|[Spigot](https://www.spigotmc.org/)| 一个常用的服务器，性能一般。|

|[Folia](https://papermc.io/software/folia)| 一个并行处理服务器，完全支持。|

|[Paper](https://papermc.io/)| Spigot 的一个衍生服务器，具有优化和更多详细设置。|

|[Tuinity](https://ci.codemc.io/job/Spottedleaf/job/Tuinity/)|Paper 的一个衍生服务器，针对大规模服务器进行了优化。|

|[Yatopia](https://yatopiamc.org/)|Tuinity 的一个衍生服务器，针对各种服务器平台应用了优化补丁。|

|[Purpur](https://purpur.pl3x.net/)|Tuinity 的一个衍生服务器，添加了各种独特功能。|

|[Akarin](https://github.com/Akarin-project/Akarin)|Paper 的一个衍生服务器，旨在提高性能。|

|[Mohist](https://mohistmc.com/)|结合了 Forge 和 Spigot（Paper）的功能。|

|[Magma](https://magmafoundation.org/)|同时具备 Forge 和 Spigot（Paper）的功能。 |

下载
-----------

| 网站 | 语言 | 描述 |

|:---|:---|:---|

| [FileArchive](https://github.com/yuttyann/FileArchive/tree/main/ScriptBlockPlus) | `日语` | 这是作者编译发行版的仓库。 |

| [SpigotMC](https://www.spigotmc.org/resources/78413/) | `英语` | 这是作者用于向海外用户分发的网站。 |

| [MCBBS](https://www.mcbbs.net/thread-691900-1-1.html) | `中文` | 这是一个中文 Minecraft 论坛，志愿者在此提供说明和发行版。 |

链接
-----------

| 页面 | 描述 |

|:---|:---|

| [MCPoteton](https://mcpoteton.com/mcplugin-scriptblockplus) | 这里解释了所有功能。 |