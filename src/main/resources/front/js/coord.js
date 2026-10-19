/**
 * 坐标系转换工具（浏览器端渲染层单一真源）。
 *
 * 背景（2026-10-19 百度地图改造 P2）：库内位置存的是「原始坐标系」（新数据 WGS-84、
 * 历史数据 GCJ-02，见每行的 coord_system 列），上报端不再预转成某一引擎系。地图渲染前
 * 必须把坐标转换到当前地图引擎坐标系——本页用百度，故统一转 BD-09。
 *
 * 转换链：WGS-84 --(国测局火星算法)--> GCJ-02 --(百度偏移)--> BD-09；GCJ-02 直接走第二段。
 * 公式为业界通用口径（eviltransform / 百度、高德开放平台一致），境外坐标不做偏移。
 *
 * 用法：XinfeiCoord.toBd09(lng, lat, row.coordSystem) → [bdLng, bdLat]
 */
(function (win) {
  var PI = 3.1415926535897932384626;
  var X_PI = PI * 3000.0 / 180.0;
  var A = 6378245.0;
  var EE = 0.00669342162296594323;

  function outOfChina(lng, lat) {
    return (lng < 72.004 || lng > 137.8347) || (lat < 0.8293 || lat > 55.8271);
  }

  function transformLat(lng, lat) {
    var ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat +
      0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng));
    ret += (20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0 / 3.0;
    ret += (20.0 * Math.sin(lat * PI) + 40.0 * Math.sin(lat / 3.0 * PI)) * 2.0 / 3.0;
    ret += (160.0 * Math.sin(lat / 12.0 * PI) + 320 * Math.sin(lat * PI / 30.0)) * 2.0 / 3.0;
    return ret;
  }

  function transformLng(lng, lat) {
    var ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng +
      0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng));
    ret += (20.0 * Math.sin(6.0 * lng * PI) + 20.0 * Math.sin(2.0 * lng * PI)) * 2.0 / 3.0;
    ret += (20.0 * Math.sin(lng * PI) + 40.0 * Math.sin(lng / 3.0 * PI)) * 2.0 / 3.0;
    ret += (150.0 * Math.sin(lng / 12.0 * PI) + 300 * Math.sin(lng / 30.0 * PI)) * 2.0 / 3.0;
    return ret;
  }

  /** WGS-84 → GCJ-02（国测局火星坐标）。境外不转换。 */
  function wgs84ToGcj02(lng, lat) {
    if (outOfChina(lng, lat)) { return [lng, lat]; }
    var dLat = transformLat(lng - 105.0, lat - 35.0);
    var dLng = transformLng(lng - 105.0, lat - 35.0);
    var radLat = lat / 180.0 * PI;
    var magic = Math.sin(radLat);
    magic = 1 - EE * magic * magic;
    var sqrtMagic = Math.sqrt(magic);
    dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
    dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
    return [lng + dLng, lat + dLat];
  }

  /** GCJ-02 → BD-09（百度坐标）。 */
  function gcj02ToBd09(lng, lat) {
    var z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI);
    var theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI);
    var bdLng = z * Math.cos(theta) + 0.0065;
    var bdLat = z * Math.sin(theta) + 0.006;
    return [bdLng, bdLat];
  }

  /** WGS-84 → BD-09（先转火星系再转百度系）。 */
  function wgs84ToBd09(lng, lat) {
    var gcj = wgs84ToGcj02(lng, lat);
    return gcj02ToBd09(gcj[0], gcj[1]);
  }

  /**
   * 按源坐标系转换到百度 BD-09。
   * @param {number} lng 经度
   * @param {number} lat 纬度
   * @param {string} fromSystem 源坐标系（WGS-84 / GCJ-02 / BD-09）；缺省按 WGS-84 处理
   * @returns {number[]} [经度, 纬度]（BD-09）
   */
  function toBd09(lng, lat, fromSystem) {
    lng = Number(lng);
    lat = Number(lat);
    if (!isFinite(lng) || !isFinite(lat)) { return [lng, lat]; }
    var s = (fromSystem || 'WGS-84').toUpperCase();
    if (s === 'BD-09') { return [lng, lat]; }
    if (s === 'GCJ-02') { return gcj02ToBd09(lng, lat); }
    return wgs84ToBd09(lng, lat);
  }

  win.XinfeiCoord = {
    wgs84ToGcj02: wgs84ToGcj02,
    gcj02ToBd09: gcj02ToBd09,
    wgs84ToBd09: wgs84ToBd09,
    toBd09: toBd09
  };
})(window);
