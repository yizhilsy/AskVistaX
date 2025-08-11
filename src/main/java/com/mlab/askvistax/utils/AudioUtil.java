package com.mlab.askvistax.utils;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;

@Component
public class AudioUtil {
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
}

