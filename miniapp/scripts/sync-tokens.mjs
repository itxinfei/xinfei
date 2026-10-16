/**
 * 设计令牌同步：把 Web 三端的单一真源 tokens.css 复制到小程序共享包。
 *
 * 为什么要复制而不是直接引源文件：小程序构建只认 miniapp/ 目录内的相对路径，
 * 跨到 src/main/resources 会打散 vite 的依赖边界；复制 + 本脚本 = 机器可校验的同步，
 * 不靠"记得改两处"。
 *
 * 唯一的文本改写：`:root {` → `page, :root {`。
 * WXSS 里自定义属性要挂在 `page` 上才对页面节点生效，浏览器/H5 端仍认 `:root`，
 * 两个选择器并列即可三端通吃。
 *
 * 用法：pnpm sync:tokens   （改了 src/main/resources/shared/styles/tokens.css 之后跑一次）
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = path.resolve(ROOT, '../src/main/resources/shared/styles/tokens.css');
const DEST = path.resolve(ROOT, 'packages/shared/src/styles/tokens.css');

if (!fs.existsSync(SRC)) {
  console.error('找不到源令牌文件：' + SRC);
  process.exit(1);
}

const original = fs.readFileSync(SRC, 'utf8').replace(/\r\n/g, '\n');
const occurrences = original.split(':root {').length - 1;
if (occurrences === 0) {
  console.error('源文件里没有 `:root {` 选择器，口径可能变了，先人工确认再同步');
  process.exit(1);
}

const transformed = original
  .replace(/:root \{/g, 'page, :root {')
  .replace(/^/, '/**\n'
    + ' * 由 miniapp/scripts/sync-tokens.mjs 自动同步，禁止手改。\n'
    + ' * 真源：src/main/resources/shared/styles/tokens.css（改完跑 `pnpm sync:tokens`）。\n'
    + ' * 唯一改写：`:root {` → `page, :root {`（WXSS 需把自定义属性挂在 page 上）。\n'
    + ' */\n');

fs.mkdirSync(path.dirname(DEST), { recursive: true });
fs.writeFileSync(DEST, transformed, 'utf8');
console.log('已同步 ' + occurrences + ' 处 :root 选择器 → ' + path.relative(ROOT, DEST));
