package dev.brenorb.escriba;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.nio.ByteBuffer;

public final class AudioDecoder {
  public interface Sink {
    void write(byte[] pcm) throws Exception;
  }

  public interface Cancellation {
    boolean cancelled();
  }

  public static long decode(Context context, Uri uri, Sink sink, Cancellation cancel)
      throws Exception {
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec codec = null;
    try {
      extractor.setDataSource(context, uri, null);
      MediaFormat format = null;
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat f = extractor.getTrackFormat(i);
        if (f.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
          format = f;
          extractor.selectTrack(i);
          break;
        }
      }
      if (format == null)
        throw new IllegalArgumentException("O arquivo não contém uma faixa de áudio compatível.");
      codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
      codec.configure(format, null, null, 0);
      codec.start();
      boolean inputEnd = false, outputEnd = false;
      long total = 0, lastProgress = System.nanoTime();
      Pcm.Converter converter = null;
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      while (!outputEnd && !cancel.cancelled()) {
        if (System.nanoTime() - lastProgress > 30_000_000_000L)
          throw new IllegalArgumentException("Não foi possível decodificar este áudio.");
        if (!inputEnd) {
          int idx = codec.dequeueInputBuffer(10000);
          if (idx >= 0) {
            ByteBuffer b = codec.getInputBuffer(idx);
            int size = extractor.readSampleData(b, 0);
            if (size < 0) {
              codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputEnd = true;
            } else {
              codec.queueInputBuffer(idx, 0, size, extractor.getSampleTime(), 0);
              extractor.advance();
            }
            lastProgress = System.nanoTime();
          }
        }
        int idx = codec.dequeueOutputBuffer(info, 10000);
        if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat f = codec.getOutputFormat();
          if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)
              && f.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT)
            throw new IllegalArgumentException("Decodificador não oferece PCM16.");
          converter =
              new Pcm.Converter(
                  f.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                  f.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        } else if (idx >= 0) {
          try {
            if (info.size > 0) {
              if (converter == null) {
                MediaFormat f = codec.getOutputFormat();
                converter =
                    new Pcm.Converter(
                        f.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        f.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
              }
              ByteBuffer b = codec.getOutputBuffer(idx);
              b.position(info.offset);
              b.limit(info.offset + info.size);
              byte[] raw = new byte[info.size];
              b.get(raw);
              byte[] pcm = converter.convert(raw);
              if (total + pcm.length > 32000L * 60 * 60 * 2)
                throw new IllegalArgumentException("Limite de duas horas por áudio.");
              if (pcm.length > 0) {
                sink.write(pcm);
                total += pcm.length;
              }
            }
            outputEnd = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          } finally {
            codec.releaseOutputBuffer(idx, false);
          }
          lastProgress = System.nanoTime();
        }
      }
      if (cancel.cancelled()) throw new InterruptedException();
      if (total == 0) throw new IllegalArgumentException("O áudio está vazio ou corrompido.");
      return total;
    } finally {
      if (codec != null) {
        try {
          codec.stop();
        } catch (Exception ignored) {
        }
        codec.release();
      }
      extractor.release();
    }
  }
}
