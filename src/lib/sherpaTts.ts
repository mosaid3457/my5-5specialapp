import { Capacitor, registerPlugin } from '@capacitor/core';

export interface SherpaVoice {
  voiceURI: string;
  name: string;
  lang: string;
  localService: boolean;
  gender?: 'male' | 'female';
  speakerId: number;
}

interface SherpaTtsPlugin {
  getVoices(): Promise<{ voices: SherpaVoice[] }>;
  speak(options: {
    text: string;
    lang: string;
    voiceURI?: string;
    speakerId?: number;
    speed?: number;
  }): Promise<void>;
  stop(): Promise<void>;
}

const SherpaTts = registerPlugin<SherpaTtsPlugin>('SherpaTts');

export const isSherpaTtsSupported = (): boolean =>
  Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';

export const getSherpaVoices = async (): Promise<SherpaVoice[]> => {
  if (!isSherpaTtsSupported()) return [];
  try {
    const result = await SherpaTts.getVoices();
    return result.voices || [];
  } catch {
    return [];
  }
};

export const speakSherpa = async (options: {
  text: string;
  lang: string;
  voiceURI?: string;
  rate?: number;
}): Promise<boolean> => {
  if (!isSherpaTtsSupported()) return false;
  try {
    await SherpaTts.speak({
      text: options.text,
      lang: options.lang,
      voiceURI: options.voiceURI,
      speed: options.rate ?? 1,
    });
    return true;
  } catch {
    return false;
  }
};

export const stopSherpa = async (): Promise<void> => {
  if (!isSherpaTtsSupported()) return;
  try {
    await SherpaTts.stop();
  } catch {
    // Native stop is best-effort.
  }
};
