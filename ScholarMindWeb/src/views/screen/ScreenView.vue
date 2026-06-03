<script setup lang="ts">
const trend = [
  { label: '周一', value: 42 },
  { label: '周二', value: 56 },
  { label: '周三', value: 72 },
  { label: '周四', value: 64 },
  { label: '周五', value: 88 },
  { label: '周六', value: 51 },
]

const domains = [
  { label: '信息检索', value: 84 },
  { label: '人工智能', value: 72 },
  { label: '数字图书馆', value: 61 },
  { label: '推荐系统', value: 47 },
]
</script>

<template>
  <section class="analytics-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">Reading Intelligence</p>
        <h2>观察文献、阅读与知识库的运行状态</h2>
        <p>
          总览页面用于展示平台第二阶段的核心指标：文献增长、阅读活跃、审核压力和 RAG 分块健康度。
        </p>
      </div>
    </div>

    <div class="kpi-grid">
      <article class="screen-kpi">
        <p>公开文献</p>
        <strong>128</strong>
        <span>较上周 +18</span>
      </article>
      <article class="screen-kpi">
        <p>今日阅读</p>
        <strong>346</strong>
        <span>平均停留 8.4 分钟</span>
      </article>
      <article class="screen-kpi">
        <p>待审核</p>
        <strong>9</strong>
        <span>最长等待 14 小时</span>
      </article>
      <article class="screen-kpi">
        <p>知识分块</p>
        <strong>1,426</strong>
        <span>可用率 97%</span>
      </article>
    </div>

    <div class="analytics-grid">
      <section class="glass-panel chart-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Weekly Reads</p>
            <h3>阅读趋势</h3>
          </div>
        </div>
        <div class="bar-chart" aria-label="每周阅读趋势">
          <div v-for="item in trend" :key="item.label" class="bar-chart__item">
            <span class="bar-chart__bar" :style="{ height: `${item.value}%` }" />
            <strong>{{ item.value }}</strong>
            <small>{{ item.label }}</small>
          </div>
        </div>
      </section>

      <section class="glass-panel chart-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Domain Heat</p>
            <h3>热门研究方向</h3>
          </div>
        </div>
        <div class="domain-bars">
          <div v-for="item in domains" :key="item.label">
            <span>{{ item.label }}</span>
            <div><i :style="{ width: `${item.value}%` }" /></div>
            <strong>{{ item.value }}%</strong>
          </div>
        </div>
      </section>

      <section class="glass-panel ops-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Operational Signals</p>
            <h3>平台运行信号</h3>
          </div>
        </div>
        <ul class="signal-list">
          <li><strong>检索服务</strong><span>响应稳定，P95 约 420ms</span></li>
          <li><strong>RAG 重建</strong><span>最近 24 小时 6 次，均成功</span></li>
          <li><strong>审核流转</strong><span>公开文献平均 2.1 小时完成审核</span></li>
          <li><strong>阅读质量</strong><span>摘要展开率 76%，笔记使用率 42%</span></li>
        </ul>
      </section>
    </div>
  </section>
</template>
