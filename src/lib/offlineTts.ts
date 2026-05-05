import meSpeak from 'mespeak';
import config from 'mespeak/src/mespeak_config.json';
import deVoice from 'mespeak/voices/de.json';
import enUsVoice from 'mespeak/voices/en/en-us.json';
import type { SpeakOptions, TTSVoice } from './tts';

type MeSpeakOptions = {
  amplitude?: number;
  pitch?: number;
  speed?: number;
  voice?: string;
  wordgap?: number;
  volume?: number;
  callback?: (success: boolean) => void;
};

type MeSpeak = {
  speak: (text: string, options?: MeSpeakOptions) => number;
  loadConfig: (data: object) => void;
  loadVoice: (data: object) => void;
  setDefaultVoice: (voice: string) => void;
  resetQueue: () => void;
  stop: () => number;
  canPlay: () => boolean;
};

type OfflineVoice = TTSVoice & {
  mespeakVoice: string;
  pitch?: number;
  speedMultiplier?: number;
};

const offlineVoices: OfflineVoice[] = [
  {
    voiceURI: 'offline-espeak-en-us',
    name: 'Offline American English',
    lang: 'en-US',
    localService: true,
    mespeakVoice: 'en/en-us',
  },
  {
    voiceURI: 'offline-espeak-en-us-male',
    name: 'Wavenet-B',
    lang: 'en-US',
    localService: true,
    gender: 'male',
    mespeakVoice: 'en/en-us',
    pitch: 32,
    speedMultiplier: 0.96,
  },
  {
    voiceURI: 'offline-espeak-en-us-female',
    name: 'Wavenet-C',
    lang: 'en-US',
    localService: true,
    gender: 'female',
    mespeakVoice: 'en/en-us',
    pitch: 68,
    speedMultiplier: 1.04,
  },
  {
    voiceURI: 'offline-espeak-de',
    name: 'Offline German',
    lang: 'de-DE',
    localService: true,
    mespeakVoice: 'de',
  },
  {
    voiceURI: 'offline-espeak-de-male',
    name: 'Conrad',
    lang: 'de-DE',
    localService: true,
    gender: 'male',
    mespeakVoice: 'de',
    pitch: 34,
    speedMultiplier: 0.96,
  },
  {
    voiceURI: 'offline-espeak-de-female',
    name: 'Katja',
    lang: 'de-DE',
    localService: true,
    gender: 'female',
    mespeakVoice: 'de',
    pitch: 68,
    speedMultiplier: 1.04,
  },
];

const offlineTts = meSpeak as MeSpeak;
let initialized = false;

const stripHtmlForSpeech = (html: string): string => {
  if (!html) return '';
  if (typeof document !== 'undefined') {
    const div = document.createElement('div');
    div.innerHTML = html;
    const text = (div.textContent || div.innerText || '').replace(/\[sound:[^\]]+\]/gi, '');
    return text.replace(/\s+/g, ' ').trim();
  }
  return html
    .replace(/<[^>]+>/g, ' ')
    .replace(/\[sound:[^\]]+\]/gi, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/\s+/g, ' ')
    .trim();
};

const normalizeLangTag = (lang: string): string => {
  if (!lang) return lang;
  const parts = lang.replace('_', '-').split('-');
  const primary = parts[0]?.toLowerCase();
  const region = parts[1]?.toUpperCase();
  return primary && region ? `${primary}-${region}` : primary || lang;
};

const langMatches = (requested: string, candidate: string): boolean => {
  const want = normalizeLangTag(requested).toLowerCase();
  const got = normalizeLangTag(candidate).toLowerCase();
  return want === got || want.split('-')[0] === got.split('-')[0];
};

const ensureInitialized = (): boolean => {
  if (initialized) return true;
  if (!offlineTts.canPlay()) return false;
  offlineTts.loadConfig(config);
  offlineTts.loadVoice(enUsVoice);
  offlineTts.loadVoice(deVoice);
  initialized = true;
  return true;
};

export const getOfflineVoices = (): TTSVoice[] => {
  return offlineVoices.map(({ mespeakVoice: _mespeakVoice, ...voice }) => voice);
};

export const supportsOfflineTts = (lang: string): boolean => {
  return offlineVoices.some(voice => langMatches(lang, voice.lang));
};

const pickVoice = (lang: string, voiceURI?: string): OfflineVoice | undefined => {
  return offlineVoices.find(voice => voice.voiceURI === voiceURI)
    || offlineVoices.find(voice => langMatches(lang, voice.lang));
};

export const speakOffline = (
  opts: SpeakOptions,
  onDone?: () => void,
): boolean => {
  const voice = pickVoice(opts.lang, opts.voiceURI);
  if (!voice || !ensureInitialized()) return false;

  const text = stripHtmlForSpeech(opts.text);
  if (!text) return true;

  offlineTts.stop();
  offlineTts.resetQueue();
  offlineTts.setDefaultVoice(voice.mespeakVoice);

  const started = offlineTts.speak(text, {
    voice: voice.mespeakVoice,
    pitch: voice.pitch,
    speed: Math.round(175 * (opts.rate ?? 1) * (voice.speedMultiplier ?? 1)),
    volume: 1,
    callback: () => onDone?.(),
  });

  if (!started) {
    opts.onError?.({ kind: 'unavailable', lang: opts.lang });
    return false;
  }

  return true;
};

export const cancelOffline = (): void => {
  if (!initialized) return;
  offlineTts.stop();
  offlineTts.resetQueue();
};
