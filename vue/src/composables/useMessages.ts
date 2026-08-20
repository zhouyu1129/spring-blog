import { ref } from 'vue'

interface Message {
  id: number
  text: string
  type: 'success' | 'error' | 'warning' | 'info'
}

const messages = ref<Message[]>([])
let nextId = 0

export function useMessages() {
  function addMessage(text: string, type: Message['type'] = 'info') {
    const id = nextId++
    messages.value.push({ id, text, type })
    setTimeout(() => {
      removeMessage(id)
    }, 5000)
  }

  function removeMessage(id: number) {
    messages.value = messages.value.filter(m => m.id !== id)
  }

  return {
    messages,
    addMessage,
    removeMessage,
  }
}
