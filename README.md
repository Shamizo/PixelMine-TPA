## Fork 3.3.1 修改说明

本 fork 基于 [WarSkyGod/TPA 3.3.1](https://github.com/WarSkyGod/TPA)，合入了 upstream 的 Folia 传送死锁修复，并保留了 `/rtp` 在 Folia/Canvas 上的异步区域安全实现。

### 修改内容

- **合入 upstream 3.3.1**：`/home`、`/back`、`/warp` 的传送先派发到异步线程，再交给 FoliaLib 实体调度器，避免从区域线程直接进入可能阻塞的传送路径。
- **修复 `/rtp` 跨区域崩溃**：修复 `/rtp` 在 Folia/Canvas 上执行时的 `Cannot retrieve chunk asynchronously` 崩溃。
- **异步区块加载**：将 `getHighestBlockYAt`/`getBlockAt` 改为异步 —— 使用 `world.getChunkAtAsync` 异步加载区块，在区块所在区域线程的回调里读取方块高度，避免阻塞主线程/区域线程。
- **安全落点重试**：最多重试 64 次以找到安全的随机落点。
- **回调生命周期保护**：玩家离线、移动取消或 RTP 超时后停止后续异步重试，并保证跨线程落点对轮询任务可见。

### 兼容性

| 服务端 | 兼容性 | 说明 |
|--------|--------|------|
| Folia | ✅ 兼容 | 走异步区块加载逻辑 |
| Canvas | ✅ 兼容 | 走异步区块加载逻辑 |
| Paper/Purpur | ✅ 兼容 | 走原有同步逻辑 |

### 构建方式

```bash
mvn package -DskipTests
```

**Java 版本要求**：Java 21

---

# PMS-TPA
[English](https://github.com/WarSkyGod/TPA/blob/main/README_en-US.md)

一个支持 **Folia/Canvas** 的简易传送插件，支持 **Bukkit/Spigot/Paper/Folia/Canvas**。

## 特点

- **简单**：易于使用和配置。
- **可配置**：支持自定义配置文件。
- **多语言支持**：内置多种语言文件。
- **可自定义语言文件**：支持用户自定义语言文件。

## 命令

### 传送
- **/tpa <玩家名称>**  
  向玩家发送传送请求。
- **/tpahere <玩家名称>**  
  请求玩家传送到你身边。
- **/tpall [player/warp/spawn] [玩家名称/传送点名称]**  
  强制将所有在线玩家传送到目标位置（如果不加参数，默认传送到使用者身边）。
- **/tplogout <玩家名称>**  
  传送到该玩家最后一次下线的位置。
- **/tpaccept**  
  接受传送请求（你可以点击聊天框里的 **[接受]** 来直接接受）。
- **/tpdeny**  
  拒绝传送请求（你可以点击聊天框里的 **[拒绝]** 来直接拒绝，点击聊天框中的 **[拒绝并拉黑]** 将拒绝请求并且拉黑该玩家）。
- **/denys [add/remove] [玩家名称]**  
  列出玩家的黑名单列表。

### 传送点
- **/warp <传送点>**  
  传送到传送点。
- **/setwarp <传送点>**  
  设置传送点。
- **/delwarp <传送点>**  
  删除传送点。

### 家
- **/home <家>**  
  传送到家。
- **/homes**  
  列出你设置的家。
- **/sethome <家>**  
  设置家。
- **/setdefaulthome <家>**  
  设置默认的家。
- **/delhome <家>**  
  删除家。

### 主城
- **/spawn**  
  传送到主城。
- **/setspawn**  
  设置主城。
- **/delspawn**  
  删除主城。

### 其他
- **/back**  
  传送到上一次的位置。
- **/rtp**  
  随机传送。
- **/tpa setlang <clear/语言>**  
  设置客户端显示语言。
- **/tpa reload**  
  重新加载配置文件。

## 权限

### 管理员
- **tpa.admin**  
  最高权限，可执行所有操作。  
- **tpa.tpall**  
  可使用 `/tpall` 命令强行将所有在线玩家传送到你的位置。  
- **tpa.tplogout**  
  可使用 `/tplogout` 传送到玩家最后的下线位置。  
- **tpa.reload**  
  可使用 `/reload` 命令重新加载配置文件。
- **tpa.nodelay**  
  拥有这个权限的玩家不会受到命令等待时间的限制。  

### 传送
- **tpa.tpa**  
  可使用 `/tpa` 命令请求传送到指定玩家的位置（默认关闭权限检查）。
- **tpa.tpahere**  
  可使用 `/tpahere` 命令请求指定玩家传送到你的位置（默认关闭权限检查）。
- **tpa.rtp**
  可使用 `/rtp` 命令随机传送（默认关闭权限检查）。

### 黑名单
- **tpa.denys**  
  可使用 `/denys` 管理黑名单。

### 主城
- **tpa.spawn**  
  可使用 `/spawn` 命令传送到主城（默认关闭权限检查）。
- **tpa.setspawn**  
  可使用 `/setspawn` 命令设置主城。
- **tpa.delspawn**  
  可使用 `/delspawn` 命令删除主城。

### 传送点
- **tpa.spawn**  
  可使用 `/warp` 命令传送到传送点（默认关闭权限检查）。
- **tpa.setwarp**  
  可使用 `/setwarp` 命令设置传送点。
- **tpa.delwarp**  
  可使用 `/delwarp` 命令删除传送点。

### 家
- **tpa.home**  
  可使用 `/home`、`/homes`、`/sethome`、`/setdefaulthome`、`/delhome`（默认关闭权限检查）。

### VIP
- **tpa.vip**
- **tpa.vip+**
- **tpa.mvp**
- **tpa.mvp+**
- **tpa.mvp++**  
  可在配置文件中设置拥有该权限的玩家最多可以设置多少个家（-1 为不限制）以及命令间隔时间等。

### 其他
- **tpa.back**  
  可使用 `/back` 命令传送到上一次的位置（默认关闭权限检查）。

## 关于配置迁移问题

3.2.0 版本添加了老版本配置文件自动迁移的功能，无需过多担心。  
但出于安全考虑，迁移后会自动备份旧版本配置文件到 `plugins/PMS-TPA/backup/旧版本号` 目录下。如果迁移失败，请手动尝试迁移配置文件。

## 感谢

本插件使用了 [FoliaLib](https://github.com/handyplus/FoliaLib) 来做 **Folia** 兼容。

### 代码贡献者
- [fanlepian1](https://github.com/fanlepian1)
- [Apleax](https://github.com/Apleax)
- [LFWQSP2641](https://github.com/LFWQSP2641)
- [sky-3311](https://github.com/sky-3311)
- [Ukiyograin](https://github.com/Ukiyograin)

### Bug 报告者
- [fanlepian1](https://github.com/fanlepian1)
- [Apleax](https://github.com/Apleax)
- [luckeist](https://github.com/luckeist)
- [DuckCattyCotton](https://github.com/DuckCattyCotton)
- [Ry4nnnnn](https://github.com/Ry4nnnnn)
- [sky-3311](https://github.com/sky-3311)
- [zhgiuu45](https://github.com/zhgiuu45)
- [notwhitebear](https://github.com/notwhitebear)
- [Amin2478](https://github.com/Amin2478)
- [TritiumBro](https://github.com/TritiumBro)
- [XiaoST-one](https://github.com/XiaoST-one)
- [Cc-Cece](https://github.com/Cc-Cece)
- [Ukiyograin](https://github.com/Ukiyograin)
- [xszx-tbh](https://github.com/xszx-tbh)
- [Carrot520](https://github.com/Carrot520)
- [NightCat](https://github.com/nightlight-hk)


### 功能建议者
- [Apleax](https://github.com/Apleax)
- [xiaoleaw](https://github.com/xiaoleaw)
- [Onako2](https://github.com/Onako2)


### 翻译提供或修正
- 越南语 [Mytai20100](https://github.com/Mytai20100)


### AI 大模型
- [DeepSeek](https://www.deepseek.com/)

---
