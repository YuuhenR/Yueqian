/// <reference types="vite/client" />

type BrowserSpeechRecognition = new () => {
  lang: string
  onresult: ((event: any) => void) | null
  onerror?: ((event: any) => void) | null
  start: () => void
}

interface Window {
  SpeechRecognition?: BrowserSpeechRecognition
  webkitSpeechRecognition?: BrowserSpeechRecognition
}
