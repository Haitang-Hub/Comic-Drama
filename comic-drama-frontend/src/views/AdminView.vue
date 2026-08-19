<template>
  <div class="admin" v-loading="globalLoading">
    <!-- 顶部统计卡 -->
    <div class="stat-grid">
      <div v-for="s in statCards" :key="s.key" class="stat-card sketch-card">
        <div class="stat-icon" :style="{ backgroundColor: s.color }">
          <el-icon><component :is="s.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" class="admin-tabs">
      <!-- 用户管理 -->
      <el-tab-pane label="用户管理" name="users">
        <div class="filter-bar sketch-card">
          <div class="filter-left">
            <el-input
              v-model="userQuery.keyword"
              placeholder="搜索用户名 / 昵称 / 邮箱"
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="loadUsers"
              @clear="loadUsers"
            />
            <el-select
              v-model="userQuery.status"
              placeholder="全部状态"
              clearable
              class="status-select"
              @change="loadUsers"
            >
              <el-option label="正常" :value="1" />
              <el-option label="已禁用" :value="0" />
            </el-select>
          </div>
          <div class="filter-right">
            <button class="sketch-btn" @click="openCreateUser">
              <el-icon><Plus /></el-icon>
              新建用户
            </button>
          </div>
        </div>

        <div class="table-card sketch-card" v-loading="userLoading">
          <el-table :data="userList" style="width: 100%" row-key="id" stripe>
            <el-table-column label="用户" min-width="220">
              <template #default="{ row }">
                <div class="cell-user">
                  <el-avatar :size="38" class="cell-avatar">
                    <img v-if="row.avatar" :src="row.avatar" />
                    <span v-else>{{ (row.nickname || row.username || '?').charAt(0) }}</span>
                  </el-avatar>
                  <div class="cell-user-info">
                    <div class="cell-name-row">
                      <span class="cell-name">{{ row.nickname || row.username || '-' }}</span>
                      <span class="cell-id">#{{ row.id }}</span>
                    </div>
                    <div class="cell-sub">{{ row.email || row.phone || '暂无联系方式' }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="角色" width="140">
              <template #default="{ row }">
                <el-tag
                  :type="row.role === 'ADMIN' ? 'warning' : 'success'"
                  effect="plain"
                  round
                  size="small"
                  class="role-tag"
                >
                  {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain" round size="small">
                  {{ row.status === 1 ? '正常' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="注册时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button link type="primary" size="small" @click="openEditUser(row as AdminUserVO)">编辑</el-button>
                  <el-button
                    v-if="row.status === 1"
                    link
                    type="warning"
                    size="small"
                    @click="handleDisable(row as AdminUserVO)"
                  >
                    禁用
                  </el-button>
                  <el-button
                    v-else
                    link
                    type="success"
                    size="small"
                    @click="handleEnable(row as AdminUserVO)"
                  >
                    启用
                  </el-button>
                  <el-button link type="danger" size="small" @click="handleDeleteUser(row as AdminUserVO)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="userQuery.page"
              v-model:page-size="userQuery.size"
              :total="userTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadUsers"
              @size-change="handleUserSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 系统设置 -->
      <el-tab-pane label="系统设置" name="config">
        <div class="config-grid">
          <div class="config-card sketch-card">
            <div class="config-head">
              <h3>模型配置</h3>
              <Doodles :size="50" :rotate="6" type="sparkle" :opacity="0.65" />
            </div>
            <div class="config-list">
              <div v-for="item in modelConfigs" :key="item.stepCode" class="config-item">
                <div class="config-label">
                  <div class="config-key">{{ item.stepName }}</div>
                  <div class="config-desc">步骤{{ item.stepOrder }}：{{ getStepDesc(item.stepCode) }}</div>
                </div>
                <el-select v-model="item.modelConfigId" class="config-select" placeholder="请选择模型" clearable @change="(val: any) => { if (val === null || val === '') item.modelConfigId = null }">
                  <el-option
                    v-for="opt in item.options"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </div>
            </div>
            <div class="config-actions">
              <div class="config-hint">步骤9（视频合并）为算法处理，无需配置AI模型</div>
              <button class="sketch-btn" @click="saveModelConfigs">保存配置</button>
            </div>
          </div>

          <div class="config-card sketch-card">
            <div class="config-head">
              <h3>平台参数</h3>
              <Doodles :size="50" :rotate="-8" type="circle" :opacity="0.6" />
            </div>
            <div class="config-list">
              <div v-for="item in platformConfigs" :key="item.key" class="config-item">
                <div class="config-label">
                  <div class="config-key">{{ item.label }}</div>
                  <div class="config-desc">{{ item.desc }}</div>
                </div>
                <el-input-number
                  v-model="item.value"
                  :min="item.min"
                  :max="item.max"
                  :step="item.step"
                />
              </div>
            </div>
            <div class="config-actions">
              <button class="sketch-btn" @click="savePlatformConfigs">保存配置</button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 模型配置 -->
      <el-tab-pane label="模型配置" name="models">
        <div class="filter-bar sketch-card">
          <div class="filter-left">
            <el-input
              v-model="modelQuery.keyword"
              placeholder="搜索模型服务商 / 名称"
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="loadModels"
              @clear="loadModels"
            />
            <el-select
              v-model="modelQuery.modelType"
              placeholder="模型类型"
              clearable
              class="status-select"
              @change="loadModels"
            >
              <el-option label="文本生成" :value="1" />
              <el-option label="图片生成" :value="2" />
              <el-option label="音频生成" :value="3" />
              <el-option label="视频生成" :value="4" />
            </el-select>
            <el-select
              v-model="modelQuery.status"
              placeholder="全部状态"
              clearable
              class="status-select"
              @change="loadModels"
            >
              <el-option label="正常" :value="1" />
              <el-option label="已禁用" :value="0" />
            </el-select>
          </div>
          <div class="filter-right">
            <button class="sketch-btn" @click="openCreateModel">
              <el-icon><Plus /></el-icon>
              新建模型
            </button>
          </div>
        </div>

        <div class="table-card sketch-card" v-loading="modelLoading">
          <el-table :data="modelList" style="width: 100%" row-key="id">
            <el-table-column label="模型服务商" min-width="130">
              <template #default="{ row }">{{ row.modelProvider }}</template>
            </el-table-column>
            <el-table-column label="模型名称" min-width="130">
              <template #default="{ row }">{{ row.modelName }}</template>
            </el-table-column>
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="modelTypeTag(row.modelType)" effect="light" round size="small">
                  {{ modelTypeText(row.modelType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="协议" width="140" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.protocol" type="warning" effect="plain" round size="small">
                  {{ protocolShortText(row.protocol) }}
                </el-tag>
                <span v-else class="text-secondary">按服务商路由</span>
              </template>
            </el-table-column>
            <el-table-column label="权重" width="70" align="center">
              <template #default="{ row }">{{ row.weight ?? 100 }}</template>
            </el-table-column>
            <el-table-column label="API地址" min-width="200">
              <template #default="{ row }">{{ row.apiUrl }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain" round size="small">
                  {{ row.status === 1 ? '正常' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="success" size="small" @click="handleTestModel(row as AiModelConfigVO)">测试</el-button>
                <el-button link type="primary" size="small" @click="openEditModel(row as AiModelConfigVO)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteModel(row as AiModelConfigVO)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="modelQuery.page"
              v-model:page-size="modelQuery.size"
              :total="modelTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadModels"
              @size-change="handleModelSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 提示词管理 -->
      <el-tab-pane label="提示词管理" name="templates">
        <div class="filter-bar sketch-card">
          <div class="filter-left">
            <el-input
              v-model="templateQuery.keyword"
              placeholder="搜索模板编码 / 名称"
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="loadTemplates"
              @clear="loadTemplates"
            />
            <el-select
              v-model="templateQuery.stage"
              placeholder="阶段"
              clearable
              class="status-select"
              @change="loadTemplates"
            >
              <el-option label="故事摘要" :value="1" />
              <el-option label="分镜脚本" :value="2" />
              <el-option label="资产设计" :value="3" />
              <el-option label="资产绘图" :value="4" />
              <el-option label="分镜绘图" :value="5" />
              <el-option label="配音合成" :value="6" />
              <el-option label="视频生成" :value="7" />
            </el-select>
          </div>
          <div class="filter-right">
            <button class="sketch-btn" @click="openCreateTemplate">
              <el-icon><Plus /></el-icon>
              新建模板
            </button>
          </div>
        </div>

        <div class="table-card sketch-card" v-loading="templateLoading">
          <el-table :data="templateList" style="width: 100%" row-key="id">
            <el-table-column label="模板编码" min-width="130">
              <template #default="{ row }">{{ row.templateCode }}</template>
            </el-table-column>
            <el-table-column label="模板名称" min-width="140">
              <template #default="{ row }">{{ row.templateName }}</template>
            </el-table-column>
            <el-table-column label="阶段" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="stageTag(row.stage)" effect="light" round size="small">
                  {{ stageText(row.stage) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="版本" width="90" align="center">
              <template #default="{ row }">{{ 'v' + (row.currentVersion || 1) }}</template>
            </el-table-column>
            <el-table-column label="启用" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'" effect="plain" round size="small">
                  {{ row.isEnabled === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditTemplate(row as PromptTemplateVO)">编辑</el-button>
                <el-button link type="info" size="small" @click="openVersions(row as PromptTemplateVO)">版本</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteTemplate(row as PromptTemplateVO)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="templateQuery.page"
              v-model:page-size="templateQuery.size"
              :total="templateTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadTemplates"
              @size-change="handleTemplateSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 用量统计 -->
      <el-tab-pane label="用量统计" name="usage">
        <div class="filter-bar sketch-card">
          <div class="filter-left">
            <el-input
              v-model="usageQuery.keyword"
              placeholder="搜索模型名称"
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="loadUsageLogs"
              @clear="loadUsageLogs"
            />
            <el-select
              v-model="usageQuery.modelName"
              placeholder="模型名称"
              clearable
              class="status-select"
              @change="loadUsageLogs"
            >
              <el-option
                v-for="m in modelNameOptions"
                :key="m"
                :label="m"
                :value="m"
              />
            </el-select>
            <el-select
              v-model="usageQuery.modelType"
              placeholder="模型类型"
              clearable
              class="status-select"
              @change="loadUsageLogs"
            >
              <el-option label="文本生成" :value="1" />
              <el-option label="图片生成" :value="2" />
              <el-option label="音频生成" :value="3" />
              <el-option label="视频生成" :value="4" />
            </el-select>
          </div>
        </div>

        <div class="table-card sketch-card" v-loading="usageLoading">
          <el-table :data="usageList" style="width: 100%" row-key="id">
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="模型名称" min-width="130">
              <template #default="{ row }">{{ row.modelName }}</template>
            </el-table-column>
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.modelType" :type="modelTypeTag(row.modelType)" effect="light" round size="small">
                  {{ modelTypeText(row.modelType) }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="步骤" width="80" align="center">
              <template #default="{ row }">{{ row.step ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="总Tokens" width="110" align="right">
              <template #default="{ row }">{{ row.totalTokens ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="费用(¥)" width="110" align="right">
              <template #default="{ row }">{{ row.costAmount ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="耗时(ms)" width="110" align="right">
              <template #default="{ row }">{{ row.latencyMs ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.status === 1 ? 'success' : row.status === 0 ? 'info' : 'danger'"
                  effect="plain"
                  round
                  size="small"
                >
                  {{ row.status === 1 ? '成功' : row.status === 0 ? '进行中' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="usageQuery.page"
              v-model:page-size="usageQuery.size"
              :total="usageTotal"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadUsageLogs"
              @size-change="handleUsageSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 资源中心 -->
      <el-tab-pane label="资源中心" name="resourceCenter">
        <ResourceCenterView />
      </el-tab-pane>

      <!-- 系统监控 -->
      <el-tab-pane label="系统监控" name="systemMonitor">
        <SystemMonitorView />
      </el-tab-pane>
    </el-tabs>

    <!-- 用户编辑/新增对话框 -->
    <el-dialog
      v-model="userDialogVisible"
      :title="editingUser ? '编辑用户' : '新建用户'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="userFormRef"
        :model="userForm"
        :rules="userFormRules"
        label-width="90px"
      >
        <el-form-item label="用户名" prop="username" v-if="!editingUser">
          <el-input v-model="userForm.username" placeholder="4-20位字符" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!editingUser">
          <el-input v-model="userForm.password" type="password" show-password placeholder="至少8位" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="userForm.nickname" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="userForm.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
            <el-radio :value="0">保密</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="userForm.role">
            <el-radio value="USER">普通用户</el-radio>
            <el-radio value="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="userDialogVisible = false">取消</button>
        <button class="sketch-btn" :disabled="userSubmitting" @click="handleSubmitUser">
          {{ userSubmitting ? '提交中...' : '确认' }}
        </button>
      </template>
    </el-dialog>

    <!-- 模型新建/编辑对话框 -->
    <el-dialog
      v-model="modelDialogVisible"
      :title="editingModel ? '编辑模型' : '新建模型'"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="modelFormRef"
        :model="modelForm"
        :rules="modelFormRules"
        label-width="110px"
      >
        <el-form-item label="模型服务商" prop="modelProvider">
          <el-input v-model="modelForm.modelProvider" placeholder="如 deepseek / seedream" />
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="modelForm.modelName" placeholder="模型显示名称" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="modelForm.modelType" placeholder="选择类型" style="width: 100%">
            <el-option label="文本生成" :value="1" />
            <el-option label="图片生成" :value="2" />
            <el-option label="音频生成" :value="3" />
            <el-option label="视频生成" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="调用协议" prop="protocol">
          <el-select
            v-model="modelForm.protocol"
            placeholder="选择调用协议（不选则按服务商路由）"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="p in protocolOptions"
              :key="p.code"
              :label="`${p.desc}（${p.code}）`"
              :value="p.code"
              :disabled="modelForm.modelType != null && p.supportedTypes != null && !p.supportedTypes.includes(modelForm.modelType)"
            />
          </el-select>
          <div class="form-tip" v-if="currentProtocolDesc">{{ currentProtocolDesc }}</div>
        </el-form-item>
        <el-form-item label="能力声明">
          <el-select
            v-model="modelForm.capabilitiesList"
            placeholder="选择模型能力（可多选）"
            multiple
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="c in capabilityOptions"
              :key="c.code"
              :label="`${c.desc}（${c.code}）`"
              :value="c.code"
            />
          </el-select>
          <div class="form-tip">声明式能力查询，步骤 Handler 按能力路由决策</div>
        </el-form-item>
        <el-form-item label="负载均衡策略">
          <el-select
            v-model="modelForm.selectorStrategy"
            placeholder="选择策略"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="s in selectorStrategyOptions"
              :key="s.code"
              :label="`${s.desc}（${s.code}）`"
              :value="s.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="modelForm.weight" :min="1" :max="1000" :step="10" />
          <div class="form-tip">同类型多模型负载均衡时，值越大被选中概率越高</div>
        </el-form-item>
        <el-form-item label="API地址" prop="apiUrl">
          <el-input v-model="modelForm.apiUrl" placeholder="https://api.example.com/v1" />
        </el-form-item>
        <el-form-item label="API密钥">
          <el-input v-model="modelForm.apiKey" type="password" show-password placeholder="留空则不更新" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="modelForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="modelDialogVisible = false">取消</button>
        <button class="sketch-btn" :disabled="modelSubmitting" @click="handleSubmitModel">
          {{ modelSubmitting ? '保存中...' : '确认' }}
        </button>
      </template>
    </el-dialog>

    <!-- 模板新建/编辑对话框 -->
    <el-dialog
      v-model="templateDialogVisible"
      :title="editingTemplate ? '编辑模板' : '新建模板'"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="templateFormRef"
        :model="templateForm"
        :rules="templateFormRules"
        label-width="110px"
      >
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="templateForm.templateCode" placeholder="如 outline-default" />
        </el-form-item>
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="templateForm.templateName" placeholder="模板显示名称" />
        </el-form-item>
        <el-form-item label="阶段" prop="stage">
          <el-select v-model="templateForm.stage" placeholder="选择阶段" style="width: 100%">
            <el-option label="故事摘要" :value="1" />
            <el-option label="分镜脚本" :value="2" />
            <el-option label="资产设计" :value="3" />
            <el-option label="资产绘图" :value="4" />
            <el-option label="分镜绘图" :value="5" />
            <el-option label="配音合成" :value="6" />
            <el-option label="视频生成" :value="7" />
          </el-select>
        </el-form-item>
        <el-form-item label="提示词" prop="content">
          <el-input v-model="templateForm.content" type="textarea" :rows="5" placeholder="支持变量：{{变量名}}" />
        </el-form-item>
        <el-form-item label="变量列表">
          <el-input v-model="templateForm.variables" placeholder='JSON格式，如 ["topic","style"]' />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="templateForm.description" placeholder="模板用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="templateDialogVisible = false">取消</button>
        <button class="sketch-btn" :disabled="templateSubmitting" @click="handleSubmitTemplate">
          {{ templateSubmitting ? '保存中...' : '确认' }}
        </button>
      </template>
    </el-dialog>

    <!-- 模板版本对话框 -->
    <el-dialog
      v-model="versionDialogVisible"
      title="模板版本历史"
      width="680px"
    >
      <div v-if="versionLoading" style="text-align: center; padding: 20px;">加载中...</div>
      <div v-else-if="versionList.length === 0" style="text-align: center; padding: 20px; color: var(--cd-text-secondary);">暂无版本记录</div>
      <div v-else class="version-list">
        <div v-for="v in versionList" :key="v.id" class="version-item">
          <div class="version-header">
            <span class="version-no">v{{ v.versionNo }}</span>
            <el-tag v-if="v.isCurrent === 1" type="success" effect="light" round size="small">当前版本</el-tag>
            <span class="version-time">{{ fmtTime(v.createTime) }}</span>
          </div>
          <div class="version-content">{{ v.content }}</div>
          <div class="version-footer">
            <span v-if="v.changeLog" class="version-changelog">{{ v.changeLog }}</span>
            <el-button
              v-if="v.isCurrent !== 1"
              link
              type="warning"
              size="small"
              @click="handleRollback(v)"
            >
              回滚到此版本
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="versionDialogVisible = false">关闭</button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Search,
  Plus,
  Warning,
  User,
  Film,
  VideoCamera,
  CircleCheck,
  DataAnalysis,
  Cpu,
  Document,
  Wallet,
  PieChart
} from '@element-plus/icons-vue'
import {
  pageUsers,
  createUser,
  updateUser,
  deleteUser,
  enableUser,
  disableUser,
  getSystemStats,
  pageModels,
  createModel,
  updateModel,
  deleteModel,
  testModel,
  listProtocols,
  listCapabilities,
  listSelectorStrategies,
  pageTemplates,
  createTemplate,
  updateTemplate,
  deleteTemplate,
  listTemplateVersions,
  rollbackTemplate,
  pageUsageLogs,
  getSystemConfigs,
  updateSystemConfig,
  createConfig,
  listBindings,
  createBinding,
  updateBinding,
  clearBinding,
  listActiveModels,
  listAllModels,
  type AdminUserVO,
  type AdminUserPageQuery,
  type AdminUserCreateDTO,
  type AdminUserUpdateDTO,
  type SystemStatsVO,
  type AiModelConfigVO,
  type AiModelConfigPageQuery,
  type ModelProtocolVO,
  type ModelCapabilityVO,
  type SelectorStrategyVO,
  type PromptTemplateVO,
  type PromptTemplatePageQuery,
  type PromptTemplateVersionVO,
  type TokenUsageLogVO,
  type TokenUsageLogPageQuery
} from '@/api/admin'
import { Doodles } from '@/components/illustrations'
import ResourceCenterView from './admin/ResourceCenterView.vue'
import SystemMonitorView from './admin/SystemMonitorView.vue'

const globalLoading = ref(false)
const activeTab = ref('users')

// ===== 全局统计 =====
const stats = reactive<SystemStatsVO>({
  userTotal: 0,
  userActive: 0,
  taskTotal: 0,
  taskRunning: 0,
  taskDone: 0,
  taskFailed: 0,
  workTotal: 0,
  todayNewUsers: 0,
  todayNewTasks: 0
})

const statCards = computed(() => [
  { key: 'userTotal', label: '用户总数', value: stats.userTotal, icon: User, color: 'var(--cd-primary)' },
  { key: 'taskTotal', label: '任务总数', value: stats.taskTotal, icon: Film, color: 'var(--cd-warning)' },
  { key: 'taskRunning', label: '进行中', value: stats.taskRunning, icon: DataAnalysis, color: 'var(--cd-accent)' },
  { key: 'taskDone', label: '已完成', value: stats.taskDone, icon: CircleCheck, color: 'var(--cd-success)' },
  { key: 'workTotal', label: '作品总数', value: stats.workTotal, icon: VideoCamera, color: 'var(--cd-accent)' },
  { key: 'todayNew', label: '今日新增', value: Number(stats.todayNewUsers) + Number(stats.todayNewTasks), icon: Plus, color: 'var(--cd-danger)' }
])

// ===== 用户管理 =====
const userLoading = ref(false)
const userList = ref<AdminUserVO[]>([])
const userTotal = ref(0)
const userQuery = reactive<AdminUserPageQuery>({
  page: 1,
  size: 10,
  keyword: '',
  status: undefined
})

async function loadUsers() {
  userLoading.value = true
  try {
    const res = await pageUsers(userQuery)
    userList.value = res.records || []
    userTotal.value = res.total || 0
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    userLoading.value = false
  }
}

function handleUserSizeChange() {
  userQuery.page = 1
  loadUsers()
}

// 新增/编辑
const userDialogVisible = ref(false)
const userSubmitting = ref(false)
const editingUser = ref<AdminUserVO | null>(null)
const userFormRef = ref<FormInstance>()
const userForm = reactive<AdminUserCreateDTO & AdminUserUpdateDTO>({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  gender: 0,
  role: 'USER'
})
const userFormRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '4-20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '至少8位', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

function openCreateUser() {
  editingUser.value = null
  Object.assign(userForm, {
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    gender: 0,
    role: 'USER'
  })
  userDialogVisible.value = true
}

function openEditUser(row: AdminUserVO) {
  editingUser.value = row
  Object.assign(userForm, {
    username: row.username,
    password: '',
    nickname: row.nickname || '',
    email: row.email || '',
    phone: row.phone || '',
    gender: row.gender || 0,
    role: row.role || 'USER'
  })
  userDialogVisible.value = true
}

async function handleSubmitUser() {
  if (!userFormRef.value) return
  await userFormRef.value.validate(async (valid) => {
    if (!valid) return
    userSubmitting.value = true
    try {
      if (editingUser.value) {
        const dto: AdminUserUpdateDTO = {
          nickname: userForm.nickname,
          email: userForm.email,
          phone: userForm.phone,
          gender: userForm.gender,
          role: userForm.role
        }
        await updateUser(editingUser.value.id, dto)
        ElMessage.success('用户信息已更新')
      } else {
        const dto: AdminUserCreateDTO = {
          username: userForm.username,
          password: userForm.password,
          nickname: userForm.nickname,
          email: userForm.email,
          phone: userForm.phone,
          gender: userForm.gender,
          role: userForm.role
        }
        await createUser(dto)
        ElMessage.success('用户创建成功')
      }
      userDialogVisible.value = false
      loadUsers()
    } catch (e) {
      /* 拦截器已提示 */
    } finally {
      userSubmitting.value = false
    }
  })
}

async function handleDisable(row: AdminUserVO) {
  await ElMessageBox.confirm(`确定禁用用户「${row.nickname || row.username}」吗？`, '禁用用户', {
    type: 'warning'
  })
    .then(async () => {
      await disableUser(row.id)
      ElMessage.success('用户已禁用')
      loadUsers()
    })
    .catch(() => {})
}

async function handleEnable(row: AdminUserVO) {
  await enableUser(row.id)
  ElMessage.success('用户已启用')
  loadUsers()
}

async function handleDeleteUser(row: AdminUserVO) {
  await ElMessageBox.confirm(
    `确定删除用户「${row.nickname || row.username}」吗？此操作不可恢复。`,
    '删除用户',
    { type: 'error', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
  )
    .then(async () => {
      await deleteUser(row.id)
      ElMessage.success('用户已删除')
      loadUsers()
    })
    .catch(() => {})
}

// ===== 系统设置 =====
interface ModelConfig {
  stepCode: string
  stepName: string
  stepOrder: number
  modelConfigId: number | null
  bindingId?: number
  options: { value: number; label: string }[]
}

// 8步工作流对应的模型绑定（步骤9为算法处理，不需要模型）
const STEP_BINDING_DEFS = [
  { stepCode: 'SUMMARY', stepOrder: 1, stepName: '故事摘要', desc: '步骤1：故事摘要生成', modelType: 1 },
  { stepCode: 'STORYBOARD', stepOrder: 2, stepName: '分镜脚本', desc: '步骤2：分镜脚本生成', modelType: 1 },
  { stepCode: 'ASSET_DESIGN', stepOrder: 3, stepName: '资产设计', desc: '步骤3：人物/场景/道具/音色设计', modelType: 1 },
  { stepCode: 'ASSET_IMAGE', stepOrder: 4, stepName: '资产绘图', desc: '步骤4：资产绘图（文生图）', modelType: 2 },
  { stepCode: 'ASSET_DERIVE', stepOrder: 5, stepName: '衍生绘图', desc: '步骤5：衍生绘图（图生图）', modelType: 2 },
  { stepCode: 'STORYBOARD_IMAGE', stepOrder: 6, stepName: '分镜绘图', desc: '步骤6：分镜画面生成', modelType: 2 },
  { stepCode: 'AUDIO', stepOrder: 7, stepName: '配音合成', desc: '步骤7：角色配音合成', modelType: 3 },
  { stepCode: 'VIDEO', stepOrder: 8, stepName: '视频生成', desc: '步骤8：场景视频生成', modelType: 4 }
]

const modelConfigs = ref<ModelConfig[]>([])

// 从数据库加载模型和步骤绑定
async function loadModelConfigs() {
  try {
    // 1. 加载启用的 AI 模型（只显示 status=1 的模型）
    const activeModels: any[] = await listActiveModels()

    // 按类型分组
    const modelsByType: Record<number, any[]> = { 1: [], 2: [], 3: [], 4: [] }
    for (const m of activeModels) {
      if (modelsByType[m.modelType]) {
        modelsByType[m.modelType].push(m)
      }
    }

    // 2. 加载步骤-模型绑定
    const bindings: any[] = await listBindings()
    const bindingMap: Record<string, any> = {}
    for (const b of bindings) {
      bindingMap[b.stepCode] = b
    }

    // 3. 构建配置列表（只显示启用的模型选项）
    const configs: ModelConfig[] = STEP_BINDING_DEFS.map(def => {
      const availableModels = modelsByType[def.modelType] || []
      const options = availableModels.map(m => {
        return {
          value: m.id,
          label: `${m.modelName} (${m.modelProvider})`
        }
      })

      const existingBinding = bindingMap[def.stepCode]
      const currentModelId = existingBinding?.modelConfigId ?? null

      return {
        stepCode: def.stepCode,
        stepName: def.stepName,
        stepOrder: def.stepOrder,
        modelConfigId: currentModelId,
        bindingId: existingBinding?.id,
        options
      }
    })

    modelConfigs.value = configs
  } catch (e) {
    console.error('[ADMIN] 加载模型配置失败:', e)
    modelConfigs.value = getDefaultConfigs()
  }
}

function getDefaultConfigs(): ModelConfig[] {
  return STEP_BINDING_DEFS.map(def => ({
    stepCode: def.stepCode,
    stepName: def.stepName,
    stepOrder: def.stepOrder,
    modelConfigId: null,
    options: []
  }))
}

function getStepDesc(stepCode: string): string {
  const def = STEP_BINDING_DEFS.find(d => d.stepCode === stepCode)
  return def?.desc || ''
}

interface PlatformConfig {
  key: string
  label: string
  desc: string
  value: number
  min: number
  max: number
  step: number
  configId?: number
}

const platformConfigs = ref<PlatformConfig[]>([])

const PLATFORM_CONFIG_DEFS = [
  { key: 'max_video_duration', label: '单集最大时长(秒)', desc: '单个任务允许生成的最大视频时长', min: 30, max: 1800, step: 30 },
  { key: 'daily_task_quota', label: '每日任务配额', desc: '每个用户每日可提交的任务数量', min: 1, max: 200, step: 1 },
  { key: 'global_ai_concurrency_limit', label: 'AI并发上限', desc: '全局AI接口最大并发请求数', min: 1, max: 100, step: 1 },
  { key: 'single_user_task_concurrency', label: '单用户并发上限', desc: '单用户同时执行的任务数量上限', min: 1, max: 20, step: 1 },
  { key: 'task_timeout_minutes', label: '任务超时(分钟)', desc: '单个任务最大执行时间，超时自动终止', min: 10, max: 360, step: 10 },
  { key: 'task_max_retry_times', label: '节点重试次数', desc: '单个节点失败后的最大自动重试次数', min: 0, max: 10, step: 1 },
  { key: 'ai_timeout', label: 'AI调用超时(秒)', desc: '单次AI模型调用最大等待时间', min: 5, max: 600, step: 5 },
  { key: 'ai_retry_times', label: 'AI重试次数', desc: 'AI调用失败后的自动重试次数', min: 0, max: 5, step: 1 },
  { key: 'ai_max_concurrency', label: 'AI最大并发数', desc: '系统同时进行的AI请求最大并发数', min: 1, max: 50, step: 1 },
  { key: 'resource_retention_days', label: '资源保留天数', desc: '过期任务资源自动清理的天数', min: 1, max: 365, step: 1 },
  { key: 'queue_high_peak_threshold', label: '排队高峰阈值', desc: '排队等待数超过此值触发高峰提示', min: 10, max: 500, step: 10 },
  { key: 'websocket_heartbeat_interval', label: '心跳间隔(秒)', desc: 'WebSocket心跳检测间隔', min: 5, max: 120, step: 5 },
  { key: 'websocket_reconnect_max', label: '重连上限', desc: 'WebSocket断线最大重连次数', min: 1, max: 20, step: 1 }
]

async function loadPlatformConfigs() {
  try {
    const configRes = await getSystemConfigs()
    const configMap: Record<string, { id?: number; value: string }> = {}
    for (const c of configRes) {
      configMap[c.configKey] = { id: c.id, value: c.configValue }
    }
    platformConfigs.value = PLATFORM_CONFIG_DEFS.map(def => {
      const saved = configMap[def.key]
      return {
        key: def.key,
        label: def.label,
        desc: def.desc,
        value: saved ? parseInt(saved.value) : getDefaultPlatformValue(def.key),
        min: def.min,
        max: def.max,
        step: def.step,
        configId: saved?.id
      }
    })
  } catch (e) {
    console.error('[ADMIN] 加载平台参数失败:', e)
    platformConfigs.value = PLATFORM_CONFIG_DEFS.map(def => ({
      ...def,
      value: getDefaultPlatformValue(def.key)
    }))
  }
}

function getDefaultPlatformValue(key: string): number {
  const defaults: Record<string, number> = {
    'max_video_duration': 300,
    'daily_task_quota': 20,
    'global_ai_concurrency_limit': 20,
    'single_user_task_concurrency': 2,
    'task_timeout_minutes': 60,
    'task_max_retry_times': 3,
    'ai_timeout': 120,
    'ai_retry_times': 3,
    'ai_max_concurrency': 10,
    'resource_retention_days': 30,
    'queue_high_peak_threshold': 50,
    'websocket_heartbeat_interval': 30,
    'websocket_reconnect_max': 5
  }
  return defaults[key] ?? 10
}

function savePlatformConfigs() {
  const promises = platformConfigs.value.map(config => {
    const def = PLATFORM_CONFIG_DEFS.find(d => d.key === config.key)
    const payload: any = {
      configKey: config.key,
      configValue: String(config.value),
      configName: def?.label || config.key,
      description: def?.desc || '',
      valueType: 2,
      status: 1
    }
    if (config.configId != null) {
      return updateSystemConfig({ id: config.configId, ...payload })
    } else {
      return createConfig(payload)
    }
  })
  Promise.all(promises).then(async () => {
    ElMessage.success('平台参数已保存')
    await loadPlatformConfigs()
  }).catch(() => {
    ElMessage.error('部分配置保存失败')
  })
}

function saveModelConfigs() {
  const errors: string[] = []
  const promises = modelConfigs.value.map(async config => {
    try {
      if (config.modelConfigId == null) {
        // 清除配置：如果有绑定则清空
        if (config.bindingId != null) {
          await clearBinding(config.bindingId)
        }
        return
      }
      const def = STEP_BINDING_DEFS.find(d => d.stepCode === config.stepCode)
      const payload: any = {
        stepCode: config.stepCode,
        stepName: config.stepName,
        stepOrder: config.stepOrder,
        modelConfigId: config.modelConfigId,
        modelType: def?.modelType
      }
      if (config.bindingId != null) {
        await updateBinding(config.bindingId, payload)
      } else {
        await createBinding(payload)
      }
    } catch (e: any) {
      const msg = e?.message || '未知错误'
      const stepName = config.stepName
      errors.push(`${stepName}：${msg}`)
    }
  })
  Promise.all(promises).then(async () => {
    if (errors.length > 0) {
      ElMessage.error(`配置保存失败：${errors.join('；')}`)
    } else {
      ElMessage.success('模型配置已保存')
    }
    await loadModelConfigs()
  })
}

async function handleClearTasks() {
  // 已移除：危险操作功能已禁用
}

async function handleResetConfig() {
  // 已移除：危险操作功能已禁用
}

// ===== 枚举映射 =====
const modelTypeMap: Record<number, string> = { 1: '文本生成', 2: '图片生成', 3: '音频生成', 4: '视频生成' }
const modelTypeTagMap: Record<number, 'primary' | 'success' | 'warning' | 'info'> = {
  1: 'primary', 2: 'success', 3: 'warning', 4: 'info'
}
function modelTypeText(t?: number) { return t ? modelTypeMap[t] || '-' : '-' }
function modelTypeTag(t?: number) { return t ? modelTypeTagMap[t] || 'primary' : 'info' }

const stageMap: Record<number, string> = { 1: '故事摘要', 2: '分镜脚本', 3: '资产设计', 4: '资产绘图', 5: '分镜绘图', 6: '配音合成', 7: '视频生成' }
const stageTagMap: Record<number, 'primary' | 'success' | 'info' | 'warning' | 'danger'> = {
  1: 'primary', 2: 'success', 3: 'warning', 4: 'danger', 5: 'info', 6: 'primary', 7: 'success'
}
function stageText(s?: number) { return s ? stageMap[s] || '-' : '-' }
function stageTag(s?: number) { return s ? stageTagMap[s] || 'primary' : 'info' }

// ===== 模型配置 =====
const modelLoading = ref(false)
const modelList = ref<AiModelConfigVO[]>([])
const modelTotal = ref(0)
const modelQuery = reactive<AiModelConfigPageQuery>({
  page: 1, size: 10, keyword: '', modelType: undefined, status: undefined
})

async function loadModels() {
  modelLoading.value = true
  try {
    const res = await pageModels(modelQuery)
    if (res && Array.isArray(res.records)) {
      modelList.value = res.records
      modelTotal.value = res.total || 0
    } else {
      console.warn('[ADMIN] 模型配置响应格式异常:', res)
      modelList.value = []
      modelTotal.value = 0
    }
  } catch (e: any) {
    console.error('[ADMIN] 加载模型配置失败:', e?.message || e)
    modelList.value = []
    modelTotal.value = 0
  } finally { modelLoading.value = false }
}
function handleModelSizeChange() { modelQuery.page = 1; loadModels() }

const modelDialogVisible = ref(false)
const modelSubmitting = ref(false)
const editingModel = ref<AiModelConfigVO | null>(null)
const modelFormRef = ref<FormInstance>()

// 模型表单（capabilitiesList 为前端多选临时字段，提交时序列化为 capabilities JSON 字符串）
const modelForm = reactive<{
  modelProvider?: string
  modelName?: string
  modelType?: number
  protocol?: string
  capabilitiesList?: string[]
  selectorStrategy?: string
  weight?: number
  apiUrl?: string
  apiKey?: string
  status?: number
}>({
  modelProvider: '', modelName: '', modelType: 1, protocol: '', capabilitiesList: [],
  selectorStrategy: 'WEIGHTED_RANDOM', weight: 100, apiUrl: '', apiKey: '', status: 1
})

const modelFormRules: FormRules = {
  modelProvider: [{ required: true, message: '请输入模型服务商', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
  apiUrl: [{ required: true, message: '请输入API地址', trigger: 'blur' }]
}

// 协议/能力/策略选项
const protocolOptions = ref<ModelProtocolVO[]>([])
const capabilityOptions = ref<(ModelCapabilityVO & { code: string })[]>([])
const selectorStrategyOptions = ref<SelectorStrategyVO[]>([])
const protocolEnumLoaded = ref(false)

async function loadProtocolEnums() {
  if (protocolEnumLoaded.value) return
  try {
    const [protocols, capabilities, strategies] = await Promise.all([
      listProtocols(),
      listCapabilities(),
      listSelectorStrategies()
    ])
    protocolOptions.value = protocols || []
    // 后端 ModelCapability 序列化为 {code, desc, description} 对象
    capabilityOptions.value = (capabilities || []) as (ModelCapabilityVO & { code: string })[]
    selectorStrategyOptions.value = strategies || []
    protocolEnumLoaded.value = true
  } catch (e) {
    console.error('[ADMIN] 加载协议枚举失败:', e)
  }
}

// 协议短名映射（表格显示用）
const PROTOCOL_SHORT_MAP: Record<string, string> = {
  'openai-chat': 'OpenAI对话',
  'modelscope-image': '魔搭图像',
  'ark-image': 'Ark图像',
  'ark-tts': 'Ark语音',
  'ark-video': 'Ark视频',
  'custom-http': '自定义HTTP'
}
function protocolShortText(code: string): string {
  if (!code) return '-'
  if (code.startsWith('custom-http')) return '自定义HTTP'
  return PROTOCOL_SHORT_MAP[code] || code
}

// 当前选中协议的说明（表单提示）
const currentProtocolDesc = computed(() => {
  if (!modelForm.protocol) return ''
  const p = protocolOptions.value.find(x => x.code === modelForm.protocol)
  return p?.description || ''
})

function openCreateModel() {
  editingModel.value = null
  Object.assign(modelForm, {
    modelProvider: '', modelName: '', modelType: 1, protocol: '', capabilitiesList: [],
    selectorStrategy: 'WEIGHTED_RANDOM', weight: 100, apiUrl: '', apiKey: '', status: 1
  })
  loadProtocolEnums()
  modelDialogVisible.value = true
}
function openEditModel(row: AiModelConfigVO) {
  editingModel.value = row
  // 解析 capabilities JSON 字符串为数组（供多选组件使用）
  let capList: string[] = []
  if (row.capabilities) {
    try {
      const parsed = JSON.parse(row.capabilities)
      if (Array.isArray(parsed)) capList = parsed.map(String)
    } catch (_) { /* 容错：非 JSON 格式忽略 */ }
  }
  Object.assign(modelForm, {
    modelProvider: row.modelProvider,
    modelName: row.modelName,
    modelType: row.modelType,
    protocol: row.protocol || '',
    capabilitiesList: capList,
    selectorStrategy: row.selectorStrategy || 'WEIGHTED_RANDOM',
    weight: row.weight ?? 100,
    apiUrl: row.apiUrl,
    apiKey: '',
    status: row.status ?? 1
  })
  loadProtocolEnums()
  modelDialogVisible.value = true
}
async function handleSubmitModel() {
  if (!modelFormRef.value) return
  await modelFormRef.value.validate(async (valid) => {
    if (!valid) return
    modelSubmitting.value = true
    try {
      // capabilitiesList 序列化为 JSON 字符串
      const capabilitiesJson = (modelForm.capabilitiesList && modelForm.capabilitiesList.length > 0)
        ? JSON.stringify(modelForm.capabilitiesList)
        : null
      if (editingModel.value) {
        // 编辑模式：构造 payload，apiKey 为空时不传（避免覆盖原密钥）
        const payload: any = {
          modelProvider: modelForm.modelProvider,
          modelName: modelForm.modelName,
          modelType: modelForm.modelType,
          protocol: modelForm.protocol || null,
          capabilities: capabilitiesJson,
          selectorStrategy: modelForm.selectorStrategy || null,
          weight: modelForm.weight,
          apiUrl: modelForm.apiUrl,
          status: modelForm.status
        }
        if (modelForm.apiKey) {
          payload.apiKey = modelForm.apiKey
        }
        await updateModel(editingModel.value.id!, payload)
        if (modelForm.status === 0 && editingModel.value.status === 1) {
          ElMessage.success('模型已禁用，相关绑定已自动清除')
        } else {
          ElMessage.success('模型已更新')
        }
      } else {
        const payload: any = {
          modelProvider: modelForm.modelProvider,
          modelName: modelForm.modelName,
          modelType: modelForm.modelType,
          protocol: modelForm.protocol || null,
          capabilities: capabilitiesJson,
          selectorStrategy: modelForm.selectorStrategy || null,
          weight: modelForm.weight,
          apiUrl: modelForm.apiUrl,
          status: modelForm.status
        }
        if (modelForm.apiKey) {
          payload.apiKey = modelForm.apiKey
        }
        await createModel(payload)
        ElMessage.success('模型已创建')
      }
      modelDialogVisible.value = false
      loadModels()
      loadModelConfigs()
    } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
    finally { modelSubmitting.value = false }
  })
}
async function handleDeleteModel(row: AiModelConfigVO) {
  try {
    await ElMessageBox.confirm(`确定删除模型「${row.modelName}」吗？删除后将自动清除所有引用此模型的绑定。`, '删除模型', { type: 'error' })
    const res: any = await deleteModel(row.id!)
    // 后端已自动清理关联绑定
    ElMessage.success(res?.message || `模型「${row.modelName}」已删除，相关绑定已自动清除`)
    loadModels()
    loadModelConfigs()
  } catch (_) { /* 用户取消 */ }
}

async function handleTestModel(row: AiModelConfigVO) {
  try {
    ElMessage.info('正在测试模型连接...')
    const result = await testModel(row.id!)
    if (result.success) {
      ElMessage.success(`✅ 连接成功！延迟 ${result.latencyMs ?? '-'}ms`)
    } else {
      ElMessage.error(`❌ ${result.message}`)
    }
  } catch (e: any) {
    ElMessage.error(`测试失败：${e?.message || '未知错误'}`)
  }
}

// ===== 提示词模板 =====
const templateLoading = ref(false)
const templateList = ref<PromptTemplateVO[]>([])
const templateTotal = ref(0)
const templateQuery = reactive<PromptTemplatePageQuery>({
  page: 1, size: 10, keyword: '', stage: undefined
})

async function loadTemplates() {
  templateLoading.value = true
  try {
    const res = await pageTemplates(templateQuery)
    templateList.value = res.records || []
    templateTotal.value = res.total || 0
  } catch (e) { /* 拦截器已提示 */ }
  finally { templateLoading.value = false }
}
function handleTemplateSizeChange() { templateQuery.page = 1; loadTemplates() }

const templateDialogVisible = ref(false)
const templateSubmitting = ref(false)
const editingTemplate = ref<PromptTemplateVO | null>(null)
const templateFormRef = ref<FormInstance>()
const templateForm = reactive<Partial<PromptTemplateVO>>({
  templateCode: '', templateName: '', stage: 1, content: '', variables: '', description: ''
})
const templateFormRules: FormRules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  stage: [{ required: true, message: '请选择阶段', trigger: 'change' }],
  content: [{ required: true, message: '请输入提示词内容', trigger: 'blur' }]
}

function openCreateTemplate() {
  editingTemplate.value = null
  Object.assign(templateForm, { templateCode: '', templateName: '', stage: 1, content: '', variables: '', description: '' })
  templateDialogVisible.value = true
}
function openEditTemplate(row: PromptTemplateVO) {
  editingTemplate.value = row
  Object.assign(templateForm, {
    templateCode: row.templateCode, templateName: row.templateName, stage: row.stage,
    content: row.content, variables: row.variables || '', description: row.description || ''
  })
  templateDialogVisible.value = true
}
async function handleSubmitTemplate() {
  if (!templateFormRef.value) return
  await templateFormRef.value.validate(async (valid) => {
    if (!valid) return
    templateSubmitting.value = true
    try {
      if (editingTemplate.value) {
        await updateTemplate(editingTemplate.value.id!, templateForm)
        ElMessage.success('模板已更新')
      } else {
        await createTemplate(templateForm)
        ElMessage.success('模板已创建')
      }
      templateDialogVisible.value = false
      loadTemplates()
    } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
    finally { templateSubmitting.value = false }
  })
}
async function handleDeleteTemplate(row: PromptTemplateVO) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.templateName}」吗？`, '删除模板', { type: 'error' })
    await deleteTemplate(row.id!)
    ElMessage.success('模板已删除')
    loadTemplates()
  } catch (_) { /* 用户取消或请求失败 */ }
}

// ===== 模板版本 =====
const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const versionList = ref<PromptTemplateVersionVO[]>([])
const currentVersionTemplateId = ref<number>(0)

async function openVersions(row: PromptTemplateVO) {
  currentVersionTemplateId.value = row.id!
  versionDialogVisible.value = true
  versionLoading.value = true
  try {
    const res = await listTemplateVersions(row.id!)
    versionList.value = res || []
  } catch (e) { /* 拦截器已提示 */ }
  finally { versionLoading.value = false }
}

async function handleRollback(v: PromptTemplateVersionVO) {
  await ElMessageBox.confirm(`确定回滚到版本 v${v.versionNo} 吗？`, '回滚确认', { type: 'warning' })
    .then(async () => {
      await rollbackTemplate(currentVersionTemplateId.value, v.versionNo)
      ElMessage.success('回滚成功')
      openVersions({ id: currentVersionTemplateId.value } as PromptTemplateVO)
      loadTemplates()
    })
    .catch(() => {})
}

// ===== 用量统计 =====
const usageLoading = ref(false)
const usageList = ref<TokenUsageLogVO[]>([])
const usageTotal = ref(0)
const usageQuery = reactive<TokenUsageLogPageQuery>({
  page: 1, size: 20, keyword: '', modelName: undefined, modelType: undefined
})
const modelNameOptions = ref<string[]>([])

async function loadUsageLogs() {
  usageLoading.value = true
  try {
    const res = await pageUsageLogs(usageQuery)
    usageList.value = res.records || []
    usageTotal.value = res.total || 0
    if (!modelNameOptions.value.length) {
      const codes = new Set<string>()
      ;(res.records || []).forEach((item: TokenUsageLogVO) => {
        if (item.modelName) codes.add(item.modelName)
      })
      modelNameOptions.value = Array.from(codes)
    }
  } catch (e) { /* 拦截器已提示 */ }
  finally { usageLoading.value = false }
}
function handleUsageSizeChange() { usageQuery.page = 1; loadUsageLogs() }

// ===== 工具函数 =====
function fmtTime(t?: string) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

// ===== 初始化 =====
async function loadStats() {
  try {
    const raw = await getSystemStats() as unknown as Record<string, unknown>
    // Long 类型被序列化为字符串（避免 JS 精度丢失），转换为数值
    const s: Record<string, unknown> = {}
    Object.keys(raw).forEach(k => {
      const v = raw[k]
      if (typeof v === 'string' && v !== '' && !isNaN(Number(v))) {
        s[k] = Number(v)
      } else {
        s[k] = v
      }
    })
    Object.assign(stats, s as Partial<typeof stats>)
  } catch (e) {
    /* 拦截器已提示 */
  }
}

/** 每个 Tab 的加载映射（已加载的不重复请求，实现懒加载） */
const tabLoaded: Record<string, boolean> = { users: true }

async function loadTabData(tab: string, force = false) {
  if (force || !tabLoaded[tab]) {
    switch (tab) {
      case 'users':
        tabLoaded[tab] = true
        await loadUsers()
        break
      case 'config':
        tabLoaded[tab] = true
        await Promise.all([loadModelConfigs(), loadPlatformConfigs()])
        break
      case 'models':
        tabLoaded[tab] = true
        await loadModels()
        break
      case 'templates':
        tabLoaded[tab] = true
        await loadTemplates()
        break
      case 'usage':
        tabLoaded[tab] = true
        await loadUsageLogs()
        break
    }
  }
}

/* 切换 Tab 时懒加载对应数据 */
watch(activeTab, (newTab) => {
  loadTabData(newTab, false)
})

onMounted(async () => {
  globalLoading.value = true
  try {
    await Promise.all([loadStats(), loadUsers(), loadModelConfigs()])
    /* 进入页面时如果当前 Tab 不是 users（浏览器刷新后），立即加载对应 Tab 数据 */
    if (activeTab.value && activeTab.value !== 'users') {
      await loadTabData(activeTab.value, false)
    }
  } finally {
    globalLoading.value = false
  }
})
</script>

<style scoped>
.admin {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ===== 统计卡片 ===== */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

.stat-card {
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  flex-shrink: 0;
  box-shadow: 2px 2px 0 0 var(--cd-shadow);
}

.stat-value {
  font-size: 24px;
  font-weight: 800;
  color: var(--cd-text);
  line-height: 1;
}

.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

/* ===== Tabs ===== */
.admin-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  font-weight: 600;
}

/* ===== 筛选栏 ===== */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.search-input {
  width: 280px;
}

.status-select {
  width: 140px;
}

/* ===== 表格 ===== */
.table-card {
  padding: 8px 18px 18px;
  overflow: hidden;
}

.table-card :deep(.el-table) {
  --el-table-header-bg-color: var(--cd-bg-soft);
  --el-table-row-hover-bg-color: var(--cd-bg-soft);
  --el-table-border-color: var(--cd-border);
  border-radius: 8px;
  overflow: hidden;
}

.table-card :deep(.el-table th.el-table__cell) {
  font-weight: 600;
  color: var(--cd-text);
  font-size: 13px;
  padding: 12px 0;
}

.table-card :deep(.el-table td.el-table__cell) {
  padding: 12px 0;
}

.table-card :deep(.el-table__body td) {
  font-size: 13px;
}

.cell-user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cell-avatar {
  background-color: var(--cd-primary);
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
  border: 2px solid var(--cd-bg-card);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}

.cell-user-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.cell-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cell-name {
  font-weight: 600;
  color: var(--cd-text);
  font-size: 14px;
}

.cell-id {
  font-size: 12px;
  color: var(--cd-text-secondary);
  font-weight: 400;
  padding: 1px 6px;
  background-color: var(--cd-bg-soft);
  border-radius: 4px;
  line-height: 1.4;
}

.cell-sub {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-btns {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.role-tag {
  margin-right: 4px;
  /* 覆盖 Element Plus 默认白色背景，边框/文字由 type 决定（与状态标签一致） */
  background: transparent !important;
  --el-fill-color-lighter: transparent;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ===== 配置卡片 ===== */
.config-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.config-card {
  padding: 22px;
}

.config-card.danger-zone {
  grid-column: 1 / -1;
  border-color: var(--cd-danger);
}

.config-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1.5px dashed var(--cd-border);
}

.config-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: var(--cd-text);
}

.danger-icon {
  color: var(--cd-danger);
  font-size: 20px;
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.config-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border: 1.5px solid var(--cd-border);
  border-radius: 8px;
  background-color: var(--cd-bg-soft);
}

.config-key {
  font-weight: 600;
  color: var(--cd-text);
  font-size: 14px;
}

.config-desc {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 3px;
}

.config-select {
  width: 180px;
}

.config-actions {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1.5px dashed var(--cd-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.config-hint {
  font-size: 12px;
  color: var(--cd-text-secondary);
  font-style: italic;
}

/* ===== 危险区域 ===== */
.danger-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.danger-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border: 1.5px solid var(--cd-danger);
  border-radius: 8px;
  background-color: var(--cd-bg-soft);
}

.danger-title {
  font-weight: 600;
  color: var(--cd-text);
  font-size: 14px;
}

.danger-desc {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 2px;
}

@media (max-width: 1280px) {
  .stat-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .config-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .filter-left {
    flex-direction: column;
    align-items: stretch;
  }
  .search-input,
  .status-select {
    width: 100%;
  }
}

/* ===== 版本对话框 ===== */
.version-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 420px;
  overflow-y: auto;
}

.version-item {
  padding: 14px;
  border: 1.5px solid var(--cd-border);
  border-radius: 10px;
  background-color: var(--cd-bg-soft);
}

.version-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.version-no {
  font-weight: 700;
  color: var(--cd-primary);
  font-size: 14px;
}

.version-time {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-left: auto;
}

.version-content {
  font-size: 13px;
  color: var(--cd-text);
  line-height: 1.5;
  padding: 8px 10px;
  background-color: var(--cd-bg-card);
  border-radius: 6px;
  border: 1px dashed var(--cd-border);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 100px;
  overflow-y: auto;
}

.version-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.version-changelog {
  font-size: 12px;
  color: var(--cd-text-secondary);
}

/* ===== 模型表单提示 ===== */
.form-tip {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 4px;
  line-height: 1.4;
}

.text-secondary {
  font-size: 12px;
  color: var(--cd-text-secondary);
}
</style>