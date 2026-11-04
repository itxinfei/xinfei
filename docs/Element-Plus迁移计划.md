# 后台 Vue 3 + Element Plus 迁移计划与开发规范（AI agent 必读）

**更新日期**：2026-10-22
**适用范围**：仅 `src/main/resources/backend/`（管理后台壳层 + `page/` 内容页）
**事实优先级**：本文档与 `AGENTS.md` / `CLAUDE.md` 冲突时，以 `AGENTS.md` 为准；组件实际行为以 `backend/js/components-v3.js`、`backend/js/app-v3.js` 源码为准。

---

## 0. 当前状态（先读这段，不要按"待迁移"处理）

- 后台**已完成** Vue 2 + Element UI 2 → **Vue 3 + Element Plus** 迁移：壳层 `index.html` + `page/` 下 70 个页面（另 1 个标准模板 `_templates/list-page.html`）统一走 v3 装配，其中 68 个挂侧边栏、2 个由页面内 iframe 打开，`backend/api/*.js` 共 39 个接口封装；后台**不存在待迁移的 Vue2 页面**。
- 旧 Vue 2 / Element UI 2 二进制资产（`vue.js`、`element-ui`、`components.js`、`chart-panel.js`、旧样式链、旧图标字体，共 14 个文件约 1.61 MB）已于 2026-10-22 整体删除，需要时从 git 历史找回，禁止重新引入。
- 本文档用途：
  1. 新写后台页面时必须遵守的 v3 / Element Plus 写法与项目封装契约；
  2. 排查历史遗留时的对照基准；
  3. 记录官方迁移依据与本项目已落地的迁移动作，供后续 AI agent 审计。

### 硬性禁令（违反即返工）

- 禁止把任何后台页面降回 Vue 2 / Element UI 2；禁止 `new Vue({...})`、禁止从 git 历史或任何途径重新引入旧 Vue2 / Element UI2 资产。
- **同一页面禁止同时运行两套组件库**（不允许既挂 Vue2 旧库又挂 v3 新库）。
- 后台不另建独立 Vite / SPA 工程，全部在 `backend/` 目录原地迭代。
- 本规范**不适用于** `front/`、`rider/`（Vue3 + Vant 4）与 `miniapp/`（uni-app），三套体系组件库不互通，不要"顺手统一"。

### 官方依据（权威口径以官方组件文档为准）

| 主题 | 链接 |
| --- | --- |
| Element Plus 迁移指南 | https://element-plus.org/zh-CN/guide/migration |
| Element Plus 组件 API（按组件查属性 / 插槽 / 事件） | https://element-plus.org/zh-CN/component/ |
| Vue 3 非兼容性改变汇总 | https://v3-migration.vuejs.org/zh/breaking-changes/ |
| 官方迁移工具 gogo code（在 Vue Element Admin 上验证；本项目已手工完成，**不需要再用**） | https://github.com/gogocodeio/vue-element-admin |

> 官方迁移指南提示：在 Vue 3 migration build 下部分组件依赖 Vue3 内部 API，需把 `compatConfig` 模式调到 3；本项目是**纯 Vue 3 global build**，不启用 migration build，该条仅作背景备查。

---

## 1. 技术基线与装配机制（无构建静态资源）

后台是 iframe 单页架构、原生 JS、无 Vite / webpack 编译，静态资源由 Spring Boot 直接伺服，改完刷新即生效。

### 1.1 运行时第三方库（本地 `plugins/`，不引 CDN）

| 顺序 | 文件 | 作用 |
| --- | --- | --- |
| 1 | `plugins/vue3/vue.global.js` | Vue 3 global build（页面/壳层统一引用此文件；同目录 `vue.global.prod.js` 为压缩备用件，当前不引用，勿混用） |
| 2 | `plugins/element-plus/index.full.min.js` | Element Plus 组件库 |
| 3 | `plugins/element-plus/icons.global.iife.min.js` | EP SVG 图标全局注册包 |
| 4 | `plugins/element-plus/locale.zh-cn.min.js` | 中文语言包（EP 默认英文，必须由装配层显式传入） |
| 5 | `plugins/axios/axios.min.js` | HTTP 客户端 |
| 按需 | `plugins/echarts/echarts.min.js` | 仅图表页在 `<head>` 引入 |
| 按需 | `plugins/remixicon/remixicon.css` | 业务图标字体（需要 Remix 图标的页面在 `<head>` 引入） |

### 1.2 装配入口 `backend/js/app-v3.js`

页面只使用启动器 `createXinfeiApp(options)`，不要直接调用 `Vue.createApp`。装配顺序（依赖顺序，不可调换）：

1. `Vue.createApp(opts)`；
2. `app.use(ElementPlus, { locale: ElementPlusLocaleZhCn })`；
3. 注册全局格式化 mixin（`window.XinfeiGlobalMixinV3`：`formatMoney` / `formatDate` / `xinfeiStatusText` …）；
4. 注册 EP 图标组件（跳过包内 `install` / `default`）；
5. 注册共享业务组件（`window.XinfeiComponentsV3`）；
6. `app.mount(selector)`。

> 漏挂语言包会导致分页、日期面板、MessageBox 按钮悄悄变成 OK / Cancel / January；漏挂 mixin 会让模板里的 `this.formatMoney` 变成 `undefined` 静默报错。两者都由 `app-v3.js` 统一完成，页面不要重复注册。

### 1.3 共享业务组件 `backend/js/components-v3.js`

注册表 `window.XinfeiComponentsV3`，由装配层统一 `app.component()` 注册。`js/components-v3.js` 注册的现有组件：

- `<stat-cards>` 统计卡片组；
- `<table-bar>` 搜索筛选 + 操作按钮栏；
- `<crud-table>` 完整 CRUD 数据表格（分页、多选、行操作、内置查看弹窗）；
- `<crud-dialog>` 新增 / 编辑弹窗壳；
- `<rg-image-uploader>` 图片上传。

报表组件由 `js/chart-panel-v3.js` **单独**写入同一注册表（页面按需引入该脚本后生效）：`<chart-panel>` 图表面板、`<kpi-card>` KPI 指标卡片、`<page-shell>` 报表页外壳；三者 `icon` prop 收 Remix 类名字符串（见第 3 节图标条目边界说明）。其余独立 JS 组件（如菜品配方弹窗 `js/food-bom-dialog.js`）由所在页面在 `components` 选项内本地注册，不进全局注册表。

新增后台共享组件在上述注册表内定义，不要再另起 Vue 2 双轨实现。

### 1.4 样式链 `backend/styles/main-v3.css`

页面只引这一条样式入口（不要再单独 `<link>` `tokens.css`，避免出现两份顺序）。`@import` 顺序即契约：

1. `plugins/element-plus/index.css`（EP 基座，必须最先，其 `:root` 变量由后面令牌覆盖）；
2. `tokens.css`（设计令牌单源，品牌金 / 语义色的 `--el-*` 覆盖令牌在此定义，替换 EP 默认蓝）；
3. `element-theme.css`（只补 tokens 没有对应物的 EP 变量，不得重复声明同名者）；
4. `design-system.css`；
5. `common-base.css` → `unified-dialog.css` → `common-fixes.css`；
6. `page.css` → `components.css` → `unified-components.css` → `form-fixes.css` → `components-stats-card.css`；
7. `consistency.css`（全局一致性层，最后覆盖历史漂移）；
8. `main-core.css`（键盘焦点环）；
9. `element-plus-adapt.css`（EP DOM 适配层，必须最后加载）。

颜色一律引用设计令牌 `var(--token, #回退)`，禁止硬编码 hex；canvas / ECharts 场景无法使用 `var()`，走 `components-v3.js` 暴露的 `window.XinfeiPalette`（运行时读令牌，hex 仅为回退值）。合规例外口径见 `AGENTS.md` 前端段。

---

## 2. Vue 2 → Vue 3 对照表（本项目场景：Options API + global build 静态页）

| 主题 | Vue 2 写法 | Vue 3 / 本项目写法 | 备注 |
| --- | --- | --- | --- |
| 实例化 | `new Vue({ el, ... })` | `createXinfeiApp({ el, ... })` | `el` 由装配层取出并在装配完成后 mount |
| 全局注册 | `Vue.component(...)` / `Vue.mixin(...)` / `Vue.use(...)` | `app.component(...)` / `app.mixin(...)` / `app.use(...)` | 注册动作集中在 `app-v3.js`，页面不写全局注册 |
| 组件双向绑定 | `.sync` 修饰符（`:visible.sync`、`:title.sync`） | `v-model:propName`；组件 `v-model` 默认绑定 `modelValue` + `update:modelValue` | 见第 3 节弹窗条目 |
| 具名插槽 | `slot="name"` + `slot-scope="row"` / `scope="row"` | `<template #name>` / `#default="props"`；`v-slot:name` | 作用域插槽对象从 `props.row`、`props.$index` 取 |
| 插槽对象 | `$scopedSlots` | 移除，统一 `$slots`（函数形式） | 判断是否有插槽：`!!this.$slots.header` |
| 事件监听 | `$listeners` | 移除，合并进 `$attrs`；`$attrs` 现在也包含 `class`、`style` | — |
| 子组件引用 | `this.$children` | 移除；配对组件需要时改走 DOM 顺序查找 | `components-v3.js` 内已按此处理 |
| 生命周期 | `beforeDestroy` / `destroyed` | `beforeUnmount` / `unmounted` | 事件监听前缀 `hook:` 改为 `vue:` |
| 事件声明 | 无 | 组件用 `emits` 选项声明事件 | `crud-dialog` 已声明 `update:modelValue` / `close` / `submit` |
| `data` | 对象字面量也能跑 | 必须是函数（`data() { return {...} }`） | mixin 的 data 现在为浅合并 |
| `<template v-for>` 的 `key` | 可放子元素上 | 必须放在 `<template>` 标签上 | 壳层面包屑已按此修正 |
| 同元素 `v-if` + `v-for` | `v-for` 优先 | `v-if` 优先（不能在 `v-if` 里访问 `v-for` 的 item） | 两者不要写在同一元素上 |
| 渲染函数中的 `h` | `this.$createElement` | `import { h } from 'vue'` 风格；本项目共享组件以模板为主 | — |

### 已移除、禁止再出现的 Vue 2 API

- 实例方法：`$set` / `$delete` / `$on` / `$off` / `$once` / `$destroy`；全局 `Vue.set` / `Vue.delete`（Vue3 Proxy 响应式不再需要）。
- 模板过滤器 `{{ value | filterName }}`（用方法或计算属性替代）。
- `inline-template` 属性；`propsData` 选项；`v-on:keyCode` 修饰符；事件 `.native` 修饰符。
- 没有任何指令（`v-if/v-for/v-slot`）的 `<template>` 会被当作原生 `<template>` 元素渲染，不再拆出内部内容。

---

## 3. Element UI 2 → Element Plus 对照表（强制，勿回退）

| 主题 | Element UI 2 | Element Plus / 本项目规则 | 出处 |
| --- | --- | --- | --- |
| 弹窗可见态 | `:visible.sync="dialogVisible"` | `v-model="dialogVisible"`（`modelValue` + `update:modelValue`） | EP Dialog 属性 `model-value / v-model` |
| 弹窗自定义类名 | `custom-class="..."` | 直接写 `class="..."`；EP 已弃用 `custom-class`（将在 2.4.0 移除），且 class 直落弹窗盒子 `.el-dialog`，不再合并到遮罩层 | EP Dialog 警告说明 |
| 弹窗挂载位置 | `modal-append-to-body` | 已删除；EP 经 Teleport 默认挂载到 body | EP 属性清单 |
| 弹窗标题 | `title` prop（EP 中已弃用，3.0.0 移除） | `#header` 插槽；本项目统一走 `<crud-dialog>`，页面不写裸 `<el-dialog>` | EP Dialog 警告说明 |
| 日期面板快捷键 | `picker-options` | `:shortcuts`；`value` 是返回日期的函数，不再 `picker.$emit('pick')` | `components-v3.js` 文件头实测 |
| 输入框前后缀图标 | `slot="prefix"` / `slot="suffix"` | `<template #prefix>` / `<template #suffix>`；`prefix-icon` / `suffix-icon` 只收 SVG 图标组件，不收 Remix 字体类名（详见下条图标体系） | EP Input 文档 |
| 图标体系 | `el-icon-xxx` 字体类名 + `element-icons` 字体 | 业务图标统一 RemixIcon `ri-xxx`。**EP 原生组件** `el-button` / `el-dropdown-item` 的 `icon` prop、`el-input` 的 `prefix-icon` / `suffix-icon` 只收**组件**（`<component :is="icon">` 解析），传 `"ri-xxx"` 字符串会解析为空、图标不显示；必须走插槽：按钮 `<template #icon><i class="ri-xxx"></i></template>`、下拉项 `<template #icon><i class="ri-xxx"></i></template>`、输入框 `<template #prefix><i class="ri-xxx"></i></template>`（EP v2.14.7 已支持 dropdown-item `#icon` 插槽）。**项目自定义组件** `chart-panel` / `kpi-card` 的 `icon` prop、`stat-cards` / `table-bar` 配置项的 `icon` 字符串类名是既定约定（内部渲染 `<i class>` 或经 `RI_ICON_CLASS` 映射），**合法，不要误改**。`element-icons` 字体已归档。 | EP Button / Dropdown / Input 文档；`chart-panel-v3.js` `isIconClass`；`components-v3.js` `RI_ICON_CLASS` |
| 文字按钮 | `<el-button type="text">` | `<el-button text>` | `components-v3.js` 文件头实测 |
| 子菜单组件 | `<el-submenu>` / CSS `.el-submenu__title`、`.el-submenu__icon-arrow`、`.el-submenu.is-opened` | `<el-sub-menu>` / CSS `.el-sub-menu__title`、`.el-sub-menu__icon-arrow`、`.el-sub-menu.is-opened` | 壳层 `index.html` 与 `styles/index.css` 已适配 |
| 上传文件列表 | `v-model`（UI2 的 `value` / `input` 语义） | `modelValue` / `update:modelValue`；文件列表走 `v-model:file-list` | `components-v3.js` 文件头实测 |
| 作用域插槽 | `scope="row"` / `slot-scope="row"` | `#default="props"` | 见第 2 节插槽条目 |
| Teleport 组件样式 | UI2 内联样式全局可覆盖 | Dialog / Tooltip / Popper 等经 Teleport 渲染，`scoped` 样式不生效；本项目静态页本就是全局样式链，特殊覆盖写全局样式层 | EP Dialog FAQ |

> 注：`.el-menu-item`、`.el-menu--inline`、`.el-menu--collapse`、`.el-scrollbar`、`.el-loading-mask` 等类名在 EP 保持同名，**不要**误改。

---

## 4. 项目封装契约（新页面必须照用，事实以 `components-v3.js` 源码为准）

### 4.1 `<crud-dialog>` —— 禁止裸写 `<el-dialog>`

- 可见态：`v-model="visible"`。
- 尺寸：`size="sm|md|lg|xl|fullscreen"`，或 `width="600px"` 直接指定；**`width` 优先于 `size`**。尺寸档单一事实源 `DIALOG_SIZE`：

  | size | 宽度 |
  | --- | --- |
  | `sm` | 420px |
  | `md` | 560px |
  | `lg` | 720px |
  | `xl` | 840px |
  | `fullscreen` | 92% |

  禁止在页面写 `el-dialog--xl` 等魔数 class；不要在 `DIALOG_SIZE` 之外另造尺寸档。
- Props：

  | Prop | 类型 | 默认 | 说明 |
  | --- | --- | --- | --- |
  | `title` | String | `新增` | 弹窗标题；有 `#header` 插槽时不传 title |
  | `modelValue` | Boolean | `false` | 可见态，页面写 `v-model` |
  | `size` | String | `md` | 尺寸别名，见上表 |
  | `width` | String | `''` | 任意像素宽度，优先级高于 `size` |
  | `closeOnClickModal` | Boolean | `false` | 点击遮罩是否关闭 |
  | `submitLoading` | Boolean | `false` | 确认按钮 loading |
  | `submitText` | String | `确 定` | 确认按钮文字 |
  | `cancelText` | String | `取 消` | 取消按钮文字 |
  | `showSubmit` | Boolean | `true` | 是否显示确认按钮 |
  | `showCancel` | Boolean | `true` | 是否显示取消按钮 |
  | `customClass` | String | `''` | 组件**自定义** prop（模板写 `custom-class="…"`），追加在 `unified-dialog` 之后做特殊覆盖；**不是** EP 原生 `custom-class` |
  | `beforeClose` | Function | `null` | 关闭前守卫：返回 `false` 中止；返回 Promise 时 `resolve(false)` 中止、其余放行。X 按钮、ESC、取消按钮均走此守卫；遮罩点击仍由 `closeOnClickModal` 控制。示例：`:before-close="() => !formDirty"` |

- 事件：`update:modelValue`、`close`、`submit`。
- 表单校验用 EP 内置 `:rules` + `validate()`，错误 inline 显示在字段下方；禁止仅靠 `this.$message` toast 提示字段错误。交互反馈统一调 `window.XinfeiUI`（见 `js/request.js`）。
- 确需裸写 `<el-dialog>` 属于例外情况，须先经批准，且仍须遵守上面的尺寸与 class 规则。

### 4.2 `<crud-table>` / `<table-bar>` / `<stat-cards>`

**列对齐**：页面显式 `col.align` 优先；未设置一律 `center`（金额、数字列也居中）；`header-align` 跟随数据列。不要在页面翻转这个默认居中规则。

**列宽**：`col.minWidth` > `col.width`（降级为下限）> 未设置由 EP 按内容自动分配。`width` 是最小宽度下限，不要把各列 `width` 写成固定总和导致横向溢出。

**CSS 选择器**：表格根元素 `.tableBox` 与 `<el-table>` 是同一元素。覆盖表格样式必须使用复合选择器 `.tableBox.el-table …`：单独 `.el-table` 会污染其他 EP 表格，单独 `.tableBox` 权重不足。

**行操作列**：常显主操作 ≤3 个，超出收进"更多"`el-dropdown`；宽度档：2 元素 120px、3 元素 180px、4 元素 240px（更多触发器算 1 个元素）。语义类：查看 `btn-view`、启用 `btn-success`、停用/暂停 `btn-warning`、删除/取消 `btn-delete`。

**多选跨翻页保留**：需要保留勾选时，页面必须显式传 `row-key`；未传维持单页行为。

**`<stat-cards>` 统计卡片视觉（`styles/components-stats-card.css`，全站统一，勿在页面内联覆盖）**：
- 默认态：`1px solid var(--border-subtle)` 中性细边框、`background: var(--bg-surface)`、`box-shadow: var(--shadow-sm)`；**禁止顶部 / 侧边主题色条**——旧实现 `.stat-card::before` 的 3px 顶部色条已删除，新页面 / 新卡片不得再用 `::before`、`border-top`、`border-left` 加语义色条。
- hover 态：边框切 `1px solid var(--el-color-primary)`（主色金 `#c99200`），同时保留全站统一抬升 `translateY(-2px)` + `box-shadow: var(--shadow-md)`；不要在页面单独写 `.stat-card:hover` 覆盖抬升档位。
- 数值 / 图标语义色仍由 `--stat-color`（primary / success / warning / danger / info 及别名 blue / green / orange / purple）按指标语义着色，允许保留；`stat-card--flex` 桌台变体图标淡色圆角底是既定约定，保持原样。
- 改动该样式表后必须同步刷新缓存号：`main-v3.css` 内 `@import` 子文件 query、引用该表的页面独立 `<link>` 的 `?v=`，并把 `main-v3.css` 自身版本号递增一档，避免旧 CSS 层叠残留旧色条。
- 类名隔离：旧私有手写卡片 `.stats-row`、`.stats-card`（及 `-icon` / `-content` / `-value` / `-label`）规则已整段移除，`backend/page/**` 不再渲染；新页面一律用 `<stat-cards>` 组件 + 容器 `.stats-cards` / 卡片 `.stat-card`，禁止在 `unified-components.css`、`consistency.css` 或页面内联样式里复活旧类名。

**输入框边框归属（Element Plus 结构，禁止复活 inner 边框，2026-10-22 定稿）**：

- **结构与边框载体**：单行 `<el-input>` DOM 为 `.el-input > .el-input__wrapper > .el-input__inner`。边框、圆角、背景、hover / focus 反馈**只由 `.el-input__wrapper`** 用 `inset box-shadow` 承担；`.el-input__inner` 保持透明背景、`border: none`、`border-radius: 0`，只保留高度、行高、内边距、字号、文字色与过渡。这是 Vue3 + EP 与旧 Vue2 + Element UI 的关键结构差异：旧 UI2 把边框压在 inner 上，EP 已上移到 wrapper，再给 inner 写 `border` 就会叠成双层框。
- **三态颜色变量（EP 元素级声明，勿在 `:root` 重复覆盖）**：EP 在 `.el-input` / `.el-date-editor` / `.el-textarea` 元素级直接声明下列变量，元素自身声明优先于 `:root` 继承值，只改 `:root` 不生效：

  | 状态 | EP 变量 | 绑定 | 本项目取值 |
  | --- | --- | --- | --- |
  | 默认 | `--el-input-border-color` | `var(--el-border-color)` | `#dcdfe6` 中性灰 |
  | hover | `--el-input-hover-border-color` | `var(--el-border-color-hover)` | `#ffcc33` 浅金（`--color-brand-400`） |
  | focus | `--el-input-focus-border-color` | `var(--el-color-primary)` | `#c99200` 主色金 |

  hover 色必须在 `element-theme.css` 映射**底层** `--el-border-color-hover: var(--color-brand-400)`；默认色随 `--el-border-color`、聚焦色随 `--el-color-primary`，无需单独覆盖。
- **各控件边框载体（不要套错选择器）**：
  - `el-select`（EP 新结构）走独立 `.el-select__wrapper`，内部**不含** `.el-input__inner`，边框由该 wrapper 承担；
  - 日期范围选择器根元素自身即 `.el-date-editor.el-input__wrapper.el-range-editor`，内部是 `.el-range-input`（不是 `.el-input__inner`），边框在根 wrapper；
  - `el-input-number` 内部仍是 `.el-input__wrapper` + `.el-input__inner`，规则与单行一致；
  - 多行 `el-input type="textarea"` 渲染 `.el-textarea > .el-textarea__inner`，**没有 wrapper**，inner 自身就是边框载体，边框 / 背景 / hover / focus 规则单源在 `form-fixes.css`，不得删除其 `border` 与 `box-shadow`。
- **禁令**：禁止在 `.el-input__inner` 上复活 `border`、实心背景、圆角；禁止在页面内联样式、`design-system.css`、`unified-components.css`、`consistency.css` 重复定义 wrapper 三态边框（单源为 EP 基座 + `element-theme.css` 变量映射 + `form-fixes.css` 布局/文字层）；新页面直接用 `<el-input>`，不要手写任何边框。
- 改动上述输入框样式表后，按 4.3 节口径刷新 `main-v3.css` 内 `@import` 的 `?v=` 与全站页面 `main-v3.css?v=` 缓存号。

### 4.3 标准页面骨架（照抄 `page/_templates/list-page.html`）

`<head>` 样式（相对路径 `../../`）：

```html
<link rel="stylesheet" href="../../styles/main-v3.css?v=3001003" />
<!-- 需要业务图标时 -->
<link rel="stylesheet" href="../../plugins/remixicon/remixicon.css" />
<!-- 报表页按需：图表面板样式 -->
<link rel="stylesheet" href="../../styles/chart-panel.css?v=20260926" />
<!-- 需要图表时 -->
<script src="../../plugins/echarts/echarts.min.js"></script>
```

`<body>` 末尾脚本（顺序不可调换）：

```html
<script src="../../plugins/vue3/vue.global.js"></script>
<script src="../../plugins/element-plus/index.full.min.js"></script>
<script src="../../plugins/element-plus/icons.global.iife.min.js"></script>
<script src="../../plugins/element-plus/locale.zh-cn.min.js"></script>
<script src="../../plugins/axios/axios.min.js"></script>
<script src="../../js/request.js?v=20260930"></script>
<script src="../../js/accessibility-utils.js?v=20260927"></script>
<script src="../../js/performance-utils.js?v=20260926"></script>
<!-- 按需：业务 API 封装 -->
<script src="../../api/模块名.js?v=20260926"></script>
<script src="../../js/components-v3.js?v=3001001"></script>
<script src="../../js/app-v3.js?v=3001001"></script>
<!-- 按需：报表组件（chart-panel / kpi-card / page-shell），在 app-v3.js 之后引入 -->
<script src="../../js/chart-panel-v3.js?v=3001001"></script>
<!-- 按需：页面本地注册的独立组件（如 food-bom-dialog.js），引入后在 components 选项注册 -->
<script src="../../js/模块-dialog.js?v=20261022"></script>
<script>
  // el 与页面根元素 id 一致；模板根 id 为 template-app，新页面可自定义 id 但须与此处匹配
  createXinfeiApp({ el: '#template-app', /* data / methods / mounted ... */ })
</script>
```

- 接口统一走 `backend/api/{模块}.js` → `js/request.js`（Axios 封装，含 CSRF 注入与 `window.XinfeiUI` 反馈）。
- 新页面优先复制 `page/_templates/list-page.html`，不要从旧业务页复制历史遗留代码。
- 独立组件脚本（如 `js/food-bom-dialog.js`）挂到 `window.组件名`，由页面 `components: { 'food-bom-dialog': window.FoodBomDialog }` 本地注册，不进全局注册表。

---

## 5. 归档、排查与验收

### 5.1 旧资产归档

- 旧归档目录 `backend/_legacy-vue2/`（14 个旧资产 + `README.md`：清单、体积、v3 替代件映射）已于 2026-10-22 整体删除，删除前内容可从 git 历史查看。
- 禁止任何页面的 `<script>` / `<link>` 重新引入旧资产；确需找回单个文件时从 git 历史单独检出，不得整目录复活。
- 旧 `front/styles/main.css` 是用户端 Vant 体系的独立活跃文件，与已归档的 `backend/styles/main.css` 同名但**不是**旧链，禁止误删。

### 5.2 常见报错排查

| 现象 | 排查方向 |
| --- | --- |
| `createXinfeiApp 需要 Vue 3 全局构建` | `plugins/vue3/vue.global.js` 未先于 `app-v3.js` 加载 |
| 分页 / 日期面板 / MessageBox 变英文 | `locale.zh-cn.min.js` 链缺失，或装配层 `app.use(ElementPlus, { locale })` 未传 |
| `el-*` 组件不渲染 | 漏引 `element-plus/index.full.min.js` |
| EP 图标不显示 | 检查 `icons.global.iife.min.js` 链；或改用 `#icon` 插槽内 Remix 类名 |
| `this.formatMoney` 为 `undefined` | 全局 mixin 未注册（由 `app-v3.js` 统一完成，页面不要跳过装配层） |
| 弹窗靠左 / 不居中 | 检查是否误用 `custom-class`；统一改用 `crud-dialog`，自定义类走 `custom-class` prop |

### 5.3 验收口径

- 前端修改后必须在浏览器渲染验证（`Ctrl+Shift+R` 硬刷新清 CSS 缓存），**未实测不得视为完成**。
- 禁止遗留无作用的死 class / 死 `<link>` / 死 `<script>`；复制代码前先理解，不照搬。
- 后台自测要求：控制台 0 error、0 warning；网络面板无 4xx / 5xx。

---

## 6. 待办与遗留（非阻塞，归属另列）

### 6.1 窄屏抽屉补测（任务 #7）

≤767px 移动端视口下汉堡按钮唤起抽屉、遮罩点击关闭、菜单点击后收起；当前自动化环境 CDP 设备模拟不可用，待下次浏览器环境验证。CSS 规则已在 `styles/index.css`，本轮未改动。

### 6.1b 弹窗内数字框 / 文本域边框真机补测（任务 #12 遗留）

任务 #12 移除单行 `.el-input__inner` 内层边框后，下列两类控件默认 DOM 经 `v-if` 懒挂载、页面首屏不渲染，本轮只完成静态规则核对，未做弹窗内真机回归，待下次打开对应弹窗补测：

- **`el-input-number`**：`page/dashboard/overview.html` 目标表单弹窗（`targetForm.daily` / `targetForm.monthly`）、`page/ai/provider-config.html` 表单弹窗（`embeddingDimensions` / `timeout` / `maxTokens`）。静态依据：内部仍是 `.el-input__wrapper` + `.el-input__inner`，`form-fixes.css` 的 `.el-input-number .el-input__inner` 仅设居中 / 内边距 / 高度 / 垂直对齐，无 `border` / 背景 / 圆角，边框由 wrapper 承担。补测要点：弹窗打开后数字框单层灰框、无内层叠加边框、聚焦切主色金。
- **`el-input type="textarea"`**：`page/customer-service/list.html` 回复弹窗、`page/food/list.html` / `page/combo/list.html` 菜品与套餐描述弹窗等。静态依据：`.el-textarea__inner` 边框 / 背景 / hover / focus 规则单源保留在 `form-fixes.css` 85–105 行，本轮未删除。补测要点：多行域边框保留、hover 浅金、focus 主色金 + 浅金晕，不出现双层框。

### 6.2 上一轮页面迁移遗留规范层残留批量整改（视觉 / 规范层，功能以真机实测为准）

> 以下两类均为上一轮页面迁移时漏改的 Vue2 / EP2 旧写法；独立 JS 组件 `food-bom-dialog.js` 的功能级损坏已在本轮修复（见附录 A）。本批残留需另起一轮逐页真机回归后整改，不要在未实测时批量提交。

#### A. EP 原生组件 `icon="ri-..."` 字符串残留（71 处 / 25 个页面，2026-10-22 实测）

- **现象**：`el-button` / `el-dropdown-item` 的 `icon`、`el-input` 的 `prefix-icon` / `suffix-icon` 仍传 `"ri-xxx"` 字符串。EP 内部 `<component :is="icon">` 只解析已注册组件，Remix 字体类名解析为空 → **图标不显示**，按钮 / 菜单项 / 输入框文字与功能正常，属视觉降级。
- **整改写法**（错误 → 正确，与第 3 节图标条目一致）：

  | 组件 | 错误写法（禁用） | 正确写法 |
  | --- | --- | --- |
  | `el-button` | `<el-button icon="ri-add-line">新增</el-button>` | `<el-button><template #icon><i class="ri-add-line"></i></template>新增</el-button>` |
  | `el-dropdown-item` | `<el-dropdown-item icon="ri-edit-line">编辑</el-dropdown-item>` | `<el-dropdown-item><template #icon><i class="ri-edit-line"></i></template>编辑</el-dropdown-item>` |
  | `el-input` | `<el-input prefix-icon="ri-search-line" />` | `<el-input><template #prefix><i class="ri-search-line"></i></template></el-input>`（`suffix-icon` 对应 `#suffix`） |

- **不要误改**：`<chart-panel>`、`<kpi-card>` 的 `icon="ri-..."`、`stat-cards` / `table-bar` 配置项 `icon` 是项目自定义约定，内部渲染 `<i class>` 或经 `RI_ICON_CLASS` 映射，保持原样。
- **本轮已修**：`page/_templates/list-page.html` 下拉项图标（新页面规范源头）。
- **剩余范围（71 处 / 25 个页面，相对 `backend/`）**：

  | 文件 | 处数 |
  | --- | --- |
  | `page/export/index.html` | 10 |
  | `page/store/list.html` | 9 |
  | `page/food/spec-management.html` | 8 |
  | `page/member-center/member-list.html` | 5 |
  | `page/dining/table-list.html` | 5 |
  | `page/food/list.html` | 4 |
  | `page/recommend/campaigns.html` | 3 |
  | `page/employee/list.html` | 3 |
  | `page/delivery/range-management.html` | 3 |
  | `page/platform/order-list.html` | 2 |
  | `page/report/evaluation-list.html` | 2 |
  | `page/report/daily.html` | 2 |
  | `page/attendance/attendance.html` | 2 |
  | `page/sys/role-list.html` | 2 |
  | `page/ai/provider-config.html` | 1 |
  | `page/inventory/material-list.html` | 1 |
  | `page/inventory/purchase-list.html` | 1 |
  | `page/inventory/stock-check.html` | 1 |
  | `page/payment/channel-config.html` | 1 |
  | `page/platform/config.html` | 1 |
  | `page/region/list.html` | 1 |
  | `page/sys/config-list.html` | 1 |
  | `page/sys/department-list.html` | 1 |
  | `page/sys/template-list.html` | 1 |
  | `page/user/list.html` | 1 |

#### B. `.native` 事件修饰符残留（17 处 / 11 个页面，2026-10-22 实测）

- **现象**：`@keyup.enter.native="..."` 等写法仍在使用。`.native` 在 Vue 3 已移除：组件未通过 `emits` 声明的事件会作为原生事件 fallthrough 到组件根元素，不再需要 `.native`；残留修饰符应直接删除。`el-input` 回车事件经内部原生 input 冒泡到根元素，删除 `.native` 后行为以真机实测为准。
- **整改写法**：`@keyup.enter.native="onSearch"` → `@keyup.enter="onSearch"`（其余事件修饰符、方法名保持不变）。
- **剩余范围（17 处 / 11 个页面，相对 `backend/`）**：

  | 文件 | 处数 |
  | --- | --- |
  | `page/notification/list.html` | 4 |
  | `page/food/list.html` | 3 |
  | `page/login/login.html` | 2 |
  | `page/cashier/index.html` | 1 |
  | `page/customer-service/list.html` | 1 |
  | `page/delivery/rider-list.html` | 1 |
  | `page/dining/queue-list.html` | 1 |
  | `page/member-center/coupon-list.html` | 1 |
  | `page/member-center/points-list.html` | 1 |
  | `page/member-center/recharge-list.html` | 1 |
  | `page/printer/task-list.html` | 1 |

  > `page/food/list.html` 同时出现在 A、B 两组，按组分别计数。

#### 复扫与验收

- **复扫命令**（PowerShell，在 `backend/` 下对 `page/**/*.html`，排除 `_templates`、HTML 注释行）：图标类匹配 `icon="ri-`、`prefix-icon="ri-`、`suffix-icon="ri-` 并剔除 `chart-panel` / `kpi-card` 所在行；`.native` 类匹配 `.native`。两类各自整改到 0 命中为止。
- **验收**：逐页浏览器硬刷新（`Ctrl+Shift+R`）实测图标可见、位置间距正常，回车 / 提交等事件触发正常；控制台 0 error / 0 warning；不得改动 `chart-panel` / `kpi-card` / `table-bar` / `stat-cards` 的字符串图标。

### 6.3 其他归属另列

- `front/`、`rider/` 个别旧版页面的 Vant 4 收尾与本后台 EP 迁移无关，归属用户端迁移清单。
- 小程序 token 会话、验证码 Redis、openid 登录、微信支付 JSAPI 等前置改造见 `miniapp/README.md` 第 5 节。

### 6.4 文档口径遗留（2026-11-04 收口）

- **根目录 `README.md`**：徽章与正文的技术栈、规模已按实测重刷为 JDK 25 / Boot 4.1.1 / HikariCP 7.0.2 / Vue 3.5.44 / Element Plus 2.14.7 / Vant 4.10.2 / 41 模块 / 136 张表 / 1160 个 `@Test` / 70 个后台页面；截图沿用仓库内既有的 `docs/screenshots/*.png`，未新拍。
- **`docs/后台页面清单.md`**：第 1~6 节已按 70 个页面、68 个菜单挂载、21 个自研 css、`components-v3.js` 等现状改写；第 7~9 节的设计方案与提示词留档保留旧名并标注为历史记录。
- **`docs/竞品与市场分析_竞析_2026.md`**：顶部已加现行栈口径注记；76 / 147 行对比表与结论中的 Vue2 表述为历史分析快照，保留不改。
- **`docs/FLYWAY_MIGRATION.md`**：第 10 行依赖版本已按 Boot 4.1.1 / Flyway 12.4.0 / `flyway-mysql` 显式依赖订正，旧 7.1.1 口径仅作历史背景保留。

---

## 附录 A：2026-10-22 壳层迁移已落地的关键动作（供审计对照）

- 组件标签：`<el-submenu>` → `<el-sub-menu>`；`<template slot="title">` → `<template #title>`。
- 启动器：`new Vue({ el: '#app', ... })` → `createXinfeiApp({ el: '#app', ... })`。
- 生命周期：`beforeDestroy` → `beforeUnmount`；`notLoginJumping` 状态补进 `data` 声明。
- 菜单键盘导航选择器：`.el-submenu__title` → `.el-sub-menu__title`。
- 面包屑 `<template v-for>` 的 `:key` 移到 `<template>` 标签上（Vue3 要求）。
- 样式：`styles/index.css` 中 EP2 子菜单类名批量改为 EP 类名；EP 保持同名的 `.el-menu-item`、`.el-menu--inline`、`.el-menu--collapse`、`.el-scrollbar`、`.el-loading-mask` 未动。
- 残留死链：`page/ai/assistant.html`、`page/printer/qrcode-center.html` 中两行 element-ui 死 CSS link 已删除。
- 后端配套：废弃 `admin-spa/` 独立工程（目录、`static/admin-spa/` 产物、`WebMvcConfig` 资源映射、`AuthConstants` 匿名放行、`.gitignore` 条目）全部清理，`GET /admin-spa/index.html` 返回 404。
- 收口扫描补迁（独立 JS 组件，页面级扫描未覆盖）：`js/food-bom-dialog.js`（被 `page/food/list.html` 引用）`:visible.sync` → `v-model`、4 处 `<template slot-scope="scope">` → `<template #default="scope">`、`<el-button type="text" size="small">` → `text`、`icon="ri-add-line"` → `<template #icon><i class="ri-add-line"></i></template>`，并补 `emits: ['saved']`。
- 标准模板 `page/_templates/list-page.html` 下拉项 `icon="ri-delete-bin-line"` → `#icon` 插槽（规范源头，避免新页面照抄旧写法）；其余业务页 EP 原生组件图标字符串残留登记在第 6.2 节待批量整改。
- 统计卡片视觉收口（2026-10-22）：`styles/components-stats-card.css` 删除 `.stat-card::before` 顶部 3px 主题色条；`.stat-card:hover` 增加 `border-color: var(--el-color-primary)`（1px 主色金 `#c99200`），抬升与阴影档位保持全站统一；数值 / 图标语义色不动。缓存号同步：`main-v3.css` 的 `@import` 带 `?v=20261022`，15 个独立引用该表的页面 `<link>` 升到 `?v=20261022`，`main-v3.css` 版本号 `3001001`（含 `3001001a` 变体）全站 82 个页面升到 `3001002`。真机实测（8082 端口、`admin/123456` 数据概览）：默认态四张卡片 `::before` content=none、边框 `rgb(243,244,246)` 中性细边；悬停目标卡片边框 `rgb(201,146,0)` 主色、`solid`、宽度不变；控制台 0 error / 0 warning。
- 旧私有统计卡片死样式清理（2026-10-22，承接上一条视觉收口）：`styles/consistency.css` 整块"手写统计卡片统一"增强规则（`.stats-row .stats-card` / `.stats-cards .stats-card` 边框与 hover 抬升）整段移除；`styles/unified-components.css` 中全部 `.stats-row`、`.stats-card`、`.stats-card-icon`、`.stats-card-content`、`.stats-card-value`、`.stats-card-label` 规则（主规则块 + 4 组响应式 media query 内选择器）整段移除。同文件并列存活的 `.chart-row`、`.chart-card`、`.toolbar`、`.toolbar-spacer`、`.od-grid`、`.od-items`、`.dashboard-container`、`.chart-container`、`.status-tag` 规则全部保留。现行 `<stat-cards>` 组件容器 `.stats-cards` 与卡片 `.stat-card` 规则只存在于 `styles/components-stats-card.css`，不得在 `unified-components.css` / `consistency.css` 重新定义。真机回归（8082、`admin/123456`）：`dashboard/overview` 4 张 `.stat-card` 默认中性细边、`::before` none；`report/sales-report` `.chart-row` 网格与窄屏单栏响应式正常；`cost/overview` 卡片与 `.chart-container` 共存；`finance/withdrawal` 无旧类残留；四页控制台 0 error / 0 warning。
- 单行输入框内层残留边框移除（2026-10-22，任务 #12）：现象为平台订单列表搜索框（`page/platform/order-list.html` table-bar `platformOrderId`，placeholder「模糊匹配」）外层 EP wrapper 圆角框之外，`.el-input__inner` 上还叠了一道旧 Vue2 Element UI 时代的实线边框，形成双层框。根因与改动：①`styles/form-fixes.css` 单行 `.el-input__inner` 保留 `height / line-height / padding / font-size / color / transition`，改为 `background: transparent; border: none; border-radius: 0`，并删除 inner 上旧的 `:hover`、`:focus` 边框与金色 box-shadow 规则；`.el-textarea__inner` 边框 / 背景 / hover / focus 规则完整保留（textarea 自身是边框载体）。②`styles/design-system.css`、`styles/unified-components.css` 中原先把 `.el-input__inner` 与 `.el-textarea__inner` 合并书写的重复边框块整段删除，替换为边框归属注释，边框定义单源收归 `form-fixes.css`。③变量根因：EP 在 `.el-input / .el-date-editor / .el-textarea` 元素级把 `--el-input-hover-border-color` 绑到 `--el-border-color-hover`，该元素级声明优先于 `:root` 继承值，最初在 `:root` 覆盖 hover 变量不生效；最终在 `styles/element-theme.css` 只映射底层 `--el-border-color-hover: var(--color-brand-400)`，默认色随 `--el-border-color`、聚焦色随 `--el-color-primary`，不再重复覆盖。缓存号：`main-v3.css` 内 `element-theme.css / design-system.css / unified-components.css / form-fixes.css` 四个 `@import` 升到 `?v=20261022b`；全站 82 个 HTML（含壳层 `index.html`）的 `main-v3.css` 从 `3001002` 升到 `3001003`，旧版 0 残留。真机实测（8082、`admin/123456`）：平台订单列表单行 input 默认 wrapper `rgb(220,223,230)` 1px inset 灰框、inner `none 0px` + 透明背景；点击聚焦后 wrapper 加 `is-focus`、阴影 `rgb(201,146,0)` 主色金 `#c99200`；hover 变量经底层 `--el-border-color-hover` 解析为 `#ffcc33` 浅金（类规则同款层叠实测 `rgb(255,204,51)` 1px inset）；同页两个 `el-select` 走独立 `.el-select__wrapper` 灰框、不含 inner；`order/list.html` 单行 input 正常、日期范围选择器根 wrapper 灰框、内部 `.el-range-input` 不受影响；`customer-service/list.html` 单行 input 正常；三页控制台均 0 error。静态扫描：`.el-input__inner` 实线边框规则 0 残留，`.el-textarea__inner` 边框仅 `form-fixes.css` 单源；`mvn compile -DskipTests` 通过，target 副本与源码六个改动文件 MD5 一致。
