package com.mlab.askvistax.utils;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class AudioUtil {
    private static final int SILENCE_THRESHOLD_SHORT = 500;
    private static final float SILENCE_THRESHOLD_FLOAT = 0.015f;
    private static final int SILENCE_THRESHOLD_BYTE = 10;

    private static final int TARGET_SR = 16000;

    /**
     * 将 JavaCV 的 Frame（音频帧）保存为临时 WAV 文件
     */
    public File convertFramesToFile(List<Frame> frames, int sampleRate, int channels) throws Exception {
        File tempFile = File.createTempFile("audio_chunk_", ".wav");

        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(tempFile, channels)) {
            recorder.setFormat("wav");
            recorder.setSampleRate(sampleRate);
            recorder.setAudioChannels(channels);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_PCM_S16LE);
            recorder.start();

            for (Frame f : frames) {
                recorder.recordSamples(f.samples);
            }

            recorder.stop();
        }

        return tempFile;
    }

    /**
     * 深拷贝一个仅包含音频数据的 Frame
     * @param src 原始 Frame
     * @return 仅包含音频数据的深拷贝 Frame
     */
    public Frame cloneAudioFrame(Frame src) {
        if (src == null || src.samples == null) {
            return null;
        }

        Buffer[] copiedSamples = new Buffer[src.samples.length];
        for (int i = 0; i < src.samples.length; i++) {
            Buffer buffer = src.samples[i];
            if (buffer instanceof ShortBuffer) {
                ShortBuffer orig = (ShortBuffer) buffer;
                ShortBuffer copy = ShortBuffer.allocate(orig.remaining());
                orig.mark();
                copy.put(orig);
                copy.flip();
                orig.reset();
                copiedSamples[i] = copy;
            } else if (buffer instanceof FloatBuffer) {
                FloatBuffer orig = (FloatBuffer) buffer;
                FloatBuffer copy = FloatBuffer.allocate(orig.remaining());
                orig.mark();
                copy.put(orig);
                copy.flip();
                orig.reset();
                copiedSamples[i] = copy;
            } else if (buffer instanceof ByteBuffer) {
                ByteBuffer orig = (ByteBuffer) buffer;
                ByteBuffer copy = ByteBuffer.allocate(orig.remaining());
                orig.mark();
                copy.put(orig);
                copy.flip();
                orig.reset();
                copiedSamples[i] = copy;
            } else {
                throw new IllegalArgumentException("未知的音频 Buffer 类型: " + buffer.getClass());
            }
        }

        Frame audioFrame = new Frame();
        audioFrame.sampleRate = src.sampleRate;
        audioFrame.audioChannels = src.audioChannels;
        audioFrame.samples = copiedSamples;

        // 复制时间戳（微秒）
        audioFrame.timestamp = src.timestamp;
        return audioFrame;
    }

    // 判断是否为静音帧
    public boolean isSilentFrame(Frame frame) {
        if (frame == null || frame.samples == null) {
            return true;  // 空帧视为静音
        }

        Buffer[] samples = frame.samples;
        for (Buffer buffer : samples) {
            if (buffer instanceof ShortBuffer) {
                ShortBuffer sb = (ShortBuffer) buffer;
                int pos = sb.position();
                int limit = sb.limit();
                for (int i = pos; i < limit; i++) {
                    short val = sb.get(i);
                    if (Math.abs(val) > SILENCE_THRESHOLD_SHORT) {
                        return false; // 有声音
                    }
                }
            } else if (buffer instanceof FloatBuffer) {
                FloatBuffer fb = (FloatBuffer) buffer;
                int pos = fb.position();
                int limit = fb.limit();
                for (int i = pos; i < limit; i++) {
                    float val = fb.get(i);
                    if (Math.abs(val) > SILENCE_THRESHOLD_FLOAT) {
                        return false;
                    }
                }
            } else if (buffer instanceof ByteBuffer) {
                ByteBuffer bb = (ByteBuffer) buffer;
                int pos = bb.position();
                int limit = bb.limit();
                for (int i = pos; i < limit; i++) {
                    byte val = bb.get(i);
                    if (Math.abs(val) > SILENCE_THRESHOLD_BYTE) {
                        return false;
                    }
                }
            } else {
                return false; // 其他类型默认有声，保守处理
            }
        }
        return true; // 全部样本都低于阈值，判定静音
    }

    // 判断此时的一段语音是否捕获到了有意义的能量
    public boolean hasMeaningfulAudioEnergy(List<Frame> frames) {
        if (frames == null || frames.isEmpty()) return false;

        double totalEnergy = 0;
        long totalSamples = 0;

        for (Frame frame : frames) {
            if (frame == null || frame.samples == null) continue;

            for (Buffer buffer : frame.samples) {
                if (buffer instanceof ShortBuffer) {
                    ShortBuffer sb = (ShortBuffer) buffer;
                    int pos = sb.position();
                    int limit = sb.limit();
                    for (int i = pos; i < limit; i++) {
                        short val = sb.get(i);
                        totalEnergy += val * val;
                    }
                    totalSamples += (limit - pos);
                }
                else if (buffer instanceof FloatBuffer) {
                    FloatBuffer fb = (FloatBuffer) buffer;
                    int pos = fb.position();
                    int limit = fb.limit();
                    for (int i = pos; i < limit; i++) {
                        float val = fb.get(i);
                        totalEnergy += val * val;
                    }
                    totalSamples += (limit - pos);
                }
                else if (buffer instanceof ByteBuffer) {
                    ByteBuffer bb = (ByteBuffer) buffer;
                    int pos = bb.position();
                    int limit = bb.limit();
                    for (int i = pos; i < limit; i++) {
                        byte val = bb.get(i);
                        totalEnergy += val * val;
                    }
                    totalSamples += (limit - pos);
                }
                else {
                    // 其他类型暂不支持，跳过
                }
            }
        }

        if (totalSamples == 0) return false;

        double rms = Math.sqrt(totalEnergy / totalSamples);

        // 下面阈值可根据实际调试调整
        // ShortBuffer最大振幅32768，经验阈值500左右对应RMS
        final double energyThreshold = 500;

        return rms >= energyThreshold;
    }

    // 克隆整个音频的frameBuffer列表，用于线程并发
    public List<Frame> cloneFrameBuffer(List<Frame> original) {
        List<Frame> copy = new ArrayList<>(original.size());
        for (Frame frame : original) {
            copy.add(cloneAudioFrame(frame));
        }
        return copy;
    }

    /**
     * 将 JavaCV Frame 中的音频数据转换为 PCM 字节数组
     *
     * @param frame JavaCV 捕获到的音频 Frame（channels > 0）
     * @return PCM 数据字节数组（16bit little-endian），如果 frame 无效返回 null
     */
    public byte[] frameToPCM(Frame frame) {
        if (frame == null || frame.samples == null || frame.samples.length == 0) return null;

        int inRate = frame.sampleRate > 0 ? frame.sampleRate : TARGET_SR;
        int channels = frame.audioChannels > 0 ? frame.audioChannels : 1;
        Object sampleBuf = frame.samples[0];

        // 1) 读取为单声道 float[] （范围大约 [-1,1]）
        float[] monoFloat;
        if (sampleBuf instanceof java.nio.ShortBuffer) {
            java.nio.ShortBuffer sb = (java.nio.ShortBuffer) sampleBuf;
            sb.rewind();
            int frames = sb.remaining() / channels;
            monoFloat = new float[frames];
            for (int i = 0; i < frames; i++) {
                int sum = 0;
                for (int c = 0; c < channels; c++) { sum += sb.get(); }
                monoFloat[i] = (sum / (float)channels) / (float)Short.MAX_VALUE;
            }
        } else if (sampleBuf instanceof java.nio.FloatBuffer) {
            java.nio.FloatBuffer fb = (java.nio.FloatBuffer) sampleBuf;
            fb.rewind();
            int frames = fb.remaining() / channels;
            monoFloat = new float[frames];
            for (int i = 0; i < frames; i++) {
                float s = 0f;
                for (int c = 0; c < channels; c++) { s += fb.get(); }
                monoFloat[i] = (s / channels);
            }
        } else {
            // 不支持的缓冲类型，返回 null 并记录
            log.warn("Unsupported sample buffer type: {}", sampleBuf.getClass().getName());
            return null;
        }

        // 2) 计算 RMS（可用于判断是否静音）
        double sumSq = 0;
        for (float v : monoFloat) { sumSq += v * v; }
        double rms = monoFloat.length > 0 ? Math.sqrt(sumSq / monoFloat.length) : 0;
        if (rms < 0.002) { // 经验阈值，太小可能是静音
            log.debug("低 RMS ({}), 可能为静音或麦克风静音", rms);
        }

        // 3) 如果采样率不等于目标，重采样（线性插值）
        float[] outFloat = monoFloat;
        if (inRate != TARGET_SR && monoFloat.length > 0) {
            outFloat = resampleLinear(monoFloat, inRate, TARGET_SR);
        }

        // 4) 转 16-bit little-endian bytes
        ByteBuffer bb = ByteBuffer.allocate(outFloat.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : outFloat) {
            float v = Math.max(-1.0f, Math.min(1.0f, f));
            short s = (short) (v * Short.MAX_VALUE);
            bb.putShort(s);
        }
        return bb.array();
    }

    private float[] resampleLinear(float[] input, int inRate, int outRate) {
        if (input == null || input.length == 0 || inRate == outRate) return input;
        double ratio = (double) outRate / inRate; // e.g. 16000/44100 = 0.3628...
        int outLen = (int) Math.round(input.length * ratio);
        if (outLen <= 0) return new float[0];
        float[] out = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            double srcIndex = i / ratio; // 映射到原数组索引
            int idx = (int) Math.floor(srcIndex);
            double frac = srcIndex - idx;
            float s0 = input[Math.min(idx, input.length - 1)];
            float s1 = input[Math.min(idx + 1, input.length - 1)];
            out[i] = (float) (s0 * (1 - frac) + s1 * frac);
        }
        return out;
    }


}

