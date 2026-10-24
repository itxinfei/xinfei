/**
 * 导出任务中心组件（O-25）—— Vue 3 / Element Plus 版
 *
 * 与 chart-panel-v3.js 同一套注册机制：本文件只把组件塞进 window.XinfeiComponentsV3
 * 注册表，由 app-v3.js 的 createXinfeiApp() 统一 app.component() 注册（加载顺序无关）。
 *
 * 用法：
 *   1. 引入：<script src="../../api/export-task.js"></script>
 *           <script src="../../js/export-task-center.js"></script>
 *   2. 模板放 <export-task-center ref="exportCenter"></export-task-center>
 *   3. 打开：this.$refs.exportCenter.open()
 *
 * 功能：
 *   - 快捷导出：订单/菜品/员工/经营报表 4 类 → POST /export/tasks 异步任务（原导出中心页 4 卡片收编）
 *   - 任务列表：轮询（3s，弹窗打开期间）状态 0排队/1生成/2完成/3失败，完成可下载/删除
 */
;(function () {
  var XinfeiComponentsV3 = window.XinfeiComponentsV3 || (window.XinfeiComponentsV3 = {})

  var TYPE_OPTIONS = [
    { value: 'orders', label: '订单数据', needRange: true, needStatus: true },
    { value: 'dishes', label: '菜品数据', needRange: false, needStatus: false },
    { value: 'employees', label: '员工数据', needRange: false, needStatus: false },
    { value: 'users', label: '用户数据', needRange: false, needStatus: false },
    { value: 'report', label: '经营报表', needRange: true, needStatus: false }
  ]

  XinfeiComponentsV3['export-task-center'] = {
    name: 'ExportTaskCenter',
    data: function () {
      return {
        visible: false,
        loading: false,
        submitting: false,
        // 快捷导出表单（原导出中心页 4 卡片收编为一行表单）
        quickType: 'orders',
        quickFormat: 'excel',
        quickDateRange: [],
        quickStatus: '',
        // 任务列表
        tasks: [],
        total: 0,
        page: 1,
        pageSize: 10,
        pollTimer: null,
        // 日期快捷项（与营业日报同机制）
        dateShortcuts: [
          { text: '今天', value: function () { var d = new Date(); return [d, d] } },
          { text: '近7天', value: function () { var e = new Date(); var s = new Date(); s.setDate(s.getDate() - 6); return [s, e] } },
          { text: '近30天', value: function () { var e = new Date(); var s = new Date(); s.setDate(s.getDate() - 29); return [s, e] } }
        ]
      }
    },
    computed: {
      typeOptions: function () { return TYPE_OPTIONS },
      currentType: function () {
        var self = this
        return TYPE_OPTIONS.find(function (t) { return t.value === self.quickType }) || TYPE_OPTIONS[0]
      }
    },
    beforeUnmount: function () {
      this.stopPoll()
    },
    methods: {
      open: function () {
        this.visible = true
        this.loadTasks()
        this.startPoll()
      },
      // ========== 快捷导出 ==========
      submitQuick: function () {
        var self = this
        var t = this.currentType
        if (t.needRange && (!this.quickDateRange || this.quickDateRange.length < 2 || !this.quickDateRange[0])) {
          XinfeiUI.warning('请选择日期范围'); return
        }
        var params = {}
        if (t.needRange && this.quickDateRange && this.quickDateRange.length === 2) {
          params.startDate = this.quickDateRange[0]
          params.endDate = this.quickDateRange[1]
        }
        if (t.needStatus && this.quickStatus) {
          params.status = this.quickStatus
        }
        this.submitting = true
        createExportTask({ taskType: this.quickType, fileFormat: this.quickFormat, params: params })
          .then(function (res) {
            if (String(res.code) === '1') {
              XinfeiUI.success('导出任务已提交，完成后可在任务列表下载')
              self.page = 1
              self.loadTasks()
              self.startPoll()
            } else {
              XinfeiUI.error(res.msg || '导出任务提交失败')
            }
          })
          .catch(function (err) { XinfeiUI.error('导出任务提交失败：' + (err.message || err)) })
          .finally(function () { self.submitting = false })
      },
      // ========== 任务列表 ==========
      loadTasks: function () {
        var self = this
        self.loading = true
        listExportTasks({ page: self.page, pageSize: self.pageSize })
          .then(function (res) {
            if (String(res.code) === '1' && res.data) {
              self.tasks = res.data.records || []
              self.total = Number(res.data.total) || 0
            }
          })
          .catch(function () { /* 静默：轮询场景不弹错 */ })
          .finally(function () { self.loading = false })
      },
      onPageChange: function (p) {
        this.page = p
        this.loadTasks()
      },
      startPoll: function () {
        var self = this
        this.stopPoll()
        this.pollTimer = setInterval(function () {
          // 弹窗关闭即停；列表有终态任务也照刷（幂等轻查询）
          if (!self.visible) { self.stopPoll(); return }
          listExportTasks({ page: self.page, pageSize: self.pageSize })
            .then(function (res) {
              if (String(res.code) === '1' && res.data) {
                self.tasks = res.data.records || []
                self.total = Number(res.data.total) || 0
              }
            })
            .catch(function () { /* 轮询静默 */ })
        }, 3000)
      },
      stopPoll: function () {
        if (this.pollTimer) {
          clearInterval(this.pollTimer)
          this.pollTimer = null
        }
      },
      download: function (row) {
        if (row.status !== 2) return
        var a = document.createElement('a')
        a.href = '/export/tasks/' + row.id + '/file'
        a.style.display = 'none'
        document.body.appendChild(a)
        a.click()
        setTimeout(function () { document.body.removeChild(a) }, 200)
        XinfeiUI.success('开始下载：' + (row.fileName || '导出文件'))
      },
      remove: function (row) {
        var self = this
        XinfeiUI.confirm('确认删除该导出任务？生成文件将一并删除', '提示', {
          confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
        }).then(function () {
          deleteExportTask(row.id).then(function (res) {
            if (String(res.code) === '1') {
              XinfeiUI.success('任务已删除')
              self.loadTasks()
            } else {
              XinfeiUI.error(res.msg || '删除失败')
            }
          }).catch(function (err) { XinfeiUI.error('删除失败：' + (err.message || err)) })
        }).catch(function () { /* 用户取消 */ })
      },
      // ========== 展示辅助 ==========
      typeLabel: function (v) {
        var hit = TYPE_OPTIONS.find(function (t) { return t.value === v })
        return hit ? hit.label : v
      },
      statusTag: function (s) {
        // 0排队中 1生成中 2已完成 3失败
        return [
          { text: '排队中', cls: 'info' },
          { text: '生成中', cls: 'warning' },
          { text: '已完成', cls: 'success' },
          { text: '失败', cls: 'danger' }
        ][s] || { text: '未知', cls: 'info' }
      },
      fmtTime: function (s) { return s || '--' },
      fmtSize: function (n) {
        if (!n) return '--'
        var k = 1024, sizes = ['B', 'KB', 'MB', 'GB']
        var i = Math.min(Math.floor(Math.log(n) / Math.log(k)), sizes.length - 1)
        return (n / Math.pow(k, i)).toFixed(i > 0 ? 2 : 0) + ' ' + sizes[i]
      },
      fmtRange: function (row) {
        if (!row.paramsJson) return '全部'
        try {
          var p = JSON.parse(row.paramsJson)
          if (p.startDate && p.endDate) return p.startDate + ' ~ ' + p.endDate
          return p.startDate || p.endDate || '全部'
        } catch (e) { return '全部' }
      }
    },
    template:
      '<el-dialog v-model="visible" title="导出中心" width="760px" append-to-body @closed="stopPoll">' +
        '<el-form inline size="small" class="etc-quick-form">' +
          '<el-form-item label="数据类型">' +
            '<el-select v-model="quickType" style="width:140px">' +
              '<el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value"></el-option>' +
            '</el-select>' +
          '</el-form-item>' +
          '<el-form-item label="格式">' +
            '<el-radio-group v-model="quickFormat">' +
              '<el-radio-button value="excel">Excel</el-radio-button>' +
              '<el-radio-button value="pdf">PDF</el-radio-button>' +
            '</el-radio-group>' +
          '</el-form-item>' +
          '<el-form-item v-if="currentType.needRange" label="日期范围">' +
            '<el-date-picker v-model="quickDateRange" type="daterange" value-format="YYYY-MM-DD"' +
              ' range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"' +
              ' :shortcuts="dateShortcuts" style="width:240px"></el-date-picker>' +
          '</el-form-item>' +
          '<el-form-item v-if="currentType.needStatus" label="订单状态">' +
            '<el-select v-model="quickStatus" clearable placeholder="全部状态" style="width:120px">' +
              '<el-option label="待付款" value="1"></el-option>' +
              '<el-option label="待接单" value="2"></el-option>' +
              '<el-option label="配送中" value="3"></el-option>' +
              '<el-option label="已完成" value="4"></el-option>' +
              '<el-option label="已取消" value="5"></el-option>' +
              '<el-option label="已退款" value="6"></el-option>' +
            '</el-select>' +
          '</el-form-item>' +
          '<el-form-item>' +
            '<el-button type="primary" :loading="submitting" @click="submitQuick">创建导出任务</el-button>' +
          '</el-form-item>' +
        '</el-form>' +
        '<el-divider content-position="left">任务列表（完成后可下载，保留 7 天）</el-divider>' +
        '<el-table :data="tasks" v-loading="loading" size="small" stripe>' +
          '<el-table-column label="创建时间" width="150">' +
            '<template #default="{ row }">{{ fmtTime(row.createTime) }}</template>' +
          '</el-table-column>' +
          '<el-table-column label="类型" width="90">' +
            '<template #default="{ row }">{{ typeLabel(row.taskType) }}</template>' +
          '</el-table-column>' +
          '<el-table-column label="格式" width="70" align="center">' +
            '<template #default="{ row }">' +
              '<span :class="[\'etc-tag\', row.fileFormat === \'pdf\' ? \'is-pdf\' : \'is-excel\']">{{ row.fileFormat === \'pdf\' ? \'PDF\' : \'Excel\' }}</span>' +
            '</template>' +
          '</el-table-column>' +
          '<el-table-column label="数据范围" width="180">' +
            '<template #default="{ row }">{{ fmtRange(row) }}</template>' +
          '</el-table-column>' +
          '<el-table-column label="状态" width="96" align="center">' +
            '<template #default="{ row }">' +
              '<span :class="[\'etc-tag\', \'is-\' + statusTag(row.status).cls]">' +
                '<i v-if="row.status === 1" class="ri-loader-2-line etc-spin"></i>{{ statusTag(row.status).text }}</span>' +
            '</template>' +
          '</el-table-column>' +
          '<el-table-column label="文件" min-width="160" show-overflow-tooltip>' +
            '<template #default="{ row }">{{ row.fileName || (row.status === 3 ? row.errorMsg : \'--\') }}</template>' +
          '</el-table-column>' +
          '<el-table-column label="大小" width="80">' +
            '<template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>' +
          '</el-table-column>' +
          '<el-table-column label="操作" width="110" align="center">' +
            '<template #default="{ row }">' +
              '<el-button v-if="row.status === 2" text type="primary" size="small" icon="ri-download-line" @click="download(row)">下载</el-button>' +
              '<el-button text type="danger" size="small" icon="ri-delete-bin-line" @click="remove(row)">删除</el-button>' +
            '</template>' +
          '</el-table-column>' +
        '</el-table>' +
        '<div style="margin-top:12px;display:flex;justify-content:flex-end;">' +
          '<el-pagination v-if="total > pageSize" small background layout="prev, pager, next"' +
            ' :total="total" :page-size="pageSize" :current-page="page" @current-change="onPageChange"></el-pagination>' +
        '</div>' +
      '</el-dialog>'
  }
})()
