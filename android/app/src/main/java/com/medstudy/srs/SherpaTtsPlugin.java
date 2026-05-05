package com.medstudy.srs;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "SherpaTts")
public class SherpaTtsPlugin extends Plugin {
    private SherpaTtsManager manager;

    @Override
    public void load() {
        manager = new SherpaTtsManager(getContext());
    }

    @PluginMethod
    public void getVoices(PluginCall call) {
        JSArray voices = new JSArray();
        addVoice(voices, "sherpa-en-vctk-0", "VCTK 0", "en-US", 0, null);
        addVoice(voices, "sherpa-en-vctk-1", "VCTK 1", "en-US", 1, null);
        addVoice(voices, "sherpa-en-vctk-2", "VCTK 2", "en-US", 2, null);
        addVoice(voices, "sherpa-en-vctk-3", "VCTK 3", "en-US", 3, null);
        addVoice(voices, "sherpa-en-vctk-4", "VCTK 4", "en-US", 4, null);
        addVoice(voices, "sherpa-en-vctk-5", "VCTK 5", "en-US", 5, null);
        addVoice(voices, "sherpa-de-kerstin-0", "Kerstin", "de-DE", 0, "female");
        addVoice(voices, "sherpa-de-thorsten-1", "Thorsten", "de-DE", 1, "male");

        JSObject ret = new JSObject();
        ret.put("voices", voices);
        call.resolve(ret);
    }

    @PluginMethod
    public void setupTTS(PluginCall call) {
        String modelPath = call.getString("modelPath", "");
        String configPath = call.getString("configPath", "");
        try {
            manager.setupTTS(modelPath, configPath);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to setup Sherpa TTS: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        String lang = call.getString("lang", "en-US");
        String voiceURI = call.getString("voiceURI", "");
        Integer speakerIdValue = call.getInt("speakerId");
        Float speedValue = call.getFloat("speed");

        int speakerId = speakerIdValue != null ? speakerIdValue : parseSpeakerId(voiceURI);
        float speed = speedValue != null ? speedValue : 1.0f;

        if (text.trim().isEmpty()) {
            call.resolve();
            return;
        }
        if (!manager.supportsLanguage(lang)) {
            call.reject("Unsupported language: " + lang);
            return;
        }

        manager.speak(text, lang, speakerId, speed, new SherpaTtsManager.SpeakCallback() {
            @Override
            public void onDone() {
                call.resolve();
            }

            @Override
            public void onError(Exception error) {
                call.reject("Sherpa TTS failed: " + error.getMessage(), error);
            }
        });
    }

    @PluginMethod
    public void stop(PluginCall call) {
        manager.stop();
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        if (manager != null) {
            manager.release();
        }
        super.handleOnDestroy();
    }

    private void addVoice(JSArray voices, String voiceURI, String name, String lang, int speakerId, String gender) {
        JSObject voice = new JSObject();
        voice.put("voiceURI", voiceURI);
        voice.put("name", name);
        voice.put("lang", lang);
        voice.put("localService", true);
        voice.put("speakerId", speakerId);
        if (gender != null) {
            voice.put("gender", gender);
        }
        voices.put(voice);
    }

    private int parseSpeakerId(String voiceURI) {
        if (voiceURI == null || voiceURI.isEmpty()) return 0;
        int dash = voiceURI.lastIndexOf('-');
        if (dash < 0 || dash >= voiceURI.length() - 1) return 0;
        try {
            return Integer.parseInt(voiceURI.substring(dash + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
