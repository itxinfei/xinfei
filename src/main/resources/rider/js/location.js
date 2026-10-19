/**
 * 骑手位置定时上报。
 * 骑手在线的页面（index/hall/detail）引入并 RiderLocation.start() 后，
 * 每 12 秒读取一次 GPS 并 POST 到 /api/rider/location，供顾客端配送追踪使用。
 *
 * 坐标口径（2026-10-19 百度地图改造 P1）：浏览器 geolocation 返回 WGS-84，本端如实上报原始
 * WGS-84 并在 body 声明 coordSystem，服务端入库标记坐标系；「转成地图引擎坐标系（百度 BD-09）」
 * 的职责从上报端前移到渲染端（见 front/rider 地图渲染的坐标转换 util），使同一份原始坐标
 * 既能喂百度、也能喂任何其它引擎，库内数据不再被不可逆地预转成某一系。
 *
 * 降级：非安全上下文（内网 HTTP 且非 localhost）下浏览器不提供 navigator.geolocation，
 * 此时静默不启动，不报错、不影响接单主流程；待站点升级 HTTPS 后自动生效。
 */
(function (win) {
  var INTERVAL_MS = 12000;

  // ---- 在线状态标志（由工作台上下线开关维护，hall/detail 页读取）----
  function isOnline() {
    // 无标志时默认在线，保证登录后各页行为与历史一致
    try { return sessionStorage.getItem('riderOnline') !== '0'; } catch (e) { return true; }
  }

  function setOnlineFlag(v) {
    try { sessionStorage.setItem('riderOnline', v ? '1' : '0'); } catch (e) { /* 忽略 */ }
  }

  var RiderLocation = {
    _timer: null,

    start: function () {
      // 浏览器不支持定位（如内网HTTP）→ 静默降级
      if (!navigator.geolocation) { return; }
      // 已知离线（工作台下线后进入 hall/detail）：不启动定时器
      if (!isOnline()) { return; }
      if (this._timer) { return; }  // 防重复启动
      var self = this;
      var report = function () { self._reportOnce(); };
      report();  // 进页面立即上报一次，不必等首个间隔
      this._timer = setInterval(report, INTERVAL_MS);
    },

    stop: function () {
      if (this._timer) {
        clearInterval(this._timer);
        this._timer = null;
      }
    },

    // 供工作台开关调用：下线时停止上报，省电且避免无效请求
    setEnabled: function (online) {
      setOnlineFlag(online);
      if (online) { this.start(); } else { this.stop(); }
    },

    _reportOnce: function () {
      // 骑手离线：跳过本轮，不耗电上报
      if (!isOnline()) { return; }
      navigator.geolocation.getCurrentPosition(function (position) {
        // 等待定位期间骑手可能已下线
        if (!isOnline()) { return; }
        var coords = position.coords;
        // 上报失败静默（下次定时自动重试），避免 Toast 打扰骑手正常作业
        $axios.post('/api/rider/location', {
          longitude: coords.longitude,   // 原始 WGS-84，不再前端预转
          latitude: coords.latitude,
          coordSystem: 'WGS-84',
          speed: coords.speed,
          heading: coords.heading
        }).catch(function () { /* 忽略，下轮重试 */ });
      }, function () {
        // 定位失败（拒绝授权/信号弱）：静默等待下轮，不弹错误
      }, {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 5000
      });
    }
  };

  win.RiderLocation = RiderLocation;
})(window);
