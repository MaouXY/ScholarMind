<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { requestFailures } from '@/utils/requestState'

const route = useRoute()
const messages = computed(() => [...new Set(requestFailures.values())])
watch(() => route.fullPath, () => requestFailures.clear())
function reload() {
  window.location.reload()
}
</script>

<template>
  <section v-if="messages.length" class="request-failure" role="alert">
    <div>
      <strong>{{ messages.join('；') }}</strong>
      <p>部分内容暂时无法获取，请重试。</p>
    </div>
    <el-button :icon="Refresh" @click="reload">重新加载</el-button>
  </section>
</template>
