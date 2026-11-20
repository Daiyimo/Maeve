# Ask and Autonomous

[简体中文](modes.md) | English

When Maeve works for you, the mode decides which soft restrictions it checks with you first. There is one global switch: "Developer options" (开发者选项) → "Autonomous mode" (自主模式) in Settings. Off means Ask, on means Autonomous (default).

| Mode | Behavior |
|---|---|
| **Ask** | Outward, irreversible or unresolved targets are confirmed first; every WeChat write is confirmed too. The card says where and what, and you can approve just this once or decline. Payment itself is completed by you. |
| **Autonomous** (default) | Ordinary reversible writes continue. WeChat per-write confirmation and the blind-tap restriction on self-drawn screens are waived. Outward, irreversible, tool-declared risk and payment still stop; payment is handed back to you. Batch spacing still applies. |

- The switch applies to all tasks from then on; if a running task switches from Ask to Autonomous, only WeChat per-write confirmation and blind taps on self-drawn screens are relaxed from the next step. Existing prompts still wait for an answer.
- In both modes Maeve asks you to take over for things only you should do, like logging in, verification codes and payment passwords. It also asks when information is missing or only you can decide (e.g. which of two contacts with the same name).
- For tasks that run while you are away, such as scheduled tasks, any step that needs user confirmation is denied without waiting, and Maeve tells you plainly which step was not done. Everything else is completed as usual.

You are responsible for actions you approve, and for actions Maeve takes on your behalf in Autonomous mode. For anything important, use Ask mode and check the results; see the [disclaimer](disclaimer.en.md).
