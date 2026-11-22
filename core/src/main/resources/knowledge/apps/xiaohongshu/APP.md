---
name: xiaohongshu
display: 小红书
packages: ["com.xingin.xhs"]
aliases: ["Xiaohongshu", "REDnote", "Red Note", "小红书", "XHS", "xiaohongshu", "xhs"]
sites: ["xiaohongshu.com"]
open: 小红书
pace: careful
description: 笔记、搜索与社区
---
## 是什么
小红书是内容与社交 App,包名 `com.xingin.xhs`。网站知识见 `xiaohongshu.com`。

## 界面
通常有首页信息流、搜索、发布、消息和个人页。搜索结果混有笔记、用户、话题、地点和商品卡片,外观相近,要看标签和作者区分。

笔记详情里有作者入口、正文展开、评论区、收藏、点赞、分享和商品关联入口。上面的数字可能是互动量,也可能是评论楼层或商品信息。

## 功能
- **搜索**:结果有标题、作者、点赞数、封面、类型和链接。通常需要登录。
- **笔记详情**:正文、作者、互动量、评论。详情依赖有效的会话,自己拼的详情网址取不到内容。

## 读取策略
小红书与微信一样优先视觉读取:先用截图和本机 OCR 看页面,用视觉定位或坐标点击。不要调用 list_elements/query/find 等 UI 树工具,也不要用树 ref 操作;这是为了降低触发风控的风险。页面模块以当前截图的局部标题和卡片内容为准,sticky tab 不代表下面所有卡片都属于该 tab。

## 已知现象
信息流、热度、价格和库存都是动态内容。
