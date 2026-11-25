---
name: amoo-growth
display: Amoo 成长计划
packages: ["com.stepos.ailauncher"]
aliases: ["Amoo 成长计划", "Amoo成长计划", "成长计划", "A 豆签到"]
open: Amoo 成长计划
description: STEPX 账号中的每日签到
---
用户要查看或完成 Amoo 成长计划签到时，优先用一条 `phone_run` 打开 STEPX 账号页并进入成长计划。第一步传 `op=open_link`、`action=com.stepos.account.ACCOUNTCENTER` 和 `packageName=com.stepos.account`，第二步传 `op=tap`、`targetText=Amoo 成长计划`。若入口不可用，从系统「设置」进入「账号管理和我的设备」。

账号页和成长计划页优先读取界面树。账号页通过文字点击「Amoo 成长计划」，可在 `phone_run` 中直接用 `targetText`，由工具重新定位并回读。进入后要分别辨认「已签到」状态和可点击的「签到」按钮。已经签到时直接报告；只有用户要求代签到且页面显示可点击的「签到」时才点击一次，再核对结果。目标读不到或不唯一时，改用当前页面的本机文字或截图核对。
