import { createRouter, createWebHashHistory } from 'vue-router'
import StatusView from './views/StatusView.vue'
import SchedulesView from './views/SchedulesView.vue'
import PersonasView from './views/PersonasView.vue'
import BlueprintsView from './views/BlueprintsView.vue'
import MemoryView from './views/MemoryView.vue'
import ChatView from './views/ChatView.vue'
import CollectionView from './views/CollectionView.vue'
import OverviewView from './views/OverviewView.vue'
import MonitoringView from './views/MonitoringView.vue'

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/status' },
    { path: '/status', name: 'status', component: StatusView, meta: { title: '运行状态' } },
    { path: '/overview', name: 'overview', component: OverviewView, meta: { title: '总览' } },
    { path: '/monitoring', name: 'monitoring', component: MonitoringView, meta: { title: '监控' } },
    { path: '/chat', name: 'chat', component: ChatView, meta: { title: '对话' } },
    { path: '/schedules', name: 'schedules', component: SchedulesView, meta: { title: '定时任务' } },
    { path: '/collection', name: 'collection', component: CollectionView, meta: { title: '采集' } },
    { path: '/personas', name: 'personas', component: PersonasView, meta: { title: '人设与提示词' } },
    { path: '/blueprints', name: 'blueprints', component: BlueprintsView, meta: { title: '消息蓝图' } },
    { path: '/memory', name: 'memory', component: MemoryView, meta: { title: '记忆' } }
  ]
})
