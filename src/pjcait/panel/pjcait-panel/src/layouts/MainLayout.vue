<template>
  <q-layout view="hHh lpR fFf">
    <q-header bordered class="bg-primary text-white">
      <q-toolbar>
        <q-toolbar-title class="select-none"> pjcait - demo面板 </q-toolbar-title>

        <q-tabs align="left">
          <q-route-tab to="/" label="概览" />
          <q-route-tab to="/job" label="自动更新任务" />
          <q-route-tab to="/collector" label="数据收集" />
          <q-route-tab to="/processor" label="数据处理" />
<!--          <q-route-tab to="/" label="已存储数据" />-->
        </q-tabs>
      </q-toolbar>
    </q-header>

    <q-page-container class="q-pt-md">
      <q-page class="flex">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component"></component>
          </transition>
        </router-view>
      </q-page>
    </q-page-container>
  </q-layout>
</template>

<script setup>
import { ref } from 'vue'

const model = ref('Google')
const options = ['Google', 'Facebook', 'Twitter', 'Apple', 'Oracle']
</script>

<style scoped lang="sass"></style>
