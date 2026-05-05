/// <reference types="vite/client" />

declare module 'mespeak' {
  type SpeakOptions = {
    amplitude?: number;
    pitch?: number;
    speed?: number;
    voice?: string;
    wordgap?: number;
    volume?: number;
    callback?: (success: boolean) => void;
  };

  const meSpeak: {
    speak: (text: string, options?: SpeakOptions) => number;
    loadConfig: (data: object) => void;
    loadVoice: (data: object) => void;
    setDefaultVoice: (voice: string) => void;
    resetQueue: () => void;
    stop: () => number;
    canPlay: () => boolean;
  };

  export default meSpeak;
}

declare module 'mespeak/src/mespeak_config.json' {
  const value: object;
  export default value;
}

declare module 'mespeak/voices/de.json' {
  const value: object;
  export default value;
}

declare module 'mespeak/voices/en/en-us.json' {
  const value: object;
  export default value;
}
