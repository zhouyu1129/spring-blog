<script setup lang="ts">
import Navbar from '@/components/Navbar.vue'
import Footer from '@/components/AppFooter.vue'
import MessageAlert from '@/components/MessageAlert.vue'
import { useMessages } from '@/composables/useMessages'

const { messages, removeMessage } = useMessages()
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <Navbar />
    <MessageAlert :messages="messages" @remove="removeMessage" />
    <main class="flex-1 container mx-auto px-4 py-6">
      <router-view />
    </main>
    <Footer />
  </div>
</template>
