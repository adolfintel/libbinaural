/*
 * This library is free software.
 * It is distributed under the GPL License Agreement.
 * http://www.gnu.org/licenses/gpl.html
 */
package com.dosse.binaural;

import com.dosse.binaural.BinauralEnvelope.Envelope;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * use this class to play a BinauralEnvelope. sample format: 44100Hz, 16bit,
 * stereo, little endian. BinauralEnvelope's time unit is supposed to be
 * seconds. USE HEADPHONES!
 *
 * @author dosse
 */
public class BinauralEnvelopePlayer extends Thread {

    private Envelope binaural, noise, binauralV;
    private SourceDataLine speaker;
    private double baseF = 220;
    private OutputStream customDestination = null;
    private double volume = 1;

    /**
     * plays a BinauralEnvelope. remember to use start() to start playing the
     * sound. the player WILL NOT STOP at the end of the BinauralEnvelope, so
     * you will have to kill this thread (this was made to avoid
     * NullPointerExceptions when using this thread).
     *
     * @param be the BinauralEnvelope that you want to play
     * @throws LineUnavailableException if there's an error writing to sound
     * card
     */
    public BinauralEnvelopePlayer(BinauralEnvelope be) throws LineUnavailableException {
        this(be, 8192);
    }

    /**
     * plays a BinauralEnvelope. remember to use start() to start playing the
     * sound. the player WILL NOT STOP at the end of the BinauralEnvelope, so
     * you will have to kill this thread (this was made to avoid
     * NullPointerExceptions when using this thread).
     *
     * @param be the BinauralEnvelope that you want to play
     * @param bufferLength the sound card buffer length. recommended values are
     * from 512 to 16384. default is 8192. if this value is too low, an
     * Exception may be thrown by java
     * @throws LineUnavailableException if there's an error writing to sound
     * card
     */
    public BinauralEnvelopePlayer(BinauralEnvelope be, int bufferLength) throws LineUnavailableException {
        this.binaural = be.getBinauralF();
        this.binauralV = be.getBinauralV();
        this.noise = be.getNoiseV();
        this.baseF = be.getBaseF();
        AudioFormat af = new AudioFormat(44100f, 16, 2, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
        speaker = (SourceDataLine) AudioSystem.getLine(info);
        speaker.open(af, bufferLength);
        speaker.start();
    }

    /**
     * plays a BinauralEnvelope but instead of writing to the speaker, it writes
     * to the specified OutputStream as fast as possible. format is 44100hz, 16
     * bit, stereo, little endian. NOTE: AFTER STARTING THE THREAD, IT WILL KILL
     * ITSELF WHEN THE RENDER IS COMPLETE!
     *
     * @param be the BinauralEnvelope that you want to play
     * @param destination where you want the class to write the rendered wave
     */
    public BinauralEnvelopePlayer(BinauralEnvelope be, OutputStream destination) {
        this.binaural = be.getBinauralF();
        this.binauralV = be.getBinauralV();
        this.noise = be.getNoiseV();
        this.baseF = be.getBaseF();
        customDestination = destination;
    }
    private double t = 0; //time
    private double ctL = 0, ctR = 0; //used for internal calculations
    /**
     *
     * @return current position (in seconds)
     */
    public double getT() {
        return t;
    }

    /**
     *
     * @param t new position (in seconds)
     */
    public void setT(double t) {
        this.t = t;
    }

    /**
     *
     * @return length in seconds of the BinauralEnvelope (in seconds)
     */
    public double getLength() {
        return binaural.getLength();
    }

    /**
     *
     * @return start time of the BinauralEnvelope (in seconds)
     */
    public double getStartT() {
        return binaural.getStartT();
    }

    /**
     *
     * @return end time of the BinauralEnvelope (in seconds)
     */
    public double getEndT() {
        return binaural.getEndT();
    }

    /**
     *
     * @return position in BinauralEnvelope as double 0-1. may be below 0 or
     * above 1 if the position is before getStartT or after getEndT.
     */
    public double getPosition() {
        return (getT() - getStartT()) / (getEndT() - getStartT());
    }

    /**
     * sets the new position in the envelope as double 0-1
     *
     * @param p the new position as double 0-1 (you may use values below 0 or
     * above 1, but it's not recommended)
     */
    public void setPosition(double p) {
        t = getStartT() + (getEndT() - getStartT()) * p;
        if (p == 0) {
            ctL = 0;
            ctR = 0;
        } //reset ct if needed
    }

    /**
     *
     * @return volume as double 0-1
     */
    public double getVolume() {
        return volume;
    }

    /**
     *
     * @param volume volume as double 0-1. if lower than 0 or higher than 1, an
     * IllegalArgumentException is thrown
     */
    public void setVolume(double volume) {
        if (volume < 0 || volume > 1) {
            throw new IllegalArgumentException("Volume must be a double 0-1");
        } else {
            this.volume = volume;
        }
    }
    private boolean killASAP = false;

    /**
     * stops playback and kills this thread
     */
    public void stopPlaying() {
        killASAP = true;
    }
    /**
     * set to true to pause playback. set to false to unpause playback. default
     * value is false, of course
     */
    public boolean paused = false;
    //SINE LUT STUFF START
    private final static int LUT_SIZE = 8192;
    private final static double[] LUT = new double[LUT_SIZE];
    private final static double STEP_SIZE = (2 * Math.PI) / LUT_SIZE;

    static {
        //init LUT
        for (int i = 0; i < LUT_SIZE; i++) {
            LUT[i] = Math.sin(STEP_SIZE * i);
        }
    }
    private final static double PI2 = Math.PI * 2;

    /**
     * fast approximate sine (with LUT table). note: do not use with high
     * frequencies as it tends to produce garbage
     *
     * @param x angle in rad
     * @return sin(x)
     */
    public final static double fastSin(double x) {
        x %= PI2;
        x = x >= 0 ? x : (PI2 + x);
        int a = (int) (x / STEP_SIZE), b = a + 1;
        return LUT[a] + ((LUT[b >= LUT_SIZE ? 0 : b] - LUT[a]) * (x - (a * STEP_SIZE))) / STEP_SIZE; //linear interpolation between the 2 values in the LUT
    }
    //SINE LUT STUFF END
    //NOISE SAMPLE STUFF START
    private static double[] noiseSample;
    private static int noiseSampleLen;

    static {
        try {
            ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(BinauralEnvelopePlayer.class.getResourceAsStream("/com/dosse/binaural/noise.dat")));
            short[] noiseSample16Bit = (short[]) ois.readObject();
            noiseSampleLen = noiseSample16Bit.length;
            noiseSample = new double[noiseSampleLen];
            for (int i = 0; i < noiseSampleLen; i++) {
                noiseSample[i] = 1.45 * (((double) noiseSample16Bit[i]) / ((double) Short.MAX_VALUE)); //1.45 is preamp
            }
        } catch (Throwable t) {
            noiseSampleLen = 0;
            noiseSample = new double[noiseSampleLen];
        }
    }
    private static int ns = 0;

    /**
     *
     * @return next sample of noise as double
     */
    private static double nextNoiseSample() {
        return noiseSample[ns++ % noiseSampleLen];
    }
    //NOISE SAMPLE STUFF END

    @Override
    public void run() {
        final double PI2 = Math.PI * 2;
        t = getStartT();
        final double tStep = 1.0 / 44100.0;
        final int buffLen = customDestination == null ? 256 : 131072;
        final byte[] toSoundCard = new byte[buffLen * 4];
        final byte[] emptyBuffer = customDestination == null ? new byte[buffLen * 4] : null; //used for silence when paused, but only if playing to speakers (that's the reason of the customDestination==null)
        for (;;) {
            if (killASAP) {
                if (customDestination == null) {
                    speaker.flush();
                    speaker.close();
                } else {
                    try {
                        customDestination.flush();
                    } catch (IOException ex) {
                    }
                }
                return;
            }
            if (paused) {
                if (customDestination == null) {
                    speaker.write(emptyBuffer, 0, emptyBuffer.length);
                } else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                    }
                }
                continue;
            }
            final double volumeMul = volume * Short.MAX_VALUE;
            for (int i = 0; i < buffLen; i++) {
                final double pinkNoise = nextNoiseSample() * noise.getValueAt(t);
                final double binauralVolume = binauralV.getValueAt(t) * 0.55;
                final double ld = binauralVolume * fastSin(PI2 * ctL) + pinkNoise;
                final double rd = binauralVolume * fastSin(PI2 * ctR) + pinkNoise;
                final short l = (short) (volumeMul * (ld > 1 ? 1 : ld < -1 ? -1 : ld));
                final short r = (short) (volumeMul * (rd > 1 ? 1 : rd < -1 ? -1 : rd));
                toSoundCard[4 * i] = (byte) l;
                toSoundCard[4 * i + 1] = (byte) (l >> 8);
                toSoundCard[4 * i + 2] = (byte) r;
                toSoundCard[4 * i + 3] = (byte) (r >> 8);
                t += tStep;
                final double frequencyShift = binaural.getValueAt(t) * 0.5;
                ctL += (baseF - frequencyShift) / 44100.0;
                ctR += (baseF + frequencyShift) / 44100.0;
            }
            if (customDestination == null) {
                speaker.write(toSoundCard, 0, toSoundCard.length);
            } else {
                try {
                    customDestination.write(toSoundCard, 0, toSoundCard.length);
                    if (getPosition() >= 1) {
                        customDestination.flush();
                        return;
                    }
                } catch (Throwable ex) {
                }
            }
        }
    }
}
