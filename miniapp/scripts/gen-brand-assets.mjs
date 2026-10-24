/**
 * 顾客端品牌位图资产生成器（零依赖，Node 内置 zlib 手写 PNG 编码）。
 *
 * 为什么不用图片文件提交：微信原生 tabBar 的 iconPath 只认包内 PNG（网络图 / base64 /
 * 字体图标都不支持），而仓库里没有设计源文件；用本脚本按设计令牌的色值与统一线宽
 * 程序化生成，线性图标跨分辨率锐利、色值单一真源、随时可重新生成。
 *
 * 产物（apps/customer/src/static/）：
 *   tabbar/{home,home-active,order,order-active,me,me-active}.png  81×81
 *   brand/logo.png                                                 144×144（登录页白圆徽章）
 *   brand/cart.png / cart-active.png                               96×96（深色购物车栏）
 *
 * 用法：node scripts/gen-brand-assets.mjs   （或 pnpm gen:assets）
 */
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.resolve(ROOT, 'apps/customer/src/static');

/* ---------------- 颜色（取自 shared/styles/tokens.css，勿自行另造色值） ---------------- */
const INK = '#6e6e6e';        // --color-text-hint：tabBar 普通态
const GOLD = '#b87d00';       // --color-tab-active：tabBar 激活态（白底细图标 AA）
const CART_EMPTY = '#d2d2d2'; // 深栏上的空车灰
const CART_FULL = '#ffc200';  // --color-primary：有货购物车
const BROWN = '#4a3500';      // --color-primary-text：徽章碗上的深色（本徽章未用，预留）
void BROWN;

/* ---------------- 极简 RGBA 画布（距离场覆盖 + source-over 合成） ---------------- */
class Canvas {
  constructor(size) {
    this.size = size;
    this.buf = new Uint8ClampedArray(size * size * 4);
  }

  blend(x, y, color, coverage) {
    if (x < 0 || y < 0 || x >= this.size || y >= this.size) return;
    const a = color[3] * Math.max(0, Math.min(1, coverage));
    if (a <= 0) return;
    const i = (y * this.size + x) * 4;
    const inv = 1 - a;
    this.buf[i] = color[0] * a + this.buf[i] * inv;
    this.buf[i + 1] = color[1] * a + this.buf[i + 1] * inv;
    this.buf[i + 2] = color[2] * a + this.buf[i + 2] * inv;
    this.buf[i + 3] = a * 255 + this.buf[i + 3] * inv;
  }

  /** 圆头线段：对包围盒内像素算到线段的精确距离，天然抗锯齿 */
  line(x0, y0, x1, y1, w, color) {
    const r = w / 2;
    const minX = Math.max(0, Math.floor(Math.min(x0, x1) - r - 1));
    const maxX = Math.min(this.size - 1, Math.ceil(Math.max(x0, x1) + r + 1));
    const minY = Math.max(0, Math.floor(Math.min(y0, y1) - r - 1));
    const maxY = Math.min(this.size - 1, Math.ceil(Math.max(y0, y1) + r + 1));
    const vx = x1 - x0;
    const vy = y1 - y0;
    const len2 = vx * vx + vy * vy || 1;
    for (let y = minY; y <= maxY; y++) {
      for (let x = minX; x <= maxX; x++) {
        let t = ((x - x0) * vx + (y - y0) * vy) / len2;
        t = Math.max(0, Math.min(1, t));
        const dx = x - (x0 + vx * t);
        const dy = y - (y0 + vy * t);
        const d = Math.sqrt(dx * dx + dy * dy);
        if (d <= r + 1) this.blend(x, y, color, r - d + 0.5);
      }
    }
  }

  /** 描边椭圆（a0/a1 为角度，弧度，0=右，顺时针；不传画整圆） */
  ellipseStroke(cx, cy, rx, ry, w, color, a0, a1) {
    const full = a0 === undefined;
    const start = full ? 0 : a0;
    const end = full ? Math.PI * 2 : a1;
    const steps = Math.max(16, Math.ceil(((end - start) * (rx + ry)) / 2));
    let px = null;
    let py = null;
    for (let i = 0; i <= steps; i++) {
      const a = start + ((end - start) * i) / steps;
      const x = cx + Math.cos(a) * rx;
      const y = cy + Math.sin(a) * ry;
      if (px !== null) this.line(px, py, x, y, w, color);
      px = x; py = y;
    }
  }

  circleStroke(cx, cy, r, w, color, a0, a1) {
    this.ellipseStroke(cx, cy, r, r, w, color, a0, a1);
  }

  fillCircle(cx, cy, r, color) {
    this.fillEllipse(cx, cy, r, r, color);
  }

  fillEllipse(cx, cy, rx, ry, color) {
    const minX = Math.max(0, Math.floor(cx - rx - 1));
    const maxX = Math.min(this.size - 1, Math.ceil(cx + rx + 1));
    const minY = Math.max(0, Math.floor(cy - ry - 1));
    const maxY = Math.min(this.size - 1, Math.ceil(cy + ry + 1));
    // 椭圆边界处的归一化距离→像素距离换算半径，用于 1px 抗锯齿过渡
    const rMean = (rx + ry) / 2;
    for (let y = minY; y <= maxY; y++) {
      for (let x = minX; x <= maxX; x++) {
        const nx = (x - cx) / rx;
        const ny = (y - cy) / ry;
        const pxDist = (Math.sqrt(nx * nx + ny * ny) - 1) * rMean;
        if (pxDist <= 0.5) this.blend(x, y, color, Math.max(0, 0.5 - pxDist));
      }
    }
  }

  /** 填充多边形（scanline）；顶点建议用贝塞尔采样到足够密 */
  fillPolygon(points, color) {
    let minY = Infinity;
    let maxY = -Infinity;
    for (const p of points) {
      minY = Math.min(minY, p[1]);
      maxY = Math.max(maxY, p[1]);
    }
    for (let y = Math.max(0, Math.floor(minY)); y <= Math.min(this.size - 1, Math.ceil(maxY)); y++) {
      const xs = [];
      for (let i = 0; i < points.length; i++) {
        const a = points[i];
        const b = points[(i + 1) % points.length];
        if ((a[1] <= y && b[1] > y) || (b[1] <= y && a[1] > y)) {
          xs.push(a[0] + ((y - a[1]) * (b[0] - a[0])) / (b[1] - a[1]));
        }
      }
      xs.sort((p, q) => p - q);
      for (let i = 0; i + 1 < xs.length; i += 2) {
        for (let x = Math.max(0, Math.ceil(xs[i] - 0.5)); x <= Math.min(this.size - 1, xs[i + 1] + 0.5); x++) {
          this.blend(x, y, color, 1);
        }
      }
    }
  }

  /** 圆角矩形描边（4 直边 + 4 圆弧） */
  roundRectStroke(x0, y0, x1, y1, r, w, color) {
    this.line(x0 + r, y0, x1 - r, y0, w, color);
    this.line(x0 + r, y1, x1 - r, y1, w, color);
    this.line(x0, y0 + r, x0, y1 - r, w, color);
    this.line(x1, y0 + r, x1, y1 - r, w, color);
    const arcs = [
      [x1 - r, y0 + r, -Math.PI / 2, 0],
      [x1 - r, y1 - r, 0, Math.PI / 2],
      [x0 + r, y1 - r, Math.PI / 2, Math.PI],
      [x0 + r, y0 + r, Math.PI, Math.PI * 1.5]
    ];
    for (const [cx, cy, a0, a1] of arcs) this.circleStroke(cx, cy, r, w, color, a0, a1);
  }

  /* ---------------- PNG 编码（8-bit RGBA, color type 6） ---------------- */
  encodePng() {
    const n = this.size;
    const raw = Buffer.alloc((n * 4 + 1) * n);
    for (let y = 0; y < n; y++) {
      raw[y * (n * 4 + 1)] = 0; // filter: none
      Buffer.from(this.buf.buffer, y * n * 4, n * 4).copy(raw, y * (n * 4 + 1) + 1);
    }
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(n, 0);
    ihdr.writeUInt32BE(n, 4);
    ihdr[8] = 8;  // bit depth
    ihdr[9] = 6;  // color type RGBA
    // 10-12 compression/filter/interlace = 0
    return Buffer.concat([
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      chunk('IHDR', ihdr),
      chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
      chunk('IEND', Buffer.alloc(0))
    ]);
  }
}

/* CRC32（PNG chunk 校验） */
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crc]);
}

function hex(hexColor, alpha = 1) {
  const h = hexColor.replace('#', '');
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), alpha];
}

/** 二次贝塞尔采样为点列 */
function quad(p0, p1, p2, steps) {
  const pts = [];
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const u = 1 - t;
    pts.push([
      u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
      u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1]
    ]);
  }
  return pts;
}

/* ---------------- 三个 tabBar 线性图标（81×81，统一笔触） ---------------- */
function drawHome(c, color) {
  // 叉子（左）：中齿连柄 + 两侧齿 + 齿根横杠
  c.line(30, 19, 30, 62, 5.2, color);
  c.line(22.5, 19, 22.5, 37, 4.6, color);
  c.line(37.5, 19, 37.5, 37, 4.6, color);
  c.line(20.5, 38.5, 39.5, 38.5, 5.2, color);
  // 餐刀（右）：刀身胶囊略宽于柄
  c.line(53, 18, 53, 47, 8.4, color);
  c.line(53, 47, 53, 62, 5.2, color);
}

function drawOrder(c, color) {
  // 单据外轮廓（圆角矩形）+ 右上折角 + 三条横线
  c.roundRectStroke(19, 14, 62, 67, 7, 5.2, color);
  c.line(49, 15.5, 49, 26.5, 4.4, color);
  c.line(49, 26.5, 60.5, 26.5, 4.4, color);
  c.line(29, 39, 52, 39, 4.6, color);
  c.line(29, 50, 52, 50, 4.6, color);
  c.line(29, 61, 45, 61, 4.6, color);
}

function drawMe(c, color) {
  // 头
  c.circleStroke(40, 29, 10.5, 5.4, color);
  // 肩：大椭圆上弧（200°→340°）
  c.ellipseStroke(40, 82, 23, 20.5, 5.4, color, Math.PI + Math.PI / 9, Math.PI * 2 - Math.PI / 9);
}

/* ---------------- 购物车（96×96，深色悬浮栏用） ---------------- */
function drawCart(c, color) {
  // 把手
  c.line(16, 17, 27, 32, 5.4, color);
  // 车筐：口沿 + 两侧斜边 + 底
  c.line(24, 32, 76, 32, 5.4, color);
  c.line(30.5, 33, 37.5, 70, 5.4, color);
  c.line(67.5, 33, 60.5, 70, 5.4, color);
  c.line(37.5, 70, 60.5, 70, 5.4, color);
  // 两轮
  c.circleStroke(40, 81, 5.5, 5, color);
  c.circleStroke(58, 81, 5.5, 5, color);
}

/* ---------------- 品牌徽章（144×144，白圆底 + 金碗米饭） ---------------- */
function drawLogo(c) {
  const white = hex('#ffffff');
  const bowl = hex('#ff9a00');       // 碗身
  const rim = hex('#ffb300');        // 碗口沿
  const rice = hex('#ffd54f');       // 饭团
  const riceInner = hex('#fff7e0');  // 碗口内米白
  const highlight = hex('#ffffff', 0.55);

  c.fillCircle(72, 72, 70, white);

  // 碗身：两条贝塞尔收到底座的闭合填充形（碗体占白圆约 60%，对齐旧版徽章比例）
  const left = quad([32, 66], [36, 98], [60, 108], 18);
  const right = quad([112, 66], [108, 98], [84, 108], 18);
  const bowlShape = [...left, ...right.slice().reverse()];
  c.fillPolygon(bowlShape, bowl);
  // 柔化曲线边
  for (let i = 0; i < left.length - 1; i++) c.line(left[i][0], left[i][1], left[i + 1][0], left[i + 1][1], 2, bowl);
  for (let i = 0; i < right.length - 1; i++) c.line(right[i][0], right[i][1], right[i + 1][0], right[i + 1][1], 2, bowl);
  // 底座
  c.fillEllipse(72, 108, 15, 5, bowl);

  // 碗口沿 + 内凹
  c.fillEllipse(72, 66, 40, 11, rim);
  c.fillEllipse(72, 64.5, 32.5, 7.2, riceInner);
  // 三个饭团冒出碗口
  c.fillCircle(59, 58, 7.5, rice);
  c.fillCircle(72, 52, 9, rice);
  c.fillCircle(85, 58, 7.5, rice);

  // 碗身左上高光
  c.ellipseStroke(53, 85, 11, 17, 3, highlight, Math.PI * 0.62, Math.PI * 0.95);
}

/* ---------------- 生成与落盘 ---------------- */
function writePng(name, size, draw, ...args) {
  const c = new Canvas(size);
  draw(c, ...args);
  const file = path.join(OUT, name);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, c.encodePng());
  return file;
}

const files = [];
for (const [base, draw] of [['home', drawHome], ['order', drawOrder], ['me', drawMe]]) {
  files.push(writePng(`tabbar/${base}.png`, 81, draw, hex(INK)));
  files.push(writePng(`tabbar/${base}-active.png`, 81, draw, hex(GOLD)));
}
files.push(writePng('brand/logo.png', 144, drawLogo));
files.push(writePng('brand/cart.png', 96, drawCart, hex(CART_EMPTY)));
files.push(writePng('brand/cart-active.png', 96, drawCart, hex(CART_FULL)));

for (const f of files) console.log('已生成 ' + path.relative(ROOT, f).replaceAll('\\', '/'));
console.log(`共 ${files.length} 张 PNG`);
