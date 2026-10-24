# UI 设计（设计系统与各端规范）

> 版本口径：2026-10-24（第 2 版，补充布局系统 / 交互规范 / 高频页交互模式）。隶属 [./PRD.md](./PRD.md) 需求文档集。契约级细节以 [../Element-Plus迁移计划.md](../Element-Plus迁移计划.md)、`src/main/resources/shared/styles/tokens.css`、`backend/js/components-v3.js` 源码与 `miniapp/README.md` 为唯一事实来源；本文是设计摘要 + 指向。
>
> ⚠ 历史文档口径提醒：[../后台页面清单.md](../后台页面清单.md) §7–§9 是 2026-08 的实用化规划与提示词模板，仅作设计意图参考、不作为现状证据；其中与现行规范冲突处（典型如「金额/数字列右对齐」）**一律以现行规范为准**——2026-09-01 起全站表头与内容一律居中（AGENTS.md「crud-table 列对齐规范」）。

---

## 1. 总体形态：两套前端体系，不互相「顺手统一」

| 体系 | 端 | 技术 | 特点 |
|---|---|---|---|
| **Web 静态资源**（Spring Boot 直接伺服，无编译，改完硬刷新即生效） | 管理后台 `/backend/`、顾客 H5 `/front/`、骑手 H5 `/rider/`、共享 `/shared/**` | 后台 Vue 3 + Element Plus（全局 build，Options API）；顾客/骑手 Vue 3 + Vant 4 | 同一体系内共享设计令牌与 `js/request.js` |
| **小程序**（pnpm workspace + uni-app，Vue 3 + Vite，有编译，产物在 `apps/*/dist/` 不入库、不经 Spring Boot 伺服） | `apps/customer`（24 页）/ `apps/rider`（9 页）/ `apps/staff`（5 页） | uni-app，**Options API、纯 JS，禁 `<script setup>` / TypeScript** | 令牌从 Web 侧同步而来，禁手改产物 |

页面规模：后台 34 目录 / 80 功能页（+ 标准模板 `page/_templates/list-page.html`）；顾客 H5 26 页；骑手 H5 8 页。API 一律 `api/*.js` → `js/request.js`（Axios 封装 + CSRF 注入）。

---

## 2. 设计令牌（唯一颜色/字号/间距来源）

真源：`src/main/resources/shared/styles/tokens.css`（Web 三端引用）；小程序侧运行 `pnpm sync:tokens` 同步（脚本把 `:root {` 改写为 `page, :root {`，WXSS 才吃得到），产物 `packages/shared/src/styles/tokens.css` **禁止手改**。

### 2.1 品牌色：美团黄系

| 令牌 | 值 | 用途 |
|---|---|---|
| `--color-primary` | `#ffc200` | 主色（金黄） |
| `--color-primary-dark` / `-light` | `#ff9a00` / `#ffd54f` | 深浅变体 |
| `--color-primary-gradient` | `135deg #ffc200 → #ff9a00` | 主按钮/强调块渐变 |
| `--color-primary-text` | `#4a3500` | 金底上的深棕文字（对比度） |
| `--color-price` | `#ff4a26` | 价格专用橙红 |

语义色：`--color-danger #e94e3c`、`--color-success #07c160`、`--color-warning #ffb300`；每个语义色配套 `-text`（深色文字版）与 `-tint`（12% 透明度浅底）变体。平台品牌色例外（仅图表场景）：微信 `#07c160`、支付宝 `#1677ff` 允许裸 hex。

### 2.2 中性色 / 背景 / 边框

- 文字五档：`--color-text-primary #333` / `secondary #666` / `hint #6e6e6e` / `disabled #ccc` / `placeholder #6e6e6e`。
- 背景：`--bg-page #f3f2f7`（页面底）、`--bg-card #fff`、`--bg-input #f8f9fa`、`--bg-warm #fdf9f0`（暖色场景，如登录/会员）。
- 边框四档：`--border-light #eee`（分割线）/ `--border-medium #ddd`（输入框卡片）/ `--border-warm #d8c8a0` / `--border-hairline`（卡片内发丝线）。

### 2.3 度量体系

| 类别 | 档位 |
|---|---|
| 圆角 | `--radius-sm 4 / md 8 / lg 12 / xl 16 / round 50%` |
| 间距 | `xs 4 / sm 8 / md 12 / lg 16 / xl 20 / 2xl 24 / 3xl 32`（px） |
| 字号 | `xs 10 / sm 12 / md 14 / lg 16 / xl 18 / 2xl 20 / 3xl 24 / display 28`（px） |
| 阴影 | `sm / md / card / card-hover / bar-top` + 主色投影 `shadow-primary`（按钮悬浮） |
| 动效 | `--transition-fast 0.15s` / `normal 0.25s`（统一贝塞尔曲线） |
| 字体 | 系统栈：-apple-system → PingFang SC → Microsoft Yahei |

### 2.4 硬编码 hex 禁令与三类合规例外

颜色一律 `var(--token)`，**禁止硬编码 hex**。例外（属令牌单源模式，勿当违规「修复」）：
① JS/canvas（ECharts）无法用 `var()`，走 `components-v3.js` 的 `window.XinfeiPalette`（运行时读令牌、hex 仅回退）；
② CSS `var(--token, #hex)` 回退值；③ HTML 内联同理。

---

## 3. 管理后台（Vue 3 + Element Plus）

### 3.1 壳层与页面骨架

- 壳层 `backend/index.html`：导航菜单 / iframe 宿主 / 顶栏 / Ctrl+K 命令面板，与内容页同一 v3 装配（`plugins/vue3/` + `plugins/element-plus/` + `main-v3.css` + `components-v3.js` + `app-v3.js`）。
- 新页面照 `page/_templates/list-page.html` 标准骨架写；ECharts、RemixIcon 全站共用。
- **禁止**从 git 历史或任何途径重新引入已删除的 Vue 2 / Element UI 资产；禁止同页混用两套组件库。

**列表页布局定式（自上而下，全站统一）**：

```
┌───────────────────────────────────┐
│ 页头：标题 + 副标题（+ 主操作按钮） │
├───────────────────────────────────┤
│ [统计卡片区 stat-cards]（可选）      │  点击卡片即筛选
├───────────────────────────────────┤
│ [搜索/筛选栏 table-bar]            │
├───────────────────────────────────┤
│ [主体：crud-table / 表单 / 图表]   │
├───────────────────────────────────┤
│ [分页器]（底部居中）                │
└───────────────────────────────────┘
```

页面水平内边距 24px、区块垂直间距 16px；卡片容器 = 白底 + `--radius-lg` + `--shadow-sm` + 细边框。

### 3.2 导航与信息架构

- 一级菜单只放「工作台 + 主链路业务」，文案统一名词短语（「订单」「商品」，不带「管理」后缀）；扩展模块折叠进「更多/设置」。
- active 态主色高亮（图标+文字）；面包屑 `首页 / 一级 / 二级`，当前页不加链接。
- 历史规划（后台页面清单 §7.2）曾把 17 模块归并为 11 模块导航，落地情况以现行 `index.html` 为准。

### 3.3 组件契约（事实以 `components-v3.js` 源码为准）

**`<crud-dialog>`（禁止裸写 `<el-dialog>`）**：`v-model` 控制可见态；尺寸档单一来源 `DIALOG_SIZE`（sm 420 / md 560 / lg 720 / xl 840 / fullscreen 92%），`width` 优先于 `size`，禁止另造尺寸档或 `el-dialog--xl` 魔数 class；`beforeClose` 守卫（返回 false / resolve(false) 中止）；表单校验用 EP 内置 `:rules` + `validate()` inline 显示，**禁止只靠 `$message` toast 报字段错误**；确需裸写属例外，须先经批准。

**`<crud-table>` 列对齐（强制）**：唯一来源 `resolveColAlign(col)`——全站表头与内容**一律居中**（含金额/数字列），页面显式 `align` 仅作覆盖；表头跟随数据列，禁止单独写死；**禁止翻转默认值**（历史多次回退，每次翻转改全站行为）。CSS 三态单一来源 `styles/consistency.css` 的 `.is-left/.is-center/.is-right .cell`（均 `!important`）。

**列宽**：`col.minWidth` > `col.width`（降级为下限）> 自动分配；勿把各列 width 写成固定总和导致横向溢出。

**行操作列**：常显主操作 ≤3 个，超出收进「更多」dropdown；宽度档 2/3/4 元素 = 120/180/240px；语义类 查看 `btn-view`、启用 `btn-success`、停用/暂停 `btn-warning`、删除/取消 `btn-delete`。高频操作图标+文字，低频进下拉；全选列仅用于有明确批量操作的场景。

**`<stat-cards>` 统计卡**：`cards` 项 `{ key, icon, label, value, color, unit, subText, clickable, active }`；`clickable:true` 卡片可点击筛选并高亮（`card-click` 事件）；全站统一中性细边框 + hover 主色描边 + `translateY(-2px)` 抬升；**禁止**顶部/侧边语义色条（旧 `::before` 色条已删）；数值/图标语义色经 `--stat-color` 着色；改该样式表须同步递增 CSS 缓存版本号。

**选择器陷阱**：模板 `<el-table class="tableBox">` 使 `.tableBox` 与 `.el-table` 为**同一元素**，覆盖样式必须写复合选择器 `.tableBox.el-table xxx`（无空格），后代选择器永不匹配。

### 3.4 状态、表单、图表、反馈规范

| 类别 | 规范 |
|---|---|
| 状态展示 | 带色圆点 + 文字（`XinfeiStatus`）：成功/完成→success、警告/待处理→warning、危险/取消→danger、进行中→info/主色；同一实体状态在列表/详情/筛选项必须同一套映射 |
| 表单 | `:rules` + `validate()` inline 错误；必经 `crud-dialog` 承载；金额 `type="number"` + `precision:2`，展示统一 `XinfeiFormat.money`（等宽数字不跳动）；提交失败区分业务错误（单 toast）与网络错误（`request.js` 统一处理，避免双 toast） |
| 图表（ECharts） | 卡片式：header 左标题右副标题；配色优先 `XinfeiPalette`/语义色；图例 tooltip 用 `--color-text-secondary`；图表数据色走 `window.XinfeiPalette`（见 §2.4 例外①） |
| 空态 | 纯查询/报表页必须覆盖 `empty-hint`，禁止「点击右上角新建」误导文案（覆盖进度见 功能升级.md O-12）；首次使用空态带引导 + 主操作按钮 |
| 加载 | crud-table 内置骨架屏，保持行高稳定防跳动；慢请求 >500ms 显示 loading |
| 交互反馈 | 全部走 `window.XinfeiUI`（success 2s/error/warning/info），**禁止**混用 `this.$message/$confirm`；危险操作（删除/退款/取消）`XinfeiUI.confirm` 二次确认且文案含对象名；批量操作反馈成功数与失败明细 |
| 无障碍 | 保留 skip-link（「跳转到主内容」）、`role="region"` + `aria-label` |

### 3.5 高频页交互模式（已落地形态，新页参照）

设计五原则（后台页面清单 §8）：**任务是第一单元**（页面围绕使用者要完成的事组织）；**高频动作 ≤2 次点击**；**一屏看全关键信息**（金额/状态/库存/待办首屏可读）；**区分角色**（收银/店长默认布局不同）；**反馈即时不静默**。

| 页面 | 已落地交互模式（2026-08-30 实测） |
|---|---|
| 订单明细 `page/order/list.html` | 顶部 stat-cards 四卡（今日营业额/待接单/配送中/总订单数，点击即筛）+ 列表高亮行 + 键盘快捷键（Enter=接单、F=完成，输入框/弹窗打开时不响应）；退款仅已完成可退，弹窗实时显示可退上限 + 双重确认。设计蓝图（左状态 Tab 清单/中详情/右操作的三栏联动）与 crud-table 架构冲突，以「高亮行+键盘」折中，三栏适合 cashier 级独立重写页 |
| 收银台 `page/cashier/index.html` | 三区定式：桌台/扫码头+单号 → 订单/菜品 → 支付条（应收/实收/找零 + 支付方式大按钮 + 快捷金额 50/100/200）；现金自动找零、会员储值收款、大额 >200 弹确认、清空购物车二次确认；页内日结条（本日应收/实收/差异）+ 一键日结不跳页 |
| 堂食桌台 `page/dining/table-list.html` | 列表/卡片/桌型图三视图；桌型图按区域渲染桌号方块（状态色顶条），点击桌号右侧详情栏内联 开台/加菜/结账/转台/二维码；占用桌「加菜」跳收银台续点；开台自动建单、结账清台与订单打通 |
| 通用微优化 | 刷新按钮 + 空数据可刷；翻页保留页数记忆；金额/数量统一 `XinfeiFormat`；高频页绑快捷键并在按钮 title 标注 |

### 3.6 Vue2→3 / EP 硬性 API 差异

`v-model` 替代 `visible.sync`、`#prefix` 替代 `slot="prefix"`、`text` 替代 `type="text"`、`#icon` 插槽替代 `el-icon-*` 字体类——完整对照表见迁移计划 §2/§3，写页面前先读。

---

## 4. 顾客端 / 骑手端（H5，Vue 3 + Vant 4）

- 移动优先单列布局，底部 TabBar 导航；卡片化信息块（`--bg-card` + `--radius-lg` + `--shadow-card`）。
- 价格用 `--color-price`、主操作用主色渐变大按钮（`--shadow-primary` 悬浮投影）。
- 与后台**不共享组件库**（EP vs Vant），只共享令牌与请求层；禁止互相统一。
- SSE 实时推送在 H5 端可用；小程序端改轮询（见 §5）。

---

## 5. 小程序端（uni-app）

- 页面按 `miniapp/README.md` 台账逐页对照 Web 侧实现，接口路径/字段名照 Controller 原样；`@RequestParam` 语义的 POST/PUT 用 `qs()` 拼 URL（uni.request 无 axios params）。
- 样式约束：WXSS 自定义属性挂 `page` 选择器（`sync:tokens` 产物）；`<image>` 不支持 `var()`，图片地址经 `enums.js#fullImage` 拼绝对路径。
- 交互替代口径：SSE → 轮询/下拉刷新（骑手轨迹 15s、位置上报 12s）；AI 对话非流式。
- 明确不做：后台完整小程序（表单/图表密度不适合手机，staff 端仅 5 页高频场景）、支付宝内支付（灰显）、扫码点餐（暂缓）。

---

## 6. 交互与可用性基线（全端）

- **空态提示（empty-hint）**：查询页必须有空态占位，禁止裸表格/空白页；覆盖进度约 21/38，收口项见 功能升级.md O-12。
- **加载与错误反馈**：交互反馈统一 `window.XinfeiUI`；接口错误按 `R.code` 解包提示（401 回登录）。
- **危险操作**：删除/取消类操作必须二次确认；表单 dirty 时关弹窗走 `beforeClose` 守卫。
- **「能上线」定义**（后台页面清单 §4.4）：页面接真实接口、异常/空态/加载态齐备、浏览器硬刷新实测通过，三项缺一不可。
