# 纪念日 AnniversaryApp

一个纯本地、无需登录的 Android 纪念日记录应用。支持阳历/农历日期、提前提醒、桌面小组件、深色模式，以及 JSON 文件导出/导入备份。

## 功能

- **纪念日管理**：添加、编辑、删除纪念日，支持生日 / 纪念日 / 节日 / 自定义四种类型，可填写备注
- **阳历与农历**：可按农历录入日期（含闰月），每年自动换算为当年阳历日期
- **每年重复**：开启后按周年循环计算"距今天数"
- **分类与搜索**：按类型 Tab 筛选，Toolbar 搜索框按名称模糊搜索
- **批量操作**：长按或菜单进入选择模式，可全选、批量删除
- **提醒通知**：可设置提前 1 / 3 / 7 / 15 / 30 天提醒，并在"我的"页面统一设置每日推送时间；设备重启后自动重新调度
- **桌面小组件**：展示最近 10 条即将到来的纪念日，点击进入详情
- **深色模式**：手动切换并持久化，也可跟随系统
- **分享**：详情页一键分享纪念日文字
- **数据导出 / 导入**：通过系统文件选择器将全部记录导出为 JSON，或从 JSON 文件追加导入，无需存储权限

应用不申请网络权限，所有数据保存在设备本地的 Room 数据库中。卸载应用会删除数据，请定期在"我的"页面导出备份。

## 环境要求

| 项目 | 版本 |
|---|---|
| JDK | 21 |
| Gradle | 9.4.1（已提供 wrapper） |
| Android Gradle Plugin | 9.2.1（使用内置 Kotlin） |
| KSP | 2.2.10-2.0.2 |
| compileSdk | 36.1 |
| minSdk / targetSdk | 21 / 34 |

Android Studio 需使用支持 AGP 9.x 的版本。

## 编译与安装

```bash
# 编译 Debug 包
./gradlew :app:assembleDebug

# 编译 + 单元测试 + Lint
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

# 安装到已连接的设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows 下使用 `gradlew.bat`。部分国产 ROM（如 MIUI / HyperOS）上 `gradlew installDebug` 可能返回 `-99` 错误，请改用上面的 `adb install -r`。

`apk/` 目录中存放的是历史打包版本，可能落后于源码，建议自行编译最新版本。

## 项目结构

```
app/src/main/java/com/anniversary/app/
├── AnniversaryApplication.kt   # 应用入口：夜间模式恢复、通知渠道
├── data/
│   ├── entity/Anniversary.kt   # Room 实体与类型枚举
│   ├── dao/AnniversaryDao.kt   # 数据访问接口
│   ├── database/               # Room 数据库与迁移脚本（当前版本 4）
│   └── repository/             # 数据仓库
├── notification/
│   ├── ReminderScheduler.kt    # AlarmManager 精确提醒调度
│   ├── ReminderReceiver.kt     # 提醒广播接收与通知发送
│   ├── ReminderSettings.kt     # 每日提醒时间设置
│   ├── ReminderHistory.kt      # 防重复提醒记录
│   └── BootReceiver.kt         # 开机后重新调度
├── ui/
│   ├── main/                   # 首页列表、筛选、搜索、选择模式
│   ├── add/                    # 新增 / 编辑页
│   ├── detail/                 # 详情页
│   ├── profile/                # "我的"页：深色模式、提醒时间、导出导入
│   ├── adapter/                # RecyclerView 适配器
│   └── widget/                 # 桌面小组件
└── util/
    ├── DateUtils.kt            # 日期计算
    ├── LunarCalendar.kt        # 农历换算
    └── DataBackupUtils.kt      # JSON 导出 / 导入与校验

design/
├── 纪念日.png                  # 应用图标源图
└── gen_icons.ps1               # 生成各密度启动图标的脚本
```

## 数据备份格式

导出文件为 UTF-8 编码的 JSON：

```json
{
  "version": 1,
  "exportTime": 1757900000000,
  "anniversaries": [
    {
      "name": "结婚纪念日",
      "date": 1609459200000,
      "type": 1,
      "note": "",
      "isRepeatYearly": true,
      "reminderDays": 3,
      "isLunar": false,
      "lunarMonth": 0,
      "lunarDay": 0,
      "lunarIsLeapMonth": false,
      "createdAt": 1609459200000
    }
  ]
}
```

- `date` / `exportTime` / `createdAt` 为毫秒时间戳
- `type`：0 生日、1 纪念日、2 节日、3 自定义
- `reminderDays`：-1 表示不提醒，否则为提前天数
- 导入时会校验 `version`、必填字段和农历范围；文件中任一记录不合法则整体拒绝导入
- 导入采用**追加**方式，不覆盖已有记录；重复导入同一文件会产生重复记录

## 更换应用图标

将新的方形 PNG 放入 `design/` 目录（保持目录内只有一个 PNG），运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File design\gen_icons.ps1
```

脚本会重新生成 `mipmap-*` 下的自适应前景图、传统方形与圆形图标。
