# 模型配置与用量（2026-11-13）

Flutter 默认外观为浅色觉醒紫；已有外观偏好继续按保存值恢复。初始原生窗口也使用浅色底，系统深色不会把新安装的应用默认切成深色。

关于页保留品牌故事与版本。数据去向说明放在 Flutter 新手引导，首次未配置模型时显示，也可从设置重新打开。设置「关于」组不展示开源许可入口；许可、署名数据与 Flutter 依赖许可继续随安装包保留。

## 阶跃星辰

添加页只展示一个阶跃星辰预设，地址旁左 API、右 Plan。两个地址分别是 `https://api.stepfun.com/v1` 和 `https://api.stepfun.com/step_plan/v1`。已有接入仍能选择和编辑。两个官方站点路径可沿用同一服务商 Key；更换为其他地址时，不自动转发已保存的 Key。

填 Key 后可获取模型，也可手填。列表请求不保存草稿 Key，不发起对话，也不占用设置写锁。修改地址或 Key 后，旧结果不会覆盖新草稿。列表失败仍保留手填入口。列表内存缓存绑定接口类型、地址和 Key 指纹；保存只保留选择过的模型元数据，不把整份远端目录变成已配置入口。官方接口中的语音转写、合成和实时语音专用条目不作为手机任务模型展示。

工具调用是任务模型的必要能力；图片输入可设置。思考强度沿用接口明确声明的档位，缺失时只对官方指南确认过的模型补充：Step 3.7 Flash / Step 5 Preview 为低、中、高；Step 3.5 Flash 2603 为低、高；不向未知模型猜测参数。选择「模型默认」时不发送 `reasoning_effort`。已保存但不再受支持的旧档位不会使编辑页崩溃，会提示保存后改用默认。能力和思考选择保存到原生模型存储，思考选择按模型保留，切换和重启后可恢复。

依据：[推理指南](https://platform.stepfun.com/docs/zh/guides/developer/reasoning)、[Step 3.7 Flash](https://platform.stepfun.com/docs/zh/guides/models/step-3.7-flash)、[Step 3.5 Flash](https://platform.stepfun.com/docs/zh/guides/models/step-3.5-flash)、[Plan 接入](https://platform.stepfun.com/docs/zh/step-plan/integrations/reasoning-api)、[模型列表](https://platform.stepfun.com/docs/zh/api-reference/models/list)。

## 真实点击与 Token

需要记录的元素点击优先走真实触摸输入，在实际可见目标范围内选择一点，保存分发的像素坐标。截图上的准星标记这一点；触摸通道不可用而使用无障碍语义点击时仍只记控件范围，不把中心点伪装成触摸证据。已有历史范围记录不重写。

执行记录显示任务合计和每步 Token。合计优先用 `RunEvent.ModelCalls`，包括运行中的辅助调用；旧记录或运行尚未产生合计时退回已记录的主循环，并注明口径。输入已包含缓存，不重复相加。没有服务商用量时明确显示未返回，不把未知用量当作实测零值。

模型配置页读取原生 `UsageLedger` 的近 30 个本地日历日，包含辅助调用、API/Plan 分项、输入、输出、缓存命中和缺失用量的请求。该统计是本机请求记录，不是账户余额或套餐剩余额度。
