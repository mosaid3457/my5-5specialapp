package com.medstudy.srs;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SherpaTtsManager {
    private static final Map<String, ModelSpec> MODELS = new HashMap<>();

    static {
        MODELS.put("en", new ModelSpec("en-US", "tts/en_US_vctk", "model.onnx", "tokens.txt", "lexicon.txt", "", 109));
        MODELS.put("de0", new ModelSpec("de-DE-kerstin", "tts/de_DE_kerstin", "model.onnx", "tokens.txt", "", "tts/espeak-ng-data", 1));
        MODELS.put("de1", new ModelSpec("de-DE-thorsten", "tts/de_DE_thorsten", "model.onnx", "tokens.txt", "", "tts/espeak-ng-data", 1));
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, OfflineTts> engines = new HashMap<>();
    private AudioTrack audioTrack;

    public SherpaTtsManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public interface SpeakCallback {
        void onDone();
        void onError(Exception error);
    }

    public boolean supportsLanguage(String lang) {
        return getModelSpec(lang) != null;
    }

    public int getSpeakerCount(String lang) {
        ModelSpec spec = getModelSpec(lang);
        return spec == null ? 0 : spec.speakerCount;
    }

    public void setupTTS(String modelPath, String configPath) {
        ModelSpec spec = new ModelSpec("custom", modelPath, "model.onnx", "tokens.txt", "lexicon.txt", configPath, 1);
        synchronized (engines) {
            releaseEngine("custom");
            engines.put("custom", createEngine(spec));
        }
    }

    public void speak(String text, String lang, int speakerId, float speed, SpeakCallback callback) {
        ModelSpec spec = getModelSpec(lang, speakerId);
        if (spec == null) {
            callback.onError(new IllegalArgumentException("Unsupported language: " + lang));
            return;
        }
        int safeSpeakerId = spec.lang.startsWith("de") ? 0 : Math.max(0, Math.min(speakerId, spec.speakerCount - 1));
        float safeSpeed = Math.max(0.5f, Math.min(speed, 1.5f));
        executor.execute(() -> {
            try {
                OfflineTts engine = getOrCreateEngine(spec);
                GeneratedAudio audio = engine.generate(text, safeSpeakerId, safeSpeed);
                play(audio.getSamples(), audio.getSampleRate());
                callback.onDone();
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public synchronized void stop() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
            } catch (Exception ignored) {
            }
            audioTrack.release();
            audioTrack = null;
        }
    }

    public void release() {
        stop();
        executor.shutdownNow();
        synchronized (engines) {
            for (String key : engines.keySet()) {
                OfflineTts engine = engines.get(key);
                if (engine != null) {
                    engine.release();
                }
            }
            engines.clear();
        }
    }

    private OfflineTts getOrCreateEngine(ModelSpec spec) {
        synchronized (engines) {
            OfflineTts engine = engines.get(spec.lang);
            if (engine == null) {
                engine = createEngine(spec);
                engines.put(spec.lang, engine);
            }
            return engine;
        }
    }

    private void releaseEngine(String key) {
        OfflineTts engine = engines.remove(key);
        if (engine != null) {
            engine.release();
        }
    }

    private OfflineTts createEngine(ModelSpec spec) {
        String model = copyAssetToFile(spec.assetDir + "/" + spec.modelFile);
        String tokens = copyAssetToFile(spec.assetDir + "/" + spec.tokensFile);
        String lexicon = spec.lexiconFile.isEmpty() ? "" : copyAssetToFile(spec.assetDir + "/" + spec.lexiconFile);
        String dataDir = spec.dataDir.isEmpty() ? "" : ensureAssetDirectory(spec.dataDir);

        OfflineTtsVitsModelConfig vits = OfflineTtsVitsModelConfig.builder()
                .setModel(model)
                .setTokens(tokens)
                .setLexicon(lexicon)
                .setDataDir(dataDir)
                .build();
        OfflineTtsModelConfig modelConfig = OfflineTtsModelConfig.builder()
                .setVits(vits)
                .setNumThreads(2)
                .setDebug(false)
                .setProvider("cpu")
                .build();
        OfflineTtsConfig config = OfflineTtsConfig.builder()
                .setModel(modelConfig)
                .build();
        return new OfflineTts(config);
    }

    private synchronized void play(float[] samples, int sampleRate) {
        stop();
        short[] pcm16 = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float sample = Math.max(-1.0f, Math.min(1.0f, samples[i]));
            pcm16[i] = (short) (sample * Short.MAX_VALUE);
        }

        int minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(Math.max(minBufferSize, pcm16.length * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();

        audioTrack.write(pcm16, 0, pcm16.length);
        audioTrack.play();
    }

    private ModelSpec getModelSpec(String lang) {
        if (lang == null) return null;
        String lower = lang.replace('_', '-').toLowerCase();
        if (lower.startsWith("en")) return MODELS.get("en");
        if (lower.startsWith("de")) return MODELS.get("de0");
        return null;
    }

    private ModelSpec getModelSpec(String lang, int speakerId) {
        if (lang == null) return null;
        String lower = lang.replace('_', '-').toLowerCase();
        if (lower.startsWith("de")) return MODELS.get(speakerId == 1 ? "de1" : "de0");
        return getModelSpec(lang);
    }

    private String ensureAssetDirectory(String assetDir) {
        File outDir = new File(context.getFilesDir(), "sherpa/" + assetDir);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        copyAssetDirectory(assetDir, outDir);
        return outDir.getAbsolutePath();
    }

    private void copyAssetDirectory(String assetDir, File outDir) {
        try {
            String[] children = context.getAssets().list(assetDir);
            if (children == null) return;
            for (String child : children) {
                String childAssetPath = assetDir + "/" + child;
                File childOut = new File(outDir, child);
                String[] grandChildren = context.getAssets().list(childAssetPath);
                if (grandChildren != null && grandChildren.length > 0) {
                    if (!childOut.exists()) {
                        childOut.mkdirs();
                    }
                    copyAssetDirectory(childAssetPath, childOut);
                } else {
                    copyAssetToFile(childAssetPath);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy asset directory " + assetDir, e);
        }
    }

    private String copyAssetToFile(String assetPath) {
        File outFile = new File(context.getFilesDir(), "sherpa/" + assetPath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (outFile.exists() && outFile.length() > 0) {
            return outFile.getAbsolutePath();
        }
        try (InputStream in = context.getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(outFile)) {
            copyStream(in, out);
            return outFile.getAbsolutePath();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to copy asset " + assetPath, e);
        }
    }

    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static class ModelSpec {
        final String lang;
        final String assetDir;
        final String modelFile;
        final String tokensFile;
        final String lexiconFile;
        final String dataDir;
        final int speakerCount;

        ModelSpec(String lang, String assetDir, String modelFile, String tokensFile, String lexiconFile, String dataDir, int speakerCount) {
            this.lang = lang;
            this.assetDir = assetDir;
            this.modelFile = modelFile;
            this.tokensFile = tokensFile;
            this.lexiconFile = lexiconFile;
            this.dataDir = dataDir;
            this.speakerCount = speakerCount;
        }
    }
}
