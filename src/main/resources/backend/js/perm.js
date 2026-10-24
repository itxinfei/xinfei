/**
 * 前端按钮级权限助手 + v-perm 指令（O-27 按钮级权限联动）
 *
 * 数据源：壳层 loadMenuAccess() 调 GET /sys/menu/access 后把
 * { roleKey, isSuperAdmin, perms[] } 写入 sessionStorage('xinfei:perm')；
 * 同源 iframe 页面共享同标签页的 sessionStorage，直接读取，无需每页重复请求。
 *
 * 判定口径（与后端 PermissionAspect 对齐，但仅做 UX 收敛、越权仍由后端拦截）：
 *   - SUPER_ADMIN 恒 true（切面同款直接放行）；
 *   - session 数据缺失（直接开页/加载失败）→ fail-open 显示，不误伤可用性；
 *   - 其余按 perms 集合判定；细粒度键（如 platform:manage）当前未入权限字典，
 *     等效于仅超管可见——与线上后端实际行为一致。
 *
 * 用法：
 *   - 模板指令：v-perm="'platform:manage'"（无权限则隐藏元素）
 *   - JS/computed：XinfeiPerm.has('notification:send')（如 table-bar actions 的 visible 配置）
 */
;(function () {
  var win = window

  function loadData() {
    try {
      var raw = win.sessionStorage && win.sessionStorage.getItem('xinfei:perm')
      if (raw) {
        var parsed = JSON.parse(raw)
        if (parsed && typeof parsed === 'object') return parsed
      }
    } catch (e) { /* 坏值按无数据 fail-open */ }
    return { roleKey: '', isSuperAdmin: false, perms: [] }
  }

  var XinfeiPerm = {
    /** 是否超管（切面同款 SUPER_ADMIN 直接放行） */
    isSuper: function () {
      var d = loadData()
      return !!(d.isSuperAdmin || d.roleKey === 'SUPER_ADMIN')
    },
    /** 当前权限集快照（调试/页面自检用） */
    snapshot: function () { return loadData() },
    /**
     * 是否拥有某权限键。session 无数据时 fail-open（后端仍是真正的拦截点）。
     */
    has: function (key) {
      if (!key) return true
      if (this.isSuper()) return true
      var d = loadData()
      if (!d || !Array.isArray(d.perms) || d.perms.length === 0) return true
      return d.perms.indexOf(key) !== -1
    }
  }

  win.XinfeiPerm = XinfeiPerm

  /* v-perm 指令（经 window.XinfeiDirectivesV3 由 app-v3.js 统一注册）：
     无权限时隐藏元素；权限数据变化（重新登录）由整页刷新兜底，updated 钩子做恢复 */
  var dirs = win.XinfeiDirectivesV3 || (win.XinfeiDirectivesV3 = {})
  dirs['perm'] = {
    mounted: function (el, binding) { apply(el, !!XinfeiPerm.has(binding.value)) },
    updated: function (el, binding) { apply(el, !!XinfeiPerm.has(binding.value)) }
  }

  function apply(el, allowed) {
    if (!allowed) {
      el.style.display = 'none'
      el.setAttribute('data-perm-hidden', '1')
    } else if (el.getAttribute('data-perm-hidden')) {
      // 恢复时清掉内联隐藏并移除标记，交还样式表控制 display
      el.style.display = ''
      el.removeAttribute('data-perm-hidden')
    }
  }
})()
