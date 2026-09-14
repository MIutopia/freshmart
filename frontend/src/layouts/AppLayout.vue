<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore, CONSOLE_LABEL, type ConsoleKey } from '../stores/auth'
import { CONSOLE_NAV } from '../router/nav'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const activeConsole = computed<ConsoleKey>(() => (route.meta.console as ConsoleKey) ?? 'app')
const navGroups = computed(() => CONSOLE_NAV[activeConsole.value])
const availableConsoles = computed(() => auth.allowedConsoles())
const roleText = computed(() => auth.roles.join(' / '))

async function signOut() {
  await auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="brand"><span class="mark">叶</span><span>鲜达生鲜</span></div>

      <nav v-if="availableConsoles.length > 1" class="console-switch" aria-label="切换业务端">
        <button
          v-for="key in availableConsoles"
          :key="key"
          :class="{ active: key === activeConsole }"
          @click="router.push(`/${key}`)"
        >
          {{ CONSOLE_LABEL[key] }}
        </button>
      </nav>
      <span v-else class="muted">{{ CONSOLE_LABEL[activeConsole] }}</span>

      <div class="header-right">
        <span class="who">{{ auth.loginName }}<template v-if="roleText"> · {{ roleText }}</template></span>
        <button class="btn sm" @click="signOut">退出</button>
      </div>
    </header>

    <div class="app-body">
      <aside class="app-sidebar">
        <div v-for="group in navGroups" :key="group.title" class="group">
          <div class="group-title">{{ group.title }}</div>
          <router-link v-for="item in group.items" :key="item.path" class="nav-link" :to="item.path">
            {{ item.label }}
          </router-link>
        </div>
      </aside>

      <main class="app-main">
        <router-view />
      </main>
    </div>
  </div>
</template>
