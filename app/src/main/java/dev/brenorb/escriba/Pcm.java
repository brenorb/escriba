package dev.brenorb.escriba;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
public final class Pcm {
    public static byte[] wav(byte[] pcm,int rate,int channels) {
        ByteBuffer b=ByteBuffer.allocate(44+pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R','I','F','F'}).putInt(36+pcm.length).put(new byte[]{'W','A','V','E','f','m','t',' '});
        b.putInt(16).putShort((short)1).putShort((short)channels).putInt(rate).putInt(rate*channels*2);
        b.putShort((short)(channels*2)).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(pcm.length).put(pcm);
        return b.array();
    }
    /** Weighted box resampler: continuous phase, bounded memory, mono PCM16. */
    public static final class Converter {
        private final int rate,channels;
        private long area=0; private int filled=0;
        public Converter(int rate,int channels) {
            if(rate<=0 || channels<=0) throw new IllegalArgumentException("Formato PCM inválido");
            this.rate=rate; this.channels=channels;
        }
        public byte[] convert(byte[] input) {
            ByteBuffer b=ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            while(b.remaining()>=channels*2) {
                int sum=0; for(int c=0;c<channels;c++) sum+=b.getShort();
                int sample=sum/channels, left=16000;
                while(left>0) {
                    int take=Math.min(left,rate-filled); area+=(long)sample*take; filled+=take; left-=take;
                    if(filled==rate) { int v=(int)(area/rate); out.write(v&255); out.write((v>>8)&255); area=0; filled=0; }
                }
            }
            return out.toByteArray();
        }
    }
}
