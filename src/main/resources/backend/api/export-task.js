/**
 * 异步导出任务 API（O-25 导出任务中心）
 * 走全局 $axios（JSON 通道）；文件下载用直链 <a href>（同源 Cookie 随请求，
 * 后端 Content-Disposition 触发浏览器另存，无需 blob 转发）。
 */
const createExportTask = (data) => $axios({ url: '/export/tasks', method: 'post', data })
const listExportTasks = (params) => $axios({ url: '/export/tasks', method: 'get', params })
const deleteExportTask = (id) => $axios({ url: '/export/tasks/' + id, method: 'delete' })
