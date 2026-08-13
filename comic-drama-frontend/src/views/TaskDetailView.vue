<template>
  <div class="task-detail" v-loading="loading">
    <!-- 顶部信息卡 -->
    <div class="top-card sketch-card">
      <div class="top-head">
        <div class="head-left">
          <button class="back-btn" @click="router.back()">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </button>
          <div class="title-block">
            <h2 class="title">{{ detail.title || '未命名任务' }}</h2>
            <div class="sub-row">
              <span class="task-no">{{ detail.taskNo }}</span>
              <el-tag :type="statusMeta(detail.status).type" effect="light" round>
                {{ detail.statusText || statusMeta(detail.status).label }}
              </el-tag>
              <span class="sub-item">剧情时长：{{ detail.duration }}s</span>
              <span class="sub-item">画幅比例：{{ detail.aspectRatio }}</span>
              <span class="sub-item">分辨率：{{ detail.resolution }}</span>
              <span class="sub-item" v-if="detail.voiceEnabled">🎙 AI 配音</span>
              <span class="sub-item sub-item--muted" v-else>🔇 配音已关闭</span>
              <span class="sub-item" v-if="styleDisplay()">画风+风格：{{ styleDisplay() }}</span>
            </div>
          </div>
        </div>
        <div class="head-right">
          <el-button
            v-if="detail.status === TaskStatus.QUEUE || detail.status === TaskStatus.RUNNING"
            size="small"
            @click="handlePause"
          >
            暂停
          </el-button>
          <el-button
            v-if="detail.status === TaskStatus.PAUSED && !detail.pendingReview"
            size="small"
            type="success"
            @click="handleResume"
          >
            继续
          </el-button>
          <el-button
            v-if="detail.status === TaskStatus.FAILED"
            size="small"
            type="primary"
            @click="handleRetry"
          >
            重试
          </el-button>
          <el-button size="small" type="danger" plain @click="handleDelete">删除</el-button>
        </div>
      </div>

      <!-- 总体进度 -->
      <div class="progress-row">
        <el-progress
          :percentage="progressTotal"
          :stroke-width="10"
          :color="`var(${statusMeta(detail.status).colorVar})`"
          :status="progressStatus"
        />
        <div class="time-row">
          <span>创建：{{ fmtTime(detail.createTime) }}</span>
          <span v-if="detail.startTime">开始：{{ fmtTime(detail.startTime) }}</span>
          <span v-if="detail.endTime">结束：{{ fmtTime(detail.endTime) }}</span>
          <span v-if="detail.totalConsumeTime">耗时：{{ detail.totalConsumeTime }}s</span>
        </div>
      </div>

      <!-- 9 步节点 -->
      <div class="step-line">
        <div
          v-for="s in 9"
          :key="s"
          class="step-node"
          :class="stepClass(s)"
        >
          <div class="step-index">{{ s }}</div>
          <div class="step-label">
            {{ stepName(s) }}
            <span v-if="isStepSkippedInDetail(s)" class="skipped-label">跳过</span>
            <span v-if="isStepFailed(s)" class="failed-label">失败</span>
            <span v-if="isStepPaused(s)" class="paused-label">暂停</span>
            <span v-if="isStepTesting(s)" class="sub-state-label test-label">进行中</span>
            <span v-if="isStepBatching(s)" class="sub-state-label batch-label">批量中</span>
          </div>
          <div v-if="s < 9" class="step-line-bar" :class="stepLineClass(s)" />
        </div>
      </div>

      <!-- 装饰插画 -->
      <div class="deco">
        <Doodles :size="42" :rotate="8" type="stars" :opacity="0.55" />
      </div>

      <!-- 失败摘要（仅在失败时显示） -->
      <div v-if="detail.status === TaskStatus.FAILED" class="fail-bar">
        <div class="fail-summary" @click="showFailDetail = !showFailDetail">
          <el-icon class="fail-icon"><component :is="Warning" /></el-icon>
          <div class="fail-summary-text">
            <span class="fail-label">任务失败</span>
            <span class="fail-step" v-if="detail.failureStep">
              步骤{{ detail.failureStep }} · {{ stepName(detail.failureStep) }}
            </span>
            <span class="fail-reason">{{ detail.failureReason || '未知错误' }}</span>
          </div>
          <el-button
            v-if="detail.failureLogs?.length"
            size="small"
            type="danger"
            plain
            @click.stop="handleClearFailureLogs"
          >
            清空日志
          </el-button>
          <el-icon class="fail-toggle" :class="{ expanded: showFailDetail }">
            <component :is="ArrowDown" />
          </el-icon>
        </div>
        <div v-if="showFailDetail" class="fail-detail">
          <div v-if="detail.failureLogs?.length" class="fail-logs">
            <div v-for="f in detail.failureLogs" :key="f.id" class="fail-log-item">
              <div class="fail-log-head">
                <span class="fail-log-step">{{ f.stepName || `步骤${f.step}` }}</span>
                <span class="fail-log-time">{{ fmtTime(f.createTime) }}</span>
              </div>
              <p class="fail-log-msg">{{ f.errorMessage }}</p>
              <pre v-if="f.errorStack" class="fail-log-stack">{{ f.errorStack }}</pre>
            </div>
          </div>
          <pre v-else-if="detail.failureDetail" class="fail-stack">{{ detail.failureDetail }}</pre>
        </div>
      </div>
    </div>

    <!-- 主体区 -->
    <div class="main-grid">
      <!-- 左栏：产物内容 -->
      <div class="left-col">
        <!-- 审核暂停横幅：人工审核模式下当前步骤完成等待审核 -->
        <div v-if="detail.pendingReview" class="review-banner">
          <div class="review-banner-left">
            <el-icon class="review-icon"><InfoFilled /></el-icon>
            <div class="review-text">
              <div class="review-title">步骤 {{ detail.currentStep }}「{{ stepName(detail.currentStep) }}」已完成，等待人工审核</div>
              <div class="review-tip">审核通过后执行下一步；不满意可点击对应产物的「重新生成」</div>
            </div>
          </div>
          <div class="review-banner-actions">
            <el-button type="primary" plain size="small"
                       :disabled="!canRegenerate"
                       :title="regenerateDisabledHint"
                       @click="handleRegenerateStepReview">重新生成</el-button>
            <el-button type="success" @click="handleApprove">执行下一步</el-button>
          </div>
        </div>

        <!-- 故事大纲 -->
        <div v-if="detail.outline" class="sketch-card prod-card">
          <div class="card-head">
            <div class="card-head-left">
              <h3>① 故事大纲</h3>
              <el-tag size="small" type="info" effect="plain" v-if="detail.outline.wordCount">
                {{ detail.outline.wordCount }} 字
              </el-tag>
            </div>
            <div class="card-head-actions">
              <el-button
                v-if="detail.outline.summary && detail.outline.outlineText && detail.outline.summary !== detail.outline.outlineText"
                size="small"
                text
                type="primary"
                @click="outlineExpanded = !outlineExpanded"
              >{{ outlineExpanded ? '收起' : '展开' }}</el-button>
              <el-button size="small" text type="primary"
                         v-if="showStepControls"
                         :disabled="!canRegenerate"
                         :title="regenerateDisabledHint"
                         @click="handleRegenerateNode(1, '故事大纲')">重新生成</el-button>
            </div>
          </div>
          <div class="outline-body"
               :class="{ 'editable-body': canManualEdit }"
               v-if="detail.outline.outlineText"
               @click="openOutlineEditor()">
            <p v-if="detail.outline.summary && !outlineExpanded && detail.outline.summary !== detail.outline.outlineText" class="outline-summary">
              {{ detail.outline.summary }}
            </p>
            <pre v-else-if="outlineExpanded" class="outline-text">{{ detail.outline.outlineText }}</pre>
            <p v-else class="outline-summary">{{ detail.outline.outlineText }}</p>
            <span v-if="canManualEdit" class="edit-hint">点击编辑</span>
          </div>
          <div v-if="detail.outline.positivePrompt || detail.outline.negativePrompt" class="outline-prompts">
            <div v-if="detail.outline.positivePrompt" class="ol-prompt-line">
              <span class="ol-prompt-label">正面提示词</span>
              <span class="ol-prompt-value">{{ detail.outline.positivePrompt }}</span>
            </div>
            <div v-if="detail.outline.negativePrompt" class="ol-prompt-line">
              <span class="ol-prompt-label">负面提示词</span>
              <span class="ol-prompt-value">{{ detail.outline.negativePrompt }}</span>
            </div>
          </div>
        </div>

        <!-- 分镜脚本（整合场景分组 + 分镜，按group_id分组） -->
        <div v-if="detail.storyboards?.length" class="sketch-card prod-card" :class="{ 'table-expanded': storyboardExpanded }">
          <div class="card-head">
            <div class="card-head-left">
              <h3>② 分镜脚本（{{ detail.storyboards.length }}）</h3>
              <el-tag size="small" type="info" effect="plain" v-if="detail.sceneGroups?.length">
                {{ detail.sceneGroups.length }} 个场景
              </el-tag>
            </div>
            <div class="card-head-actions">
              <el-button size="small" text type="primary" @click="storyboardExpanded = !storyboardExpanded">{{ storyboardExpanded ? '收起' : '展开' }}</el-button>
              <el-button size="small" text type="primary"
                         v-if="showStepControls"
                         :disabled="!canRegenerate"
                         :title="regenerateDisabledHint"
                         @click="handleRegenerateNode(2, '分镜脚本')">重新生成</el-button>
            </div>
          </div>
          <!-- 顶部共享表头 -->
          <div class="sb-grid sb-grid-header">
            <div class="sb-cell sb-w-dur">本镜时长</div>
            <div class="sb-cell sb-w-angle">镜头角度</div>
            <div class="sb-cell sb-w-shot">镜头描述</div>
            <div class="sb-cell sb-w-scene">场景</div>
            <div class="sb-cell sb-w-char">出场角色</div>
            <div class="sb-cell sb-w-props">出场道具</div>
            <div class="sb-cell sb-w-desc">分镜描述</div>
            <div class="sb-cell sb-w-dlg">台词内容</div>
            <div class="sb-cell sb-w-visual">画面描述</div>
          </div>
          <div v-for="(group, gIdx) in storyboardGroups" :key="gIdx" class="group-block">
            <div class="group-head">
              <span class="group-tag">#{{ group.groupIndex }}</span>
              <span class="group-title-sm">{{ group.title }}</span>
              <span class="group-meta-sm">{{ group.list.length }} 条 · {{ group.duration }}s</span>
            </div>
            <div class="sb-grid sb-grid-body" v-for="row in group.list" :key="row.id"
                 :class="{ 'row-clickable': canManualEdit }"
                 @click="openStoryboardEditor(row)">
              <div class="sb-cell sb-w-dur sb-c-dur">{{ row.duration ? row.duration + 's' : '—' }}</div>
              <div class="sb-cell sb-w-angle">{{ row.cameraAngle || '—' }}</div>
              <div class="sb-cell sb-w-shot">{{ row.shotDesc || row.action || '—' }}</div>
              <div class="sb-cell sb-w-scene">{{ row.scene || '—' }}</div>
              <div class="sb-cell sb-w-char">{{ row.characters || '—' }}</div>
              <div class="sb-cell sb-w-props">{{ row.props || '—' }}</div>
              <div class="sb-cell sb-w-desc">{{ row.storyboardDesc || '—' }}</div>
              <div class="sb-cell sb-w-dlg">{{ row.dialogue || '—' }}</div>
              <div class="sb-cell sb-w-visual">{{ row.visualDesc || '—' }}</div>
            </div>
          </div>
        </div>

        <!-- 资产设计 -->
        <div v-if="detail.assetDesigns?.length" class="sketch-card prod-card" :class="{ 'table-expanded': assetExpanded }">
          <div class="card-head">
            <div class="card-head-left">
              <h3>③ 资产设计（{{ detail.assetDesigns.length }}）</h3>
            </div>
            <div class="card-head-actions">
              <el-button size="small" text type="primary" @click="assetExpanded = !assetExpanded">{{ assetExpanded ? '收起' : '展开' }}</el-button>
              <el-button size="small" text type="primary"
                         v-if="showStepControls"
                         :disabled="!canRegenerate"
                         :title="regenerateDisabledHint"
                         @click="handleRegenerateNode(3, '资产设计')">重新生成</el-button>
            </div>
          </div>
          <div class="ad-grid ad-grid-header">
            <div class="ad-cell ad-w-type">资产类型</div>
            <div class="ad-cell ad-w-name">资产名称</div>
            <div class="ad-cell ad-w-from">衍生自</div>
            <div class="ad-cell ad-cell-last">资产描述</div>
            <div class="ad-cell ad-w-ver">版本</div>
          </div>
          <div class="ad-grid ad-grid-body" v-for="row in detail.assetDesigns" :key="row.id"
               :class="{ 'row-clickable': canManualEdit }"
               @click="openAssetEditor(row)">
            <div class="ad-cell ad-w-type">
              <el-tag :type="assetTypeTag(row.assetType)" size="small" effect="plain">{{ row.assetType }}</el-tag>
            </div>
            <div class="ad-cell ad-w-name">{{ row.assetName }}</div>
            <div class="ad-cell ad-w-from">{{ row.derivedFrom || '无' }}</div>
            <div class="ad-cell ad-cell-last">{{ row.assetDesc }}</div>
            <div class="ad-cell ad-w-ver ad-c-ver">{{ row.version || 1 }}</div>
          </div>
        </div>

        <!-- 资产绘图 -->
        <div v-if="detail.assetImages?.length" class="sketch-card prod-card">
          <div class="card-head">
            <div class="card-head-left">
              <h3>④ 资产绘图（{{ detail.assetImages.length }}）</h3>
            </div>
            <div class="card-head-actions" v-if="showStepControls">
              <el-button size="small" text type="primary"
                         :disabled="!canRegenerate"
                         :title="regenerateDisabledHint"
                         @click="handleRegenerateNode(4, '资产绘图')">重新生成</el-button>
            </div>
          </div>
          <div class="image-grid">
            <div v-for="img in detail.assetImages" :key="img.id ?? img.assetName" class="image-item">
              <div class="img-actions">
                <el-button
                  v-if="img.id"
                  size="small"
                  text
                  type="primary"
                  class="regen-single-btn"
                  :disabled="!canRegenerate"
                  :title="regenerateDisabledHint"
                  @click.stop="openRegenDialog(4, img)"
                >重生成</el-button>
              </div>
              <el-image
                :src="img.thumbnailUrl || img.imageUrl"
                :preview-src-list="[img.imageUrl]"
                fit="cover"
                class="thumb"
              />
              <div class="image-caption">
                <span>{{ img.assetName }}</span>
                <span v-if="img.width && img.height" class="cap-sub">{{ img.width }}×{{ img.height }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 衍生绘图 -->
        <div v-if="detail.deriveImages?.length" class="sketch-card prod-card">
          <div class="card-head">
            <div class="card-head-left">
              <h3>⑤ 衍生绘图（{{ detail.deriveImages.length }}）</h3>
            </div>
            <div class="card-head-actions" v-if="showStepControls">
              <el-button size="small" text type="primary"
                         :disabled="!canRegenerate"
                         :title="regenerateDisabledHint"
                         @click="handleRegenerateNode(5, '衍生绘图')">重新生成</el-button>
            </div>
          </div>
          <div class="image-grid">
            <div v-for="img in detail.deriveImages" :key="img.id ?? img.assetName" class="image-item">
              <div class="img-actions">
                <el-button
                  v-if="img.id"
                  size="small"
                  text
                  type="primary"
                  class="regen-single-btn"
                  :disabled="!canRegenerate"
                  :title="regenerateDisabledHint"
                  @click.stop="openRegenDialog(5, img)"
                >重生成</el-button>
              </div>
              <el-image
                :src="img.thumbnailUrl || img.imageUrl"
                :preview-src-list="[img.imageUrl]"
                fit="cover"
                class="thumb"
              />
              <div class="image-caption">
                <span>{{ img.assetName }}</span>
                <span v-if="img.width && img.height" class="cap-sub">{{ img.width }}×{{ img.height }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 素材提示词（保留结构，目前后端未接入，暂不显示） -->
        <div v-if="detail.materialPrompts?.length" class="sketch-card prod-card">
          <div class="card-head">
            <div class="card-head-left">
              <h3>素材提示词（{{ detail.materialPrompts.length }}）</h3>
            </div>
          </div>
          <div class="prompt-grid">
            <div v-for="p in detail.materialPrompts" :key="p.id ?? p.sceneIndex" class="prompt-item">
              <div class="prompt-head">
                <span class="prompt-tag">#{{ p.sceneIndex }}</span>
                <span class="prompt-type" v-if="p.promptType">{{ p.promptType }}</span>
              </div>
              <p class="prompt-text">{{ p.promptText }}</p>
              <p v-if="p.negativePrompt" class="prompt-neg">
                <span class="neg-label">负向：</span>{{ p.negativePrompt }}
              </p>
            </div>
          </div>
        </div>

        <!-- 分镜画面 -->
        <div v-if="detail.images?.length" class="sketch-card prod-card">
          <div class="card-head">
            <div class="card-head-left">
              <h3>⑥ 分镜画面（{{ detail.images.length }}）</h3>
            </div>
            <div class="card-head-actions" v-if="showStepControls">
              <el-button size="small" text type="primary"
                         :disabled="!canRegenerate"
                         :title="regenerateDisabledHint"
                         @click="handleRegenerateNode(6, '分镜绘图')">重新生成</el-button>
            </div>
          </div>
          <div class="image-grid">
            <div v-for="img in detail.images" :key="img.id ?? img.sceneIndex" class="image-item">
              <div class="img-actions">
                <el-button
                  v-if="img.id"
                  size="small"
                  text
                  type="primary"
                  class="regen-single-btn"
                  :disabled="!canRegenerate"
                  :title="regenerateDisabledHint"
                  @click.stop="openRegenDialog(6, img)"
                >重生成</el-button>
              </div>
              <el-image
                :src="img.thumbnailUrl || img.imageUrl"
                :preview-src-list="[img.imageUrl]"
                fit="cover"
                class="thumb"
              />
              <div class="image-caption">
                <span>#{{ img.sceneIndex }}</span>
                <span v-if="img.width && img.height" class="cap-sub">{{ img.width }}×{{ img.height }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 角色音频 -->
        <div v-if="detail.audios?.length || isStepSkippedInDetail(7)" class="sketch-card prod-card">
          <div class="card-head">
            <div class="card-head-left">
              <h3>⑦ 角色音频（{{ detail.audios?.length || 0 }}）</h3>
              <el-tag v-if="isStepSkippedInDetail(7)" type="info" size="small" effect="plain">
                已跳过（配音关闭）
              </el-tag>
            </div>
            <div class="card-head-actions" v-if="showStepControls && !isStepSkippedInDetail(7)">
              <el-button size="small" text type="primary"
                         :disabled="!canRegenerate"
                         :title="regenerateDisabledHint"
                         @click="handleRegenerateNode(7, '音频合成')">重新生成</el-button>
            </div>
          </div>
          <div v-if="isStepSkippedInDetail(7) && !detail.audios?.length" class="skipped-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>AI 配音已关闭，步骤7「配音合成」被跳过，直接进入视频生成阶段。</span>
          </div>
          <div v-else-if="detail.audios?.length" class="audio-list">
            <div v-for="a in detail.audios" :key="a.id ?? a.sceneIndex" class="audio-item">
              <div class="audio-meta">
                <span class="audio-tag">#{{ a.sceneIndex }}</span>
                <span class="audio-role">{{ a.roleName || '旁白' }}</span>
                <span v-if="a.duration" class="audio-dur">{{ a.duration }}s</span>
              </div>
              <audio controls :src="a.audioUrl" class="audio-player" />
            </div>
          </div>
        </div>

        <!-- 场景视频 -->
        <div v-if="detail.videos?.length" class="sketch-card prod-card">
          <div class="card-head">
            <div class="card-head-left">
              <h3>⑧ 场景视频（{{ detail.videos.length }}）</h3>
            </div>
            <div class="card-head-actions" v-if="showStepControls">
              <el-button size="small" text type="primary"
                         :disabled="!canRegenerate"
                         :title="regenerateDisabledHint"
                         @click="handleRegenerateNode(8, '视频生成')">重新生成</el-button>
            </div>
          </div>
          <div class="video-grid">
            <div v-for="(v, vidx) in detail.videos" :key="v.id ?? vidx" class="video-item">
              <div v-if="v.id && showStepControls" class="vid-actions">
                <el-button
                  size="small"
                  text
                  type="primary"
                  class="regen-single-btn"
                  :disabled="!canRegenerate"
                  @click.stop="openRegenDialog(8, v)"
                >重生成</el-button>
              </div>
              <video
                :src="v.videoUrl"
                :poster="v.coverUrl"
                controls
                preload="metadata"
                class="video-player"
              />
              <div class="video-caption">
                <span>#{{ vidx + 1 }}</span>
                <span v-if="v.duration" class="cap-sub">{{ v.duration }}s</span>
                <span v-if="v.resolution" class="cap-sub">{{ v.resolution }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- ⑨ 成片下载（步骤9产物：ZIP播放清单包）+ 在线播放（步骤8完成后也可用） -->
        <div v-if="detail.finalVideoUrl || detail.videos?.length" class="sketch-card prod-card final-card">
          <div class="card-head">
            <div class="card-head-left">
              <h3>⑨ 成片播放与下载</h3>
            </div>
          </div>
          <div class="final-content">
            <div class="final-info">
              <div class="final-cover" v-if="detail.coverUrl">
                <img :src="detail.coverUrl" alt="封面" />
              </div>
              <div class="final-cover" v-else-if="detail.videos?.length && detail.videos[0].coverUrl">
                <img :src="detail.videos[0].coverUrl" alt="封面" />
              </div>
              <div class="final-meta">
                <div class="final-title">{{ detail.title || '未命名作品' }}</div>
                <div class="final-stats">
                  <span>共 {{ (detail.videos?.length || 0) }} 段视频</span>
                  <span v-if="detail.duration">总时长 {{ detail.duration }}s</span>
                  <span v-if="detail.resolution">{{ detail.resolution }}</span>
                </div>
                <div class="final-desc">
                  <span v-if="detail.finalVideoUrl">ZIP 包含：编号视频文件（001_xxx.mp4, 002_xxx.mp4...）+ manifest.json 播放清单</span>
                  <span v-else>已有场景视频可直接播放；完成步骤9后可下载打包成片</span>
                </div>
              </div>
            </div>
            <div class="final-actions">
              <el-button
                v-if="detail.videos?.length"
                @click="openPlayer"
                class="final-play-btn"
              >
                <el-icon><VideoPlay /></el-icon>
                <span>播放成片</span>
              </el-button>
              <a
                v-if="detail.finalVideoUrl"
                :href="detail.finalVideoUrl"
                download
                class="final-download-btn"
              >
                <el-icon><Download /></el-icon>
                <span>下载成片包（ZIP）</span>
              </a>
              <el-button
                v-else
                disabled
                class="final-download-btn"
                title="完成步骤9（视频合并）后可下载打包成片"
              >
                <el-icon><Download /></el-icon>
                <span>步骤9未完成</span>
              </el-button>
            </div>
          </div>
        </div>

        <!-- 视频播放器模态框 -->
        <el-dialog v-model="showPlayer" title="成片播放器" width="85%" :close-on-click-modal="false" class="player-dialog">
          <div class="player-container">
            <div class="player-main">
              <video
                ref="videoElRef"
                :src="currentPlayingUrl"
                :loop="isSingleLoop"
                :playback-rate="playbackRate"
                controls
                preload="auto"
                class="player-video"
                @ended="onVideoEnded"
                @error="onVideoError"
                @loadeddata="onVideoLoaded"
                @play="isPlaying = true"
                @pause="isPlaying = false"
              ></video>
              <div class="player-controls">
                <div class="ctrl-left">
                  <el-button size="small" text @click="playPrev" :disabled="currentPlayIndex <= 0">上一段</el-button>
                  <el-button size="small" type="primary" @click="togglePlay">
                    {{ isPlaying ? '暂停' : '播放' }}
                  </el-button>
                  <el-button size="small" text @click="playNext" :disabled="currentPlayIndex >= playList.length - 1">下一段</el-button>
                </div>
                <div class="ctrl-center">
                  <span class="player-now-playing">
                    正在播放：第 {{ currentPlayIndex + 1 }} / {{ playList.length }} 段
                    <span v-if="playList[currentPlayIndex]" class="player-dur">
                      ({{ playList[currentPlayIndex].duration }}s)
                    </span>
                  </span>
                </div>
                <div class="ctrl-right">
                  <el-button
                    size="small"
                    text
                    :type="playMode !== 'sequential' ? 'primary' : ''"
                    @click="togglePlayMode"
                    :title="'播放模式：' + playModeLabel[playMode]"
                  >
                    {{ playModeLabel[playMode] }}
                  </el-button>
                  <el-dropdown size="small" @command="setPlaybackRate">
                    <el-button size="small" text>
                      {{ playbackRate }}x
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item
                          v-for="r in [0.5, 0.75, 1, 1.25, 1.5, 2]"
                          :key="r"
                          :command="r"
                          :class="{ 'is-active': playbackRate === r }"
                        >{{ r }}x</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
            </div>
            <div class="player-sidebar">
              <h4 class="player-sidebar-title">播放列表</h4>
                <div class="player-list">
                  <div
                    v-for="(item, index) in playList"
                    :key="item.id || index"
                    class="player-list-item"
                    :class="{ active: index === currentPlayIndex }"
                    @click="selectPlayItem(index)"
                  >
                    <span class="player-list-order">{{ index + 1 }}.</span>
                    <span class="player-list-name">{{ item.storyboardSeqRange || `片段${index + 1}` }}</span>
                    <span class="player-list-dur">{{ item.duration }}s</span>
                    <span v-if="index === currentPlayIndex" class="player-list-playing-indicator">▶</span>
                  </div>
                </div>
            </div>
          </div>
          <template #footer>
            <el-button @click="closePlayer">关闭</el-button>
          </template>
        </el-dialog>

        <el-empty
          v-if="!hasAnyProduct"
          description="尚未生成产物，任务启动后将陆续展示 9 步结果"
        />
      </div>

      <!-- 右栏：实时进度 / 节点 / 失败 -->
      <div class="right-col">
        <!-- 连接状态 -->
        <div class="sketch-card side-card">
          <div class="card-head">
            <h3>实时进度</h3>
            <span class="conn-dot" :class="{ ws: wsConnected, polling: polling }">
              {{ wsConnected ? 'WebSocket' : polling ? '轮询中' : '空闲' }}
            </span>
          </div>
          <div ref="logBoxRef" class="log-box">
            <div
              v-for="log in progressLogs"
              :key="log.id || `${log.step}-${log.createTime}`"
              class="log-line"
            >
              <span class="log-time">{{ fmtTime(log.createTime) }}</span>
              <span class="log-step">{{ log.stepName || `步骤${log.step}` }}</span>
              <span class="log-msg">{{ log.message || `${log.progress}%` }}</span>
            </div>
            <div v-if="!progressLogs?.length" class="log-empty">暂无进度</div>
          </div>
        </div>

        <!-- 节点状态 -->
        <div v-if="detail.nodeStates?.length" class="sketch-card side-card">
          <div class="card-head">
            <h3>节点状态</h3>
            <el-button
              v-if="detail.status === TaskStatus.FAILED"
              size="small"
              type="warning"
              @click="handleResumeFromFailure"
            >
              断点续跑
            </el-button>
          </div>
          <el-table :data="detail.nodeStates" size="small" :show-header="false">
            <el-table-column width="65" align="center">
              <template #default="{ row }">
                <el-tag :type="nodeTagType(row.status)" size="small" effect="light" round>
                  {{ nodeStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="nodeName" min-width="90">
              <template #default="{ row }">{{ row.nodeName || row.nodeCode }}</template>
            </el-table-column>
            <el-table-column label="产物" width="100">
              <template #default="{ row }">
                <span v-if="nodeArtifactSummary(row.step)" class="artifact-summary">
                  {{ nodeArtifactSummary(row.step) }}
                </span>
                <span v-else class="artifact-empty">—</span>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="80" align="center">
              <template #default="{ row }">
                <span v-if="row.durationMs != null || row.duration"
                      :title="row.durationMs != null ? `耗时 ${row.durationMs.toLocaleString()} 毫秒` : ''">
                  {{ formatDuration(row.duration, row.durationMs) }}
                </span>
                <span v-else class="artifact-empty">—</span>
              </template>
            </el-table-column>
          </el-table>
        </div>


      </div>
    </div>
  </div>

  <!-- JSON 编辑模态框 -->
  <el-dialog
    v-model="editorDialog.visible"
    :title="editorDialog.title"
    width="700px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-input
      v-model="editorDialog.jsonText"
      type="textarea"
      :rows="16"
      placeholder='编辑 JSON 内容...'
      style="font-family: 'Monaco','Menlo','Consolas',monospace"
    />
    <template #footer>
      <el-button @click="editorDialog.visible = false">取消</el-button>
      <el-button type="primary" @click="confirmEditorSave()">保存</el-button>
    </template>
  </el-dialog>

  <!-- 单图重生成参数确认对话框 -->
  <el-dialog
    v-model="regenDialog.visible"
    :title="regenDialog.title"
    width="620px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="regen-dialog-body">
      <div v-if="regenDialog.item" class="regen-preview">
        <!-- 视频预览（step 8） -->
        <video
          v-if="regenDialog.step === 8"
          :src="regenDialog.item.videoUrl"
          :poster="regenDialog.item.coverUrl"
          controls
          preload="metadata"
          class="regen-thumb regen-video-thumb"
        />
        <!-- 图片预览（step 4/5/6） -->
        <el-image
          v-else
          :src="regenDialog.item.thumbnailUrl || regenDialog.item.imageUrl"
          fit="cover"
          class="regen-thumb"
        />
        <div class="regen-meta">
          <div class="regen-label">{{ regenDialog.step === 8 ? '分镜序号：' : (regenDialog.item.assetName ? '资产名称：' : '分镜序号：') }}</div>
          <div class="regen-value">{{ regenDialog.item.assetName || `#${regenDialog.item.sceneIndex}` }}</div>
          <div class="regen-label">所属步骤：</div>
          <div class="regen-value">步骤{{ regenDialog.step }}</div>
          <div v-if="regenDialog.step === 8 && regenDialog.item.duration" class="regen-label">原时长：</div>
          <div v-if="regenDialog.step === 8 && regenDialog.item.duration" class="regen-value">{{ regenDialog.item.duration }}s</div>
        </div>
      </div>

      <div class="regen-form">
        <div v-if="regenDialog.step === 4 || regenDialog.step === 5" class="regen-form-item">
          <label class="regen-form-label">资产描述</label>
          <el-input
            v-model="regenDialog.fields.assetDesc"
            type="textarea"
            :rows="3"
            placeholder="修改资产描述以重新生成图片"
          />
        </div>
        <div v-if="regenDialog.step === 6" class="regen-form-item">
          <label class="regen-form-label">画面描述</label>
          <el-input
            v-model="regenDialog.fields.visualDesc"
            type="textarea"
            :rows="3"
            placeholder="修改分镜画面描述以重新生成图片"
          />
        </div>
        <div v-if="regenDialog.step === 8" class="regen-form-item">
          <label class="regen-form-label">时长（秒）</label>
          <el-input-number
            v-model="regenDialog.fields.duration"
            :min="5"
            :max="60"
            :step="1"
            controls-position="right"
            size="default"
            placeholder="修改单条视频时长，单位秒（建议5-60s）"
          />
          <div class="regen-hint">提示：Agnes 视频模型最少5秒。组内后续帧的「前帧→本帧」连贯插值会占用总时长。</div>
        </div>

        <div class="regen-form-item">
          <label class="regen-form-label">画风+风格（视觉定位）</label>
          <div class="regen-style-readonly">
            <span v-if="regenStylePreview" class="regen-style-badge">
              {{ regenStylePreview }}
            </span>
            <span v-else class="regen-style-empty">未设置</span>
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="regenDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="regenSubmitting" @click="confirmRegenSingle()">
        确认重新生成
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowDown, Warning, InfoFilled, Download, VideoPlay } from '@element-plus/icons-vue'
import {
  getTaskDetail,
  getTaskFailureLogs,
  clearTaskFailureLogs,
  pauseTask,
  resumeTask,
  retryTask,
  deleteTask,
  regenerateNode,
  regenerateAssetImage,
  regenerateStoryboardImage,
  regenerateSceneVideo,
  resumeFromFailure,
  resumeFromStep,
  approveTask,
  executeNextStep,
  updateStorySummary,
  updateStoryboard,
  updateAssetDesign,
  getTaskManifest,
  type TaskDetailVO,
  type StoryboardVO,
  type AssetImageVO,
  type StoryboardImageVO,
  type AssetDesignVO
} from '@/api/task'
import { useTaskProgress } from '@/composables/useTaskProgress'
import { TaskStatus, statusMeta, stepName, ART_STYLES, VISUAL_STYLES, ART_STYLE_LEGACY_MAP, VISUAL_STYLE_LEGACY_MAP } from '@/constants/task'
import { Doodles } from '@/components/illustrations'

const route = useRoute()
const router = useRouter()

const taskIdRef = ref<string | null>(String(route.params.id || null))

const loading = ref(false)
const detail = ref<TaskDetailVO>({} as TaskDetailVO)
const expandedGroups = ref<number[]>([1])
const outlineExpanded = ref(false)
const storyboardExpanded = ref(false)
const assetExpanded = ref(false)
const showFailDetail = ref(false)

// ======== 人工审核：JSON 编辑模态框 ========
const editorDialog = reactive({
  visible: false,
  title: '',
  jsonText: '',
  onSave: null as (() => Promise<void>) | null,
})

// ======== 单图重生成参数对话框 ========
const regenDialog = reactive({
  visible: false,
  title: '',
  step: 0,
  itemId: null as string | number | null,
  item: null as any,
  fields: {} as Record<string, any>,
  onConfirm: null as (() => Promise<void>) | null,
})

// ======== 视频播放器状态 ========
const showPlayer = ref(false)
const videoElRef = ref<HTMLVideoElement | null>(null)
const currentPlayIndex = ref(0)
const isPlaying = ref(false)
type PlayMode = 'sequential' | 'single-loop'
const playMode = ref<PlayMode>('sequential')
const playModeLabel: Record<PlayMode, string> = {
  'sequential': '顺序播放',
  'single-loop': '单段循环',
}
const isSingleLoop = computed(() => playMode.value === 'single-loop')
const playbackRate = ref(1)
const runtimeManifest = ref<string>('')

const playList = computed(() => {
  const manifestStr = runtimeManifest.value || detail.value.finalWorkManifest
  if (manifestStr) {
    try {
      const manifest = JSON.parse(manifestStr)
      if (manifest?.videos?.length) return manifest.videos
    } catch (e) {
      console.error('Failed to parse manifest:', e)
    }
  }
  const videos = detail.value.videos
  if (videos?.length) {
    return videos.map((v, idx) => ({
      id: v.id ?? idx,
      orderIndex: idx + 1,
      filename: `片段${idx + 1}.mp4`,
      sceneGroupId: (v as any).sceneGroupId,
      storyboardSeqRange: `分镜#${idx + 1}`,
      duration: v.duration ?? 0,
      originalUrl: v.videoUrl,
      coverUrl: v.coverUrl
    }))
  }
  return []
})

const currentPlayingUrl = computed(() => {
  if (playList.value.length === 0) return ''
  const idx = Math.min(currentPlayIndex.value, playList.value.length - 1)
  return playList.value[idx]?.originalUrl || ''
})

async function ensureManifest() {
  if (runtimeManifest.value || detail.value.finalWorkManifest) return
  const id = taskIdRef.value
  if (!id) return
  try {
    const json = await getTaskManifest(id)
    if (json) {
      runtimeManifest.value = json
      detail.value.finalWorkManifest = json
    }
  } catch (_e) {
    // 静默失败：步骤8已有videos兜底，不弹窗打扰用户
    console.warn('[player] manifest 加载失败，使用场景视频兜底')
  }
}

/** 彻底停止一个 video 元素：静音→暂停→重置时间→清空src→load()，确保没有残留声音 */
function forceStopVideo(video: HTMLVideoElement | null | undefined) {
  if (!video) return
  try {
    video.pause()
    video.muted = true
    video.currentTime = 0
    video.removeAttribute('src')
    video.load()
  } catch (_) { /* ignore */ }
}

/** 统一的切视频逻辑：复用同一个 video 节点，无缝切换（先静音+停 → 变源 → load → 播 → 解音），避免销毁重建导致闪屏 */
function switchToIndex(index: number, shouldPlay: boolean = true) {
  if (index < 0 || index >= playList.value.length) return
  const v = videoElRef.value
  if (v) {
    try {
      v.pause()
      v.muted = true
      v.currentTime = 0
    } catch (_) { /* ignore */ }
  }
  currentPlayIndex.value = index
  isPlaying.value = shouldPlay
  nextTick(() => {
    const v2 = videoElRef.value
    if (!v2) return
    v2.playbackRate = playbackRate.value
    v2.loop = isSingleLoop.value
    // 强制用新 src 重新开始加载流（防止浏览器复用旧解码缓冲露出上一帧残影）
    try { v2.load() } catch (_) { /* ignore */ }
    if (shouldPlay) {
      v2.play().catch(() => { /* autoplay reject 静默 */ })
    }
  })
}

async function openPlayer() {
  await ensureManifest()
  if (playList.value.length === 0) {
    ElMessage.warning('暂无可播放的视频')
    return
  }
  showPlayer.value = true
  // 等待对话框挂载 video DOM
  await nextTick()
  // 重新初始化：video 节点是新创建的，需显式设置 src + load 才会开始加载
  const v = videoElRef.value
  if (!v) return
  currentPlayIndex.value = 0
  isPlaying.value = true
  v.src = currentPlayingUrl.value
  v.playbackRate = playbackRate.value
  v.loop = isSingleLoop.value
  v.muted = false
  v.currentTime = 0
  try { v.load() } catch (_) { /* ignore */ }
  v.play().catch(() => { /* autoplay reject 静默 */ })
}

function closePlayer() {
  forceStopVideo(videoElRef.value)
  isPlaying.value = false
  showPlayer.value = false
}

function selectPlayItem(index: number) {
  switchToIndex(index, true)
}

function togglePlay() {
  const video = videoElRef.value
  if (!video) return
  if (!video.paused) {
    video.pause()
  } else {
    video.play().catch(() => { /* 用户手动触发失败不弹窗 */ })
  }
}

function togglePlayMode() {
  playMode.value = playMode.value === 'sequential' ? 'single-loop' : 'sequential'
  // 实时写回当前 video.loop（无需切段立即生效）
  if (videoElRef.value) {
    videoElRef.value.loop = playMode.value === 'single-loop'
  }
  ElMessage.info(`播放模式：${playModeLabel[playMode.value]}`)
}

function playNext() {
  if (currentPlayIndex.value < playList.value.length - 1) {
    switchToIndex(currentPlayIndex.value + 1, true)
  } else {
    isPlaying.value = false
    if (videoElRef.value) videoElRef.value.pause()
    if (isSingleLoop.value) {
      // 单段循环理论上不会走到这里（video.loop=true 不触发 ended），兜底重播本段
      switchToIndex(currentPlayIndex.value, true)
    } else {
      ElMessage.info('已播放到最后一段')
    }
  }
}

function playPrev() {
  if (currentPlayIndex.value > 0) {
    switchToIndex(currentPlayIndex.value - 1, true)
  }
}

function setPlaybackRate(rate: number) {
  playbackRate.value = rate
  if (videoElRef.value) {
    videoElRef.value.playbackRate = rate
  }
}

function onVideoEnded() {
  playNext()
}

function onVideoError() {
  // 静默：单段加载失败不弹窗打扰，1s 后尝试下一段
  console.warn('[player] 视频加载失败，将尝试下一段:', currentPlayingUrl.value)
  setTimeout(() => {
    if (showPlayer.value) playNext()
  }, 1000)
}

function onVideoLoaded() {
  const v = videoElRef.value
  if (!v) return
  // 确保切换后倍速 & loop 被正确应用
  v.playbackRate = playbackRate.value
  v.loop = isSingleLoop.value
  // 切视频时 muted=true 防止过渡杂音；加载完恢复音量（除非用户本来就静音了）
  v.muted = false
}

// 关闭模态框时（无论是点击关闭按钮还是 × 按钮触发的 v-model）强制停止视频，防止残留声音
watch(showPlayer, (val) => {
  if (!val) {
    // 延迟到 dialog 过渡结束后再停，避免过渡期间的声音残留
    setTimeout(() => forceStopVideo(videoElRef.value), 300)
  }
})
// 单段循环开关：实时更新当前正在播放的 video.loop（不切段时立即生效）
watch(isSingleLoop, (val) => {
  if (videoElRef.value) videoElRef.value.loop = val
})
onBeforeUnmount(() => {
  forceStopVideo(videoElRef.value)
})

/** 是否允许编辑（人工审核模式 + 已完成的步骤） */
const canManualEdit = computed(() => {
  return detail.value.execMode === 1 && detail.value.pendingReview
})

const { progressLogs, currentStep, totalProgress, status, wsConnected, polling, refresh, startPolling } =
  useTaskProgress(taskIdRef, {
    onStepCompleted: (_completedStep) => {
      loadDetail()
    }
  })

const progressTotal = computed(() => {
  if (detail.value.status === TaskStatus.DONE) return 100
  const p = totalProgress.value || detail.value.progress || 0
  // When task is failed, ensure progress doesn't exceed the failure step's proportion
  if (detail.value.status === TaskStatus.FAILED && detail.value.failureStep) {
    const maxP = ((detail.value.failureStep - 1) * 100) / 9
    return Math.min(p, maxP)
  }
  // When task is paused, ensure progress doesn't exceed the pause step
  if (detail.value.status === TaskStatus.PAUSED && currentStep.value) {
    const maxP = ((currentStep.value - 1) * 100) / 9
    return Math.min(p, maxP)
  }
  return p
})

const progressStatus = computed<'' | 'success' | 'exception' | 'warning'>(() => {
  if (detail.value.status === TaskStatus.DONE) return 'success'
  if (detail.value.status === TaskStatus.FAILED) return 'exception'
  if (detail.value.status === TaskStatus.PAUSED) return 'warning'
  return ''
})

const hasAnyProduct = computed(() => {
  const d = detail.value
  return !!(
    d.outline ||
    d.sceneGroups?.length ||
    d.storyboards?.length ||
    d.assetDesigns?.length ||
    d.assetImages?.length ||
    d.deriveImages?.length ||
    d.materialPrompts?.length ||
    d.images?.length ||
    d.audios?.length ||
    d.videos?.length ||
    d.finalVideoUrl
  )
})

/** 将 storyboards 按 sceneGroupId 分组 */
const storyboardGroups = computed(() => {
  const list = detail.value.storyboards || []
  if (!list.length) return []
  const map = new Map<number, StoryboardVO[]>()
  for (const sb of list) {
    const gId = Number(sb.sceneGroupId ?? 0)
    if (!map.has(gId)) map.set(gId, [])
    map.get(gId)!.push(sb)
  }
  const groups = detail.value.sceneGroups || []
  return Array.from(map.entries()).map(([gId, items]) => {
    const g = groups.find(g => Number(g.groupIndex) === gId)
    const totalDur = items.reduce((s, i) => s + (Number(i.duration) || 0), 0)
    return {
      groupIndex: gId,
      title: g?.title || `场景 ${gId}`,
      duration: totalDur,
      list: items
    }
  }).sort((a, b) => a.groupIndex - b.groupIndex)
})

function assetTypeTag(type?: string): 'info' | 'success' | 'warning' | 'danger' {
  if (!type) return 'info'
  if (type.includes('人物') || type.toLowerCase().includes('character')) return 'success'
  if (type.includes('场景') || type.toLowerCase().includes('scene')) return 'warning'
  if (type.includes('道具') || type.toLowerCase().includes('prop')) return 'info'
  if (type.includes('音色') || type.toLowerCase().includes('voice')) return 'danger'
  return 'info'
}

const canRegenerate = computed(() => {
  const st = detail.value.status
  // 仅允许「已暂停 / 失败 / 已完成」时重生成。生成中/排队中禁止重生成，避免并发冲突。
  return st === TaskStatus.PAUSED || st === TaskStatus.FAILED || st === TaskStatus.DONE
})

const regenerateDisabledHint = computed(() => {
  const st = detail.value.status
  if (st === TaskStatus.RUNNING) return '任务生成中，请先暂停后再重新生成'
  if (st === TaskStatus.QUEUE) return '任务排队中，请等待开始执行并暂停后再重新生成'
  return ''
})

const showStepControls = computed(() => {
  const st = detail.value.status
  return st === TaskStatus.RUNNING || st === TaskStatus.FAILED || st === TaskStatus.PAUSED
    || st === TaskStatus.DONE
})

function isStepSkippedInDetail(stepOrder: number): boolean {
  return stepOrder === 7 && detail.value?.voiceEnabled === 0
}

function isStepFailed(stepOrder: number): boolean {
  if (detail.value?.failureStep === stepOrder && detail.value?.status === TaskStatus.FAILED) {
    return true
  }
  const node = detail.value?.nodeStates?.find(n => n.step === stepOrder && n.status === 3)
  return !!node
}

function isStepPaused(stepOrder: number): boolean {
  if (detail.value?.status === TaskStatus.PAUSED && currentStep.value === stepOrder) {
    return true
  }
  const node = detail.value?.nodeStates?.find(n => n.step === stepOrder && n.status === 5)
  return !!node
}

function isStepTesting(stepOrder: number): boolean {
  const node = detail.value?.nodeStates?.find(n => n.step === stepOrder)
  if (!node) return false
  return node.status === 1 && detail.value?.status === TaskStatus.RUNNING
}

function isStepBatching(stepOrder: number): boolean {
  const node = detail.value?.nodeStates?.find(n => n.step === stepOrder)
  if (!node) return false
  return node.status === 4 && detail.value?.status === TaskStatus.RUNNING
}

function stepClass(s: number) {
  if (isStepSkippedInDetail(s)) return 'skipped'
  if (isStepFailed(s)) return 'failed'
  if (isStepPaused(s)) return 'paused'
  const cur = currentStep.value || detail.value.currentStep || 0
  const st = detail.value.status
  if (st === TaskStatus.DONE) return 'done'
  if (cur > s) return 'done'
  if (cur === s && st === TaskStatus.RUNNING) return 'active'
  return ''
}

function stepLineClass(s: number) {
  if (isStepSkippedInDetail(s)) return 'skipped'
  if (isStepFailed(s)) return 'failed'
  if (isStepPaused(s)) return 'paused'
  const cur = currentStep.value || detail.value.currentStep || 0
  const st = detail.value.status
  if (st === TaskStatus.DONE) return 'done'
  if (cur > s) return 'done'
  if (cur === s && st === TaskStatus.RUNNING) return 'active'
  return ''
}

function nodeTagType(st?: number): 'info' | 'success' | 'danger' | 'warning' {
  if (st === 2) return 'success'
  if (st === 3) return 'danger'
  if (st === 4) return 'warning'
  if (st === 5) return 'info'
  if (st === 6) return 'info'
  return 'info'
}

function nodeStatusText(st?: number): string {
  if (st === 2) return '完成'
  if (st === 3) return '失败'
  if (st === 4) return '批量中'
  if (st === 5) return '已暂停'
  if (st === 6) return '已跳过'
  if (st === 1) return '进行'
  return '等待'
}

function nodeArtifactSummary(step?: number): string {
  const d = detail.value
  if (!step) return ''
  switch (step) {
    case 1: return d.outline ? `${d.outline.outlineText?.length || 0}字` : ''
    case 2: return d.storyboards?.length ? `${d.storyboards.length}条分镜` : ''
    case 3: return d.assetDesigns?.length ? `${d.assetDesigns.length}项设计` : ''
    case 4: return d.assetImages?.length ? `${d.assetImages.length}张图片` : ''
    case 5: return d.deriveImages?.length
      ? `${d.deriveImages.length}张衍生图`
      : (d.materialPrompts?.length ? `${d.materialPrompts.length}条提示词` : '')
    case 6: return d.images?.length ? `${d.images.length}张分镜图` : ''
    case 7: return d.audios?.length ? `${d.audios.length}条音频` : (isStepSkippedInDetail(7) ? '已跳过' : '')
    case 8: return d.videos?.length ? `${d.videos.length}段视频` : ''
    case 9: return d.finalVideoUrl ? '成片包已生成' : (d.videos?.length ? '合成中' : '')
    default: return ''
  }
}

function fmtTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

function artStyleLabel(value?: string): string {
  if (!value) return ''
  const normalized = ART_STYLE_LEGACY_MAP[value] ?? value
  const found = ART_STYLES.find(a => a.value === normalized)
  return found ? found.label : value
}

function visualStyleLabel(value?: string): string {
  if (!value) return ''
  const normalized = VISUAL_STYLE_LEGACY_MAP[value] ?? value
  const found = VISUAL_STYLES.find(s => s.value === normalized)
  return found ? found.label : value
}

function styleDisplay(): string {
  const art = detail.value.artStyle
  const vis = detail.value.visualStyle
  const parts: string[] = []
  if (art) parts.push(artStyleLabel(art))
  if (vis) parts.push(visualStyleLabel(vis))
  return parts.join(' + ')
}

const regenStylePreview = computed(() => {
  const art = regenDialog.fields.artStyle
  const vis = regenDialog.fields.visualStyle
  const parts: string[] = []
  if (art) parts.push(artStyleLabel(art))
  if (vis) parts.push(visualStyleLabel(vis))
  return parts.join(' + ')
})

/**
 * 节点状态耗时展示：
 * - duration == 0 秒且有 durationMs：短耗时显示毫秒（例如 376ms）
 * - duration < 60 秒：显示秒（例如 5s / 11s）
 * - duration >= 60 秒：换算为分钟+秒（例如 3m35s）
 * 避免列宽太窄把 "s" 挤到下一行误以为是两个单位。
 */
function formatDuration(durationSec?: number, durationMs?: number): string {
  if (durationSec == null && durationMs == null) return '—'
  if ((durationSec ?? 0) === 0 && (durationMs ?? 0) > 0 && (durationMs as number) < 1000) {
    return `${durationMs}ms`
  }
  const sec = durationSec ?? Math.round((durationMs ?? 0) / 1000)
  if (sec < 60) return `${sec}s`
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return s === 0 ? `${m}m` : `${m}m${s}s`
}

let _detailLoading = false
let _lastLoadTime = 0
const LOAD_DEBOUNCE_MS = 500

async function loadDetail(silent = false) {
  const id = taskIdRef.value
  if (!id) return
  const now = Date.now()
  // 静默模式放宽防抖到 4 秒（给 WebSocket 实时刷新让路）
  const threshold = silent ? 4000 : LOAD_DEBOUNCE_MS
  if (_detailLoading || now - _lastLoadTime < threshold) return
  _detailLoading = true
  _lastLoadTime = now
  if (!silent) loading.value = true
  try {
    const d = await getTaskDetail(id)
    detail.value = d
    // 同步任务状态到 composable，控制轮询启停
    status.value = d.status ?? TaskStatus.QUEUE
    if (d.sceneGroups?.length && expandedGroups.value.length <= 1) {
      expandedGroups.value = [d.sceneGroups[0].groupIndex]
    }
    // If task is failed, load failure logs
    if (d.status === TaskStatus.FAILED || d.status === TaskStatus.PAUSED) {
      try {
        const logs = await getTaskFailureLogs(id)
        if (logs && logs.length > 0) {
          detail.value.failureLogs = logs.map(l => ({
            ...l,
            stepName: l.step ? stepName(l.step) : '未知步骤'
          }))
        }
      } catch (e) {
        console.warn('加载失败日志失败', e)
      }
    }
  } catch {
    if (!silent) ElMessage.error('加载任务详情失败')
  } finally {
    _detailLoading = false
    if (!silent) loading.value = false
  }
}

async function handlePause() {
  const id = taskIdRef.value
  if (!id) return
  try {
    // 直接暂停：当前正在生成的单张产物跑完后就停止（不再开始下一张），
    // 保留已完成产物，下次继续从当前步骤未完成的项目续跑（resolveBatchStartIndex 断点续跑）。
    // 不再支持"回退上一步"功能。
    await pauseTask(id, false, false)
    ElMessage.success('已暂停：当前产物生成完成后将停止，下次继续从断点续跑')
    loadDetail()
    refresh()
  } catch (e: any) {
    ElMessage.error(e?.message || '暂停失败')
  }
}

/**
 * 继续按钮：执行后续步骤（按 execMode 分流）。
 * - pendingReview=true（审核暂停横幅显示中）：调用 handleApprove 保持单步语义
 * - execMode === 1（人工审核模式）：执行下一步骤（单步），完成后再次暂停
 * - execMode === 0（自动模式）：执行后续全部步骤直到结束，不再主动暂停
 */
async function handleResume() {
  const id = taskIdRef.value
  if (!id) return
  if (detail.value.pendingReview) {
    // 审核暂停横幅显示时，按钮行为与横幅内"执行下一步"保持一致（单步）
    await handleApprove()
    return
  }
  try {
    const execMode = detail.value.execMode ?? 0
    if (execMode === 1) {
      // 人工审核模式：单步执行
      await executeNextStep(id)
      ElMessage.success('已开始执行下一步骤（单步，完成后暂停）')
    } else {
      // 自动模式：执行后续全部步骤
      await resumeTask(id)
      ElMessage.success('已继续，后续步骤将自动执行直到结束')
    }
    loadDetail()
    startPolling()
  } catch (e: any) {
    ElMessage.error(e?.message || '执行失败')
  }
}

async function handleRetry() {
  const id = taskIdRef.value
  if (!id) return
  await ElMessageBox.confirm('确定重试该任务吗？', '重试任务', { type: 'info' })
    .then(async () => {
      await retryTask(id)
      ElMessage.success('已重新入队')
      loadDetail()
    })
    .catch(() => {})
}

async function handleDelete() {
  const id = taskIdRef.value
  if (!id) return
  await ElMessageBox.confirm('确定删除该任务吗？此操作不可恢复。', '删除任务', {
    type: 'error',
    confirmButtonText: '删除'
  })
    .then(async () => {
      await deleteTask(id)
      ElMessage.success('任务已删除')
      router.replace('/task')
    })
    .catch(() => {})
}

async function handleRegenerateNode(stepOrder: number, nodeName: string) {
  const id = taskIdRef.value
  if (!id) return
  await ElMessageBox.confirm(
    `确定重新生成步骤「${nodeName}」吗？此操作将仅重新生成该步骤，不影响其他步骤的产物。`,
    '单步重生成',
    { type: 'warning', confirmButtonText: '确认重生成' }
  )
    .then(async () => {
      const params: Record<string, any> = {}
      if (detail.value.artStyle) params.artStyle = detail.value.artStyle
      if (detail.value.visualStyle) params.visualStyle = detail.value.visualStyle
      await regenerateNode(id, stepOrder, params)
      ElMessage.success(`已开始重新生成步骤 ${stepOrder}「${nodeName}」`)
      loadDetail()
      refresh()
      startPolling(1500, 60000)
    })
    .catch(() => {})
}

async function handleRegenerateFromStep(stepOrder: number) {
  const id = taskIdRef.value
  if (!id) return
  const nodeName = stepName(stepOrder)
  await ElMessageBox.confirm(
    `确定从步骤「${nodeName}」开始续跑吗？将保留之前步骤的成功产物，从当前位置重新执行该步骤及后续所有步骤。`,
    '断点续跑',
    { type: 'info', confirmButtonText: '确认续跑' }
  )
    .then(async () => {
      await resumeFromStep(id, stepOrder)
      ElMessage.success(`已从步骤 ${stepOrder} 开始续跑`)
      loadDetail()
      refresh()
    })
    .catch(() => {})
}

/** 审核通过：人工审核模式下执行下一步 */
async function handleApprove() {
  const id = taskIdRef.value
  if (!id) return
  await ElMessageBox.confirm(
    '确认执行下一步？将从当前步骤继续往后执行。',
    '执行下一步',
    { type: 'success', confirmButtonText: '执行下一步' }
  )
    .then(async () => {
      await approveTask(id)
      ElMessage.success('已确认，开始执行下一步')
      loadDetail()
      refresh()
    })
    .catch(() => {})
}

/** 审核模式下重新生成当前步骤产物 */
async function handleRegenerateStepReview() {
  const id = taskIdRef.value
  if (!id) return
  const step = detail.value.currentStep || 0
  const nodeName = stepName(step)
  await ElMessageBox.confirm(
    `确认重新生成步骤 ${step}「${nodeName}」吗？此操作将仅重新生成该步骤，不影响其他步骤的产物。`,
    '单步重生成',
    { type: 'warning', confirmButtonText: '重新生成' }
  )
    .then(async () => {
      await regenerateNode(id, step)
      ElMessage.success(`已开始重新生成步骤 ${step}「${nodeName}」`)
      loadDetail()
      refresh()
    })
    .catch(() => {})
}

/** 单张资产图重生成（步骤4 首版 / 步骤5 衍生 通用） */
async function handleRegenerateSingleAsset(img: AssetImageVO, stepOrder: number) {
  const id = taskIdRef.value
  const imageId = img.id
  if (!id || imageId == null) return
  const assetLabel = stepOrder === 5 ? '衍生资产图' : '首版资产图'
  await ElMessageBox.confirm(
    `确认重新生成${assetLabel}「${img.assetName}」吗？此操作将仅重新生成这一张图片，不影响该步骤其他产物。`,
    '单张重生成',
    { type: 'warning', confirmButtonText: '确认重生成' }
  )
    .then(async () => {
      await regenerateAssetImage(id, imageId)
      ElMessage.success(`已开始重新生成${assetLabel}「${img.assetName}」`)
      loadDetail()
      refresh()
    })
    .catch(() => {})
}

/** 单张分镜图重生成（步骤6） */
async function handleRegenerateSingleStoryboard(img: StoryboardImageVO) {
  const id = taskIdRef.value
  const imageId = img.id
  if (!id || imageId == null) return
  await ElMessageBox.confirm(
    `确认重新生成分镜图 #${img.sceneIndex} 吗？此操作将仅重新生成这一张分镜图，不影响其他分镜产物。`,
    '单张分镜重生成',
    { type: 'warning', confirmButtonText: '确认重生成' }
  )
    .then(async () => {
      await regenerateStoryboardImage(id, imageId)
      ElMessage.success(`已开始重新生成分镜图 #${img.sceneIndex}`)
      loadDetail()
      refresh()
    })
    .catch(() => {})
}

async function handleResumeFromFailure() {
  const id = taskIdRef.value
  if (!id) return
  await ElMessageBox.confirm(
    '确定从最近失败的步骤开始续跑吗？',
    '断点续跑',
    { type: 'info', confirmButtonText: '确认续跑' }
  )
    .then(async () => {
      await resumeFromFailure(id)
      ElMessage.success('已从失败步骤开始续跑')
      loadDetail()
      refresh()
    })
    .catch(() => {})
}

async function handleClearFailureLogs() {
  const id = taskIdRef.value
  if (!id) return
  await ElMessageBox.confirm(
    '确定清空该任务的所有失败日志吗？此操作不可恢复。',
    '清空失败日志',
    { type: 'warning', confirmButtonText: '确认清空' }
  )
    .then(async () => {
      await clearTaskFailureLogs(id)
      ElMessage.success('失败日志已清空')
      detail.value.failureLogs = []
      showFailDetail.value = false
      loadDetail()
    })
    .catch(() => {})
}

// ======== 人工审核：JSON 编辑模态框操作 ========
const regenSubmitting = ref(false)

/** 打开故事摘要编辑器 */
function openOutlineEditor() {
  if (!canManualEdit.value) return
  const id = taskIdRef.value
  if (!id) return
  editorDialog.title = '编辑故事摘要'
  editorDialog.jsonText = detail.value.outline?.outlineText || ''
  editorDialog.onSave = async () => {
    await updateStorySummary(id, editorDialog.jsonText)
  }
  editorDialog.visible = true
}

/** 打开分镜脚本编辑器 */
function openStoryboardEditor(storyboard: StoryboardVO) {
  if (!canManualEdit.value) return
  const id = taskIdRef.value
  if (!id || !storyboard.id) return
  editorDialog.title = `编辑分镜 #${storyboard.sceneIndex ?? storyboard.localSeq ?? ''}`
  const fields: Record<string, any> = {}
  if (storyboard.cameraAngle) fields.cameraAngle = storyboard.cameraAngle
  if (storyboard.shotDesc) fields.shotDesc = storyboard.shotDesc
  if (storyboard.scene) fields.scene = storyboard.scene
  if (storyboard.characters) fields.characters = storyboard.characters
  if (storyboard.props) fields.props = storyboard.props
  if (storyboard.storyboardDesc) fields.storyboardDesc = storyboard.storyboardDesc
  if (storyboard.dialogue) fields.dialogue = storyboard.dialogue
  if (storyboard.visualDesc) fields.visualDesc = storyboard.visualDesc
  if (storyboard.duration) fields.duration = storyboard.duration
  editorDialog.jsonText = JSON.stringify(fields, null, 2)
  editorDialog.onSave = async () => {
    const parsed = JSON.parse(editorDialog.jsonText)
    await updateStoryboard(id, storyboard.id!, parsed)
  }
  editorDialog.visible = true
}

/** 打开资产设计编辑器 */
function openAssetEditor(asset: AssetDesignVO) {
  if (!canManualEdit.value) return
  const id = taskIdRef.value
  if (!id || !asset.id) return
  editorDialog.title = `编辑资产：${asset.assetName || ''}`
  const fields: Record<string, any> = {}
  if (asset.assetName) fields.assetName = asset.assetName
  if (asset.assetDesc) fields.assetDesc = asset.assetDesc
  if (asset.derivedFrom) fields.derivedFrom = asset.derivedFrom
  if (asset.resourceUrl) fields.resourceUrl = asset.resourceUrl
  editorDialog.jsonText = JSON.stringify(fields, null, 2)
  editorDialog.onSave = async () => {
    const parsed = JSON.parse(editorDialog.jsonText)
    await updateAssetDesign(id, asset.id!, parsed)
  }
  editorDialog.visible = true
}

/** 保存编辑器内容 */
async function confirmEditorSave() {
  if (!editorDialog.onSave) return
  try {
    await editorDialog.onSave()
    ElMessage.success('保存成功')
    editorDialog.visible = false
    loadDetail()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

// ======== 单图重生成参数对话框 ========

/** 打开单图重生成对话框 */
function openRegenDialog(step: number, item: any) {
  regenDialog.step = step
  regenDialog.item = item
  regenDialog.itemId = item.id
  const stepNames: Record<number, string> = {
    4: '资产绘图',
    5: '衍生绘图',
    6: '分镜绘图',
    8: '场景视频',
  }
  regenDialog.title = `重新生成${stepNames[step] || ''}`

  // 从对应数据源查找原始描述/时长
  let assetDesc = ''
  let visualDesc = ''
  let duration: number | null = null

  if (step === 4 || step === 5) {
    // 从资产设计列表中查找 assetDesc
    const designs = detail.value.assetDesigns
    if (designs && item.assetId) {
      const matched = designs.find((d: any) => String(d.id) === String(item.assetId))
      if (matched) {
        assetDesc = matched.assetDesc || ''
      }
    }
  } else if (step === 6) {
    // 从分镜脚本列表中查找 visualDesc
    const storyboards = detail.value.storyboards
    if (storyboards && item.sceneIndex != null) {
      const matched = storyboards.find((s: any) => s.sceneIndex === item.sceneIndex)
      if (matched) {
        visualDesc = matched.visualDesc || ''
      }
    }
  } else if (step === 8) {
    // 优先使用现有 SceneVideo 的 duration，兜底从分镜脚本 seq 中查找
    if (typeof item.duration === 'number') {
      duration = Math.round(item.duration)
    } else if (detail.value.storyboards && item.sceneIndex != null) {
      const matched = detail.value.storyboards.find((s: any) => s.sceneIndex === item.sceneIndex)
      if (matched && typeof matched.duration === 'number') {
        duration = matched.duration
      }
    }
    // Agnes 最小 5s 兜底
    if (duration == null || duration < 5) duration = 6
  }

  regenDialog.fields = {
    assetDesc,
    visualDesc,
    duration,
    artStyle: detail.value.artStyle || '',
    visualStyle: detail.value.visualStyle || '',
  }
  regenDialog.visible = true
}

/** 确认单图重生成 */
async function confirmRegenSingle() {
  const taskId = taskIdRef.value
  const itemId = regenDialog.itemId
  const step = regenDialog.step
  if (!taskId || itemId == null || !step) return
  regenSubmitting.value = true

  // 构建参数覆盖对象
  const params: Record<string, any> = {}
  const f = regenDialog.fields
  if (step === 4 || step === 5) {
    if (f.assetDesc) params.assetDesc = f.assetDesc
  } else if (step === 6) {
    if (f.visualDesc) params.visualDesc = f.visualDesc
  } else if (step === 8) {
    if (typeof f.duration === 'number') params.duration = f.duration
  }
  if (f.artStyle) params.artStyle = f.artStyle
  if (f.visualStyle) params.visualStyle = f.visualStyle

  try {
    if (step === 4 || step === 5) {
      await regenerateAssetImage(taskId, String(itemId), params)
    } else if (step === 6) {
      await regenerateStoryboardImage(taskId, String(itemId), params)
    } else if (step === 8) {
      await regenerateSceneVideo(taskId, String(itemId), params)
    }
    ElMessage.success('已提交重新生成')
    regenDialog.visible = false
    loadDetail()
    startPolling(1500, 60000) // 强制轮询 60 秒，避免 PAUSED 状态立即停止
  } catch (e: any) {
    ElMessage.error(e?.message || '重生成失败')
  } finally {
    regenSubmitting.value = false
  }
}

// 自动滚动日志
const logBoxRef = ref<HTMLElement | null>(null)
watch(progressLogs, async () => {
  await nextTick()
  if (logBoxRef.value) {
    logBoxRef.value.scrollTop = logBoxRef.value.scrollHeight
  }
})

// 路由参数变化（如 /task/1 → /task/2）
watch(
  () => route.params.id,
  (val) => {
    taskIdRef.value = String(val || null)
  }
)

// 进度状态同步到当前 detail 状态（让进度条/节点实时更新）
watch(
  [totalProgress, status, currentStep],
  ([p, st, step]) => {
    if (detail.value) {
      detail.value.progress = p
      if (typeof st === 'number') detail.value.status = st
      if (typeof step === 'number') detail.value.currentStep = step
    }
  }
)

// 运行中定时刷新详情：每 5 秒拉取最新 nodeStates/pendingReview，
// 确保步骤状态标签和审核横幅无需手动刷新即可实时更新
const LIVE_REFRESH_INTERVAL = 5000
let _lastLiveRefresh = 0
watch(
  [totalProgress, status, currentStep],
  () => {
    if (status.value !== TaskStatus.RUNNING) return
    const now = Date.now()
    if (now - _lastLiveRefresh >= LIVE_REFRESH_INTERVAL) {
      _lastLiveRefresh = now
      loadDetail(true)
    }
  }
)

loadDetail()
</script>

<style scoped>
.task-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.top-card {
  position: relative;
  padding: 18px 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: hidden;
}

.top-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.head-left {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: transparent;
  border: 1.5px solid var(--cd-border);
  border-radius: 6px;
  cursor: pointer;
  color: var(--cd-text);
  font-weight: 600;
  font-family: inherit;
}
.back-btn:hover {
  background: var(--cd-bg-soft);
}

.title-block .title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
}

.sub-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.sub-item {
  padding: 2px 8px;
  border: 1px dashed var(--cd-border);
  border-radius: 4px;
}
.sub-item--muted {
  color: var(--cd-text-secondary);
  background: var(--cd-bg-soft);
  font-style: italic;
}

.head-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.progress-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.time-row {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--cd-text-secondary);
  flex-wrap: wrap;
}

.step-line {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-top: 4px;
}

.step-node {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.step-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid var(--cd-border);
  background: var(--cd-bg-card);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 13px;
  color: var(--cd-text-secondary);
  transition: all 0.25s;
  z-index: 2;
}

.step-label {
  font-size: 12px;
  color: var(--cd-text-secondary);
  text-align: center;
}

.step-node.done .step-index {
  background: var(--cd-primary);
  color: #fff;
  border-color: var(--cd-primary);
}
.step-node.done .step-label {
  color: var(--cd-text);
}

.step-node.active .step-index {
  background: var(--cd-warning);
  color: #fff;
  border-color: var(--cd-warning);
  animation: pulse 1.6s ease-in-out infinite;
}
.step-node.active .step-label {
  color: var(--cd-warning);
  font-weight: 700;
}

.step-node.failed .step-index {
  background: var(--cd-danger, #f56c6c);
  color: #fff;
  border-color: var(--cd-danger, #f56c6c);
  animation: failed-pulse 1.2s ease-in-out infinite;
}
.step-node.failed .step-label {
  color: var(--cd-danger, #f56c6c);
  font-weight: 700;
}

.step-node.paused .step-index {
  background: var(--cd-text-secondary);
  color: #fff;
  border-color: var(--cd-text-secondary);
}
.step-node.paused .step-label {
  color: var(--cd-text-secondary);
  font-weight: 600;
}

.step-node.skipped .step-index {
  background: var(--cd-bg-soft);
  color: var(--cd-text-secondary);
  border-color: var(--cd-text-secondary);
}
.step-node.skipped .step-label {
  color: var(--cd-text-secondary);
  text-decoration: line-through;
}
.step-node.skipped .step-label .skipped-label {
  margin-left: 4px;
  font-size: 10px;
  font-weight: 500;
  color: var(--cd-text-secondary);
  background: var(--cd-bg-soft);
  padding: 1px 5px;
  border-radius: 6px;
  text-decoration: none;
}

.step-label .failed-label {
  margin-left: 4px;
  font-size: 10px;
  font-weight: 600;
  color: var(--cd-danger, #f56c6c);
  background: rgba(245, 108, 108, 0.12);
  padding: 1px 5px;
  border-radius: 6px;
}

.step-label .paused-label {
  margin-left: 4px;
  font-size: 10px;
  font-weight: 500;
  color: var(--cd-text-secondary);
  background: var(--cd-bg-soft);
  padding: 1px 5px;
  border-radius: 6px;
}

.step-label .sub-state-label {
  margin-left: 3px;
  font-size: 9px;
  font-weight: 600;
  padding: 0 4px;
  border-radius: 4px;
  color: #fff;
}
.step-label .test-label {
  background: var(--cd-warning, #e6a23c);
}
.step-label .batch-label {
  background: var(--cd-primary, #409eff);
}

.step-line-bar {
  position: absolute;
  top: 14px;
  left: 58%;
  width: 100%;
  height: 2px;
  background: var(--cd-border);
  z-index: 1;
}
.step-line-bar.done {
  background: var(--cd-primary);
}
.step-line-bar.active {
  background: var(--cd-warning);
}
.step-line-bar.failed {
  background: var(--cd-danger, #f56c6c);
}
.step-line-bar.paused {
  background: var(--cd-text-secondary);
  border-top: 1px dashed var(--cd-text-secondary);
}
.step-line-bar.skipped {
  background: var(--cd-text-secondary);
  border-top: 1px dashed var(--cd-text-secondary);
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(0,0,0,0.2); }
  50% { box-shadow: 0 0 0 6px rgba(0,0,0,0); }
}

@keyframes failed-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.35); }
  50% { box-shadow: 0 0 0 6px rgba(245, 108, 108, 0); }
}

.deco {
  position: absolute;
  right: 12px;
  top: 10px;
  pointer-events: none;
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.left-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: 16px;
}

.prod-card,
.side-card {
  padding: 16px 18px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.card-head-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.card-head-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.artifact-summary {
  font-size: 12px;
  color: var(--el-color-primary);
  font-weight: 500;
}

.artifact-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.card-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 审核暂停横幅 */
.review-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: linear-gradient(90deg, var(--el-color-success-light-9, #e1f3d8), var(--cd-bg-soft));
  border: 1px solid var(--el-color-success-light-7, #95d475);
  border-radius: 8px;
}
.review-banner-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.review-icon {
  font-size: 22px;
  color: var(--el-color-success, #67c23a);
  flex-shrink: 0;
}
.review-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--cd-text);
}
.review-tip {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 2px;
}

/* 大纲 */
.outline-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.outline-summary {
  margin: 0;
  padding: 10px 12px;
  background: var(--cd-bg-soft);
  border-left: 3px solid var(--cd-primary);
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
}
.outline-text {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  color: var(--cd-text);
}
/* 大纲正/负面提示词 */
.outline-prompts {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ol-prompt-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 10px;
  background: var(--cd-bg-soft);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
}
.ol-prompt-label {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--cd-text-secondary);
  white-space: nowrap;
}
.ol-prompt-value {
  word-break: break-word;
  color: var(--cd-text);
}

/* 分镜分组区块 */
.group-block {
  margin-bottom: 10px;
  overflow: hidden;
}
.group-block:last-child {
  margin-bottom: 0;
}
.group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
}
.group-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  padding: 1px 6px;
  background: var(--cd-primary);
  color: #fff;
  border-radius: 4px;
  font-weight: 700;
  font-size: 12px;
}
.group-title-sm {
  font-weight: 600;
  color: var(--cd-text);
}
.group-meta-sm {
  margin-left: auto;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

/* 分镜脚本 - div 网格表格 */
.sb-grid {
  display: flex;
  align-items: stretch;
  font-size: 12px;
  width: 100%;
  overflow: hidden;
  box-sizing: border-box;
  table-layout: fixed;
}
.sb-grid-header {
  background: var(--cd-bg-soft);
  color: var(--cd-text);
  font-weight: 600;
}
.sb-grid-body {
  transition: background 0.15s;
  overflow: hidden;
}
.sb-grid-body:nth-child(even) {
  background: var(--cd-bg-soft);
}
.sb-grid-body:hover {
  background: var(--cd-bg-soft);
}
.sb-cell {
  padding: 4px 8px;
  box-sizing: border-box;
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sb-cell-last {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-right: none;
}
/* 列宽 */
.sb-w-dur { width: 68px; justify-content: center; }
.sb-w-angle { width: 76px; }
.sb-w-shot { width: 120px; }
.sb-w-scene { width: 96px; }
.sb-w-char { width: 96px; }
.sb-w-props { width: 96px; }
.sb-w-desc { width: 140px; }
.sb-w-dlg { width: 130px; }
.sb-w-visual { width: 160px; }
.sb-c-dur { justify-content: center; }

/* 展开模式：画面描述列弹性伸缩以允许多行显示 */
.table-expanded .sb-w-visual {
  flex: 1;
  min-width: 120px;
}

/* 展开模式：文本多行显示 */
.table-expanded .sb-cell,
.table-expanded .ad-cell {
  white-space: normal;
  word-break: break-word;
  align-items: flex-start;
  overflow: visible;
  text-overflow: clip;
}
.table-expanded .sb-cell-last,
.table-expanded .ad-cell-last {
  overflow: visible;
  text-overflow: clip;
}
.table-expanded .sb-grid,
.table-expanded .ad-grid {
  overflow: visible;
}

/* 资产设计 - div 网格表格 */
.ad-grid {
  display: flex;
  align-items: stretch;
  font-size: 12px;
  width: 100%;
  overflow: hidden;
  box-sizing: border-box;
  table-layout: fixed;
}
.ad-grid-header {
  background: var(--cd-bg-soft);
  color: var(--cd-text);
  font-weight: 600;
}
.ad-grid-body {
  transition: background 0.15s;
  overflow: hidden;
}
.ad-grid-body:nth-child(even) {
  background: var(--cd-bg-soft);
}
.ad-grid-body:hover {
  background: var(--cd-bg-soft);
}
.ad-cell {
  padding: 4px 8px;
  box-sizing: border-box;
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ad-cell-last {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 列宽 */
.ad-w-type { width: 88px; }
.ad-w-name { width: 118px; }
.ad-w-from { width: 110px; }
.ad-w-ver { width: 60px; justify-content: center; border-right: none; }
.ad-c-ver { justify-content: center; }

/* 分组（原 collapse） */
.group-collapse {
  border: none;
}
.group-collapse :deep(.el-collapse-item__header) {
  border-bottom: 1px dashed var(--cd-border);
  font-weight: 600;
  font-size: 13px;
}
.group-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 12px;
  color: var(--cd-text-secondary);
}
.group-title {
  margin-right: 8px;
}
.group-meta {
  color: var(--cd-text-secondary);
  font-weight: 400;
}
.group-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

/* 提示词 */
.prompt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
}
.prompt-item {
  padding: 10px 12px;
  border: 1.5px dashed var(--cd-border);
  border-radius: 6px;
  background: var(--cd-bg-soft);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.prompt-head {
  display: flex;
  gap: 6px;
  align-items: center;
}
.prompt-tag {
  font-weight: 700;
  color: var(--cd-primary);
}
.prompt-type {
  font-size: 12px;
  color: var(--cd-text-secondary);
  padding: 1px 6px;
  border: 1px solid var(--cd-border);
  border-radius: 4px;
}
.prompt-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--cd-text);
}
.prompt-neg {
  margin: 0;
  font-size: 12px;
  color: var(--cd-text-secondary);
}
.neg-label {
  color: var(--cd-danger);
  font-weight: 600;
}

/* 图片 */
.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}
.image-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.img-actions {
  position: absolute;
  top: 4px;
  right: 4px;
  z-index: 3;
}
.regen-single-btn {
  background: var(--cd-bg-card);
  border: 1.5px solid var(--cd-border);
  border-radius: 4px;
  padding: 2px 6px !important;
  font-size: 11px !important;
  font-weight: 600;
  color: var(--cd-text);
}
.regen-single-btn:disabled {
  background: var(--cd-bg-soft);
  opacity: 0.55;
}
.thumb {
  width: 100%;
  aspect-ratio: 16 / 9;
  border: 1.5px solid var(--cd-border);
  border-radius: 4px;
  overflow: hidden;
}
.image-caption {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--cd-text-secondary);
}
.cap-sub {
  opacity: 0.7;
}

/* 音频 */
.skipped-tip {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  background: var(--cd-bg-soft);
  border: 1px dashed var(--cd-border);
  border-radius: 6px;
  font-size: 13px;
  color: var(--cd-text-secondary);
  line-height: 1.6;
}
.skipped-tip .el-icon {
  font-size: 18px;
  margin-top: 1px;
  flex-shrink: 0;
}
.audio-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.audio-item {
  padding: 10px 12px;
  border: 1px solid var(--cd-border);
  border-radius: 6px;
  background: var(--cd-bg-soft);
}
.audio-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12px;
}
.audio-tag {
  font-weight: 700;
  color: var(--cd-primary);
}
.audio-role {
  font-weight: 600;
}
.audio-dur {
  color: var(--cd-text-secondary);
  margin-left: auto;
}
.audio-player {
  width: 100%;
  height: 32px;
}

/* 视频 */
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.video-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.vid-actions {
  position: absolute;
  top: 4px;
  right: 4px;
  z-index: 3;
}
.video-player {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
  border: 1.5px solid var(--cd-border);
  border-radius: 4px;
}
.video-caption {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

/* 连接点 */
.conn-dot {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  border: 1px solid var(--cd-border);
  color: var(--cd-text-secondary);
}
.conn-dot.ws {
  background: var(--cd-success);
  color: #fff;
  border-color: var(--cd-success);
}
.conn-dot.polling {
  background: var(--cd-warning);
  color: #fff;
  border-color: var(--cd-warning);
}

/* 进度日志 */
.log-box {
  max-height: 260px;
  overflow-y: auto;
  padding: 8px 10px;
  background: var(--cd-bg-soft);
  border: 1px dashed var(--cd-border);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  font-family: Consolas, Monaco, monospace;
}
.log-line {
  display: grid;
  grid-template-columns: 92px 80px 1fr;
  gap: 6px;
  color: var(--cd-text);
  line-height: 1.5;
}
.log-time {
  color: var(--cd-text-secondary);
}
.log-step {
  color: var(--cd-primary);
  font-weight: 600;
}
.log-empty {
  color: var(--cd-text-secondary);
  text-align: center;
  padding: 12px 0;
}

/* 失败摘要条 */
.fail-bar {
  margin-top: 12px;
  border: 2px solid var(--cd-danger);
  border-radius: 10px;
  overflow: hidden;
  background: var(--cd-bg-card);
}
.fail-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(245, 108, 108, 0.08);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.fail-summary:hover {
  background: var(--cd-bg-soft);
}
.fail-summary-text {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
  font-size: 14px;
  line-height: 1.5;
}
.fail-label {
  font-weight: 700;
  color: var(--cd-danger);
}
.fail-step {
  padding: 2px 8px;
  background: var(--cd-danger);
  color: #fff;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.fail-reason {
  color: var(--cd-text);
  word-break: break-all;
}
.fail-toggle {
  font-size: 16px;
  color: var(--cd-text-secondary);
  transition: transform 0.2s;
  flex-shrink: 0;
}
.fail-toggle.expanded {
  transform: rotate(180deg);
}
.fail-icon {
  font-size: 22px;
  color: var(--cd-danger);
  flex-shrink: 0;
}
.fail-detail {
  padding: 0 16px 12px;
  border-top: 1px dashed var(--cd-border);
}
.fail-stack {
  margin: 10px 0 0;
  padding: 8px 10px;
  background: var(--cd-bg-soft);
  border-radius: 6px;
  font-size: 12px;
  color: var(--cd-text-secondary);
  max-height: 160px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.fail-logs {
  margin-top: 10px;
}
.fail-logs-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--cd-text-secondary);
  margin-bottom: 6px;
}
.fail-log-item {
  padding: 8px 10px;
  background: var(--cd-bg-soft);
  border-left: 3px solid var(--cd-danger);
  border-radius: 4px;
  margin-bottom: 6px;
}
.fail-log-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 4px;
}
.fail-log-step {
  font-weight: 600;
  color: var(--cd-danger);
}
.fail-log-time {
  color: var(--cd-text-secondary);
}
.fail-log-msg {
  margin: 0;
  font-size: 13px;
  color: var(--cd-text);
  line-height: 1.5;
}
.fail-log-stack {
  margin: 4px 0 0;
  padding: 6px 8px;
  background: var(--cd-bg);
  border-radius: 4px;
  font-size: 11px;
  color: var(--cd-text-secondary);
  max-height: 100px;
  overflow-y: auto;
  white-space: pre-wrap;
}

@media (max-width: 1100px) {
  .main-grid {
    grid-template-columns: 1fr;
  }
  .right-col {
    position: static;
  }
}

/* ======== 人工审核：点击行可编辑 + 重生成对话框 ======== */
.editable-body {
  cursor: pointer;
  transition: background 0.15s;
  position: relative;
}
.editable-body:hover {
  background: var(--cd-bg-soft, #f5f5f5);
  border-radius: 4px;
}
.edit-hint {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 11px;
  color: var(--cd-primary, #409eff);
  opacity: 0.7;
  pointer-events: none;
}

/* 可点击行样式 */
.row-clickable {
  cursor: pointer;
  transition: background 0.15s;
}
.row-clickable:hover {
  background: var(--cd-bg-soft);
}
.row-clickable:active {
  background: var(--cd-bg-soft);
}

/* 重生成对话框 */
.regen-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.regen-preview {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 12px;
  background: var(--cd-bg-soft, #f5f5f5);
  border-radius: 8px;
  border: 1px solid var(--cd-border, #e0e0e0);
}
.regen-thumb {
  width: 100px;
  height: 100px;
  border-radius: 6px;
  flex-shrink: 0;
}
.regen-video-thumb {
  width: 180px;
  height: 101px; /* 16:9 */
  background: #000;
  object-fit: cover;
}
.regen-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}
.regen-label {
  color: var(--cd-text-secondary, #888);
  font-weight: 500;
  display: inline;
  margin-right: 6px;
}
.regen-value {
  color: var(--cd-text, #333);
  font-weight: 600;
  display: inline;
}
.regen-tip {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  background: rgba(230, 162, 60, 0.08);
  border: 1px solid rgba(230, 162, 60, 0.3);
  border-radius: 6px;
  font-size: 13px;
  color: var(--cd-text-secondary, #666);
  line-height: 1.5;
}
.regen-tip .el-icon {
  color: var(--cd-warning, #e6a23c);
  margin-top: 2px;
  flex-shrink: 0;
}
.regen-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 12px;
  background: var(--cd-bg-soft, #f9f9f9);
  border-radius: 8px;
  border: 1px solid var(--cd-border, #e0e0e0);
}
.regen-form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.regen-form-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--cd-text-secondary, #666);
}
.regen-form-item .el-textarea,
.regen-form-item .el-select {
  width: 100%;
}
.regen-style-row {
  display: flex;
  gap: 10px;
}
.regen-style-select {
  flex: 1;
}
.regen-style-preview {
  font-size: 12px;
  color: var(--cd-text-secondary, #888);
  margin-top: 4px;
  padding: 6px 10px;
  background: var(--cd-bg-soft, #f5f7fa);
  border-radius: 6px;
  border: 1px dashed var(--cd-border, #dcdfe6);
}
.regen-hint {
  font-size: 11px;
  color: var(--cd-text-secondary, #999);
  margin-top: 2px;
  line-height: 1.6;
}

/* ⑨ 成片下载卡片 */
.final-card {
  border: 2px solid var(--el-color-success-light-5, #95d475);
}
.final-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 16px 18px;
}
.final-info {
  display: flex;
  gap: 16px;
  align-items: center;
  flex: 1;
  min-width: 0;
}
.final-cover {
  flex-shrink: 0;
}
.final-cover img {
  width: 96px;
  height: 96px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--cd-border, #dcdfe6);
  display: block;
}
.final-meta {
  min-width: 0;
  flex: 1;
}
.final-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--cd-text, #303133);
}
.final-stats {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--cd-text-secondary, #909399);
  margin-bottom: 6px;
  align-items: center;
  flex-wrap: wrap;
}
.final-desc {
  font-size: 12px;
  color: var(--cd-text-secondary, #909399);
  line-height: 1.5;
}
.final-actions {
  display: flex;
  align-items: stretch;
  gap: 12px;
  flex-shrink: 0;
  padding: 4px 0 4px 16px;
  border-left: 1px dashed var(--cd-border, #e4e7ed);
  min-height: 56px;
}
/* ======== 成片操作按钮（播放 + 下载，高度完全一致）======== */
.final-play-btn,
.final-download-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 4px 14px !important;
  line-height: 1.4 !important;
  font-size: 13px !important;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  border-radius: 6px;
  min-height: 28px;
  height: auto;
  box-sizing: border-box;
  background: transparent !important;
  border-width: 2px !important;
  border-style: solid !important;
  transition: all 0.2s;
}
.final-play-btn {
  min-width: 110px;
  border-color: var(--cd-primary) !important;
  color: var(--cd-primary) !important;
}
.final-play-btn:hover {
  background: var(--cd-bg-soft) !important;
  border-color: var(--cd-primary-hover) !important;
  color: var(--cd-primary-hover) !important;
}
.final-download-btn {
  min-width: 160px;
  border-color: var(--cd-success) !important;
  color: var(--cd-success) !important;
}
.final-download-btn:hover {
  background: var(--cd-bg-soft) !important;
  text-decoration: none;
}
.final-download-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
  background: transparent !important;
}

/* ======== 视频播放器模态框 ======== */
.player-container {
  display: flex;
  gap: 20px;
  min-height: 400px;
}
.player-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.player-video {
  width: 100%;
  max-height: 480px;
  background-color: #000;
  border-radius: 8px;
}
.player-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--cd-bg-soft);
  border-radius: 6px;
  gap: 12px;
}
.ctrl-left {
  display: flex;
  gap: 6px;
  align-items: center;
}
/* 播放器控制栏 text 按钮：启用=原色，禁用=灰色变淡，避免颜色搞反 */
.ctrl-left .el-button.is-text:not(.is-disabled),
.ctrl-right .el-button.is-text:not(.is-disabled) {
  color: var(--cd-text) !important;
  font-weight: 600;
}
.ctrl-left .el-button.is-text:hover:not(.is-disabled),
.ctrl-right .el-button.is-text:hover:not(.is-disabled) {
  color: var(--cd-primary) !important;
}
.ctrl-left .el-button.is-text.is-disabled,
.ctrl-right .el-button.is-text.is-disabled {
  color: var(--cd-text-secondary) !important;
  opacity: 0.45 !important;
  font-weight: 500;
}
.ctrl-center {
  flex: 1;
  text-align: center;
}
.ctrl-right {
  display: flex;
  gap: 6px;
  align-items: center;
}
.player-now-playing {
  font-size: 13px;
  color: var(--cd-text);
  font-weight: 500;
}
.player-dur {
  color: var(--cd-text-secondary);
  margin-left: 6px;
}
.player-sidebar {
  width: 240px;
  border-left: 1px solid var(--cd-border);
  padding-left: 12px;
  overflow-y: auto;
  max-height: 520px;
  display: flex;
  flex-direction: column;
}
.player-sidebar-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--cd-text);
}
.player-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.player-list-item {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  background: var(--cd-bg-card);
  border: 1px solid var(--cd-border);
  transition: all 0.15s;
  font-size: 12px;
  user-select: none;
}
.player-list-item:hover {
  background: var(--cd-bg-soft);
  border-color: var(--cd-primary);
}
.player-list-item.active {
  background: var(--cd-bg-soft);
  border-color: var(--cd-primary);
  font-weight: 600;
}
.player-list-order {
  font-weight: 700;
  margin-right: 8px;
  color: var(--cd-text);
  min-width: 24px;
}
.player-list-name {
  flex: 1;
  color: var(--cd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.player-list-dur {
  color: var(--cd-text-secondary);
  font-size: 11px;
  flex-shrink: 0;
  margin-right: 6px;
}
.player-list-playing-indicator {
  color: var(--cd-primary);
  font-size: 10px;
}

/* 播放器对话框暗色主题适配 */
.player-dialog .el-dialog {
  background: var(--cd-bg-card);
  color: var(--cd-text);
}
.player-dialog .el-dialog__title {
  color: var(--cd-text);
}
.player-dialog .el-dialog__body {
  background: var(--cd-bg-card);
}
.player-dialog .el-dialog__footer {
  background: var(--cd-bg-card);
}

/* 下拉菜单激活项 */
.el-dropdown-menu__item.is-active {
  color: var(--cd-primary);
  font-weight: 600;
}
</style>
