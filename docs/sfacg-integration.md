# 菠萝包账号接入（v0.15.0）

## 数据与账号边界

账号/手机号及密码直接发送到 `https://api.sfacg.com/sessions`，从响应取得 `.SFCommunity` 和 `session_APP`，再通过 `GET /user` 验证账号。不把网站 `session_PC` 当作 App 登录凭据，不保存密码，不记录凭据或原始登录错误响应。App 请求禁止重定向，避免携带会话跳往其他地址。请求使用参考客户端的协议兼容设备标识，不采集本机硬件标识；公开接口实测中，随机设备标识会被正文接口以 401 / 507 拒绝。

书架通过 `GET /user/Pockets?expand=novels` 读取，按 30 本分段展示；如上游不再内嵌作品则尝试分类作品分页接口。目录通过 `novels/{id}/dirs` 读取，正文通过 `Chaps/{id}` 读取。只有登录使用 POST，其余只使用 GET；没有充值、订阅、自动购买、收藏增删或远端进度写入。

正文做字符替换映射及 HTML 清理。试读标记、未购标记和空正文都不能计入下载成功。会话失效及账号风控暂停下载，重新登录后手动继续；其他缺章保留错误清单并可重试。成功正文先写入数据库，任务恢复时复用这些正文。

远端章节保留稳定 ID，在线缓存和主动下载使用独立标记。刷新目录保留已保存正文、阅读记录、收藏标记和 AI 衍生章节。退出账号保留本地内容，但清除 App 会话和远端书架列表。导出仅包含所选范围已有的正文，缺章版本必须显式确认，并在文件前部附完整缺章列表。

## 开发验证

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python tools/test_sf_migration.py
pwsh -File tools/Test-SfPublic.ps1
```

单元测试覆盖 App Cookie、登录校验、签名向量、错误分类、风控、取消、免费/付费权限分流、书架分页、目录、缺章导出及 EPUB 容器/XML/目录顺序。迁移检查使用真实 Room 1/2 导出 Schema，验证旧数据保留及缓存清理不删除主动下载内容。匿名冒烟脚本只访问公开目录与免费章节，不读取任何账号凭据。

账号验收需要在应用内执行：登录并确认昵称、书架分类和更多作品；打开账号已购 VIP 章；验证未购章不会自动购买；重新打开应用确认会话；退出账号确认本地正文和进度保留；重新登录继续暂停任务。遇到验证码或平台风控时，需要到官方 App 完成验证，不能仅据登录接口返回成功便宣称已购正文验证成功。

## 协议参考与字符映射来源

- [NeiHanH/sfacg_downloader_improved](https://github.com/NeiHanH/sfacg_downloader_improved)：App 请求签名、会话获取与字符替换映射参考。`app/src/main/assets/sf-map.json` 使用此前本机 SF 阅读器已验证的相同映射数据；映射随上游变化可能需要维护。
- [VeronicaAlexia/sfacgLib 的已发布 Go 模块](https://pkg.go.dev/github.com/VeronicaAlexia/sfacgLib@v0.0.0-20230724132746-d53e09b72d6a/boluobao/client)：账号和书架接口、`expand=novels` 数据结构参考。

这些是社区协议实现，不是菠萝包官方开放 API 保证。源码适配和匿名读取通过，不代表账号安全验证或所有 VIP 章节已验证。
