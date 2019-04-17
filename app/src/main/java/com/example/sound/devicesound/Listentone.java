package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.genetics.TournamentSelection;
import org.apache.commons.math3.transform.*;

import java.nio.charset.IllegalCharsetNameException;
import java.util.ArrayList;

import google.zxing.common.reedsolomon.ReedSolomonDecoder;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;


    public Listentone(){

        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();
    }

    public void PreRequest(){

        boolean in_packet = false;
        ArrayList<Integer> packet = new ArrayList<Integer>();
        ArrayList<Integer> byte_stream;

        int dom;

        int bolcksize = findPowerSize((int)(long)Math.round(interval/2*mSampleRate));
        short[] buffer = new short[bolcksize];
        double[] bufferToInt = new double[bolcksize];



        while (true){
            int bufferedReadResult = mAudioRecord.read(buffer,0,bolcksize);

            if( bufferedReadResult < 0){
                continue;
            }

            for(int i = 0 ; i < buffer.length ; i++){
                bufferToInt[i] = (double)buffer[i];

            }
            dom = findFrequency(bufferToInt);
            Log.d("dom :", Integer.toString(dom));
            Log.d("dom : ", Boolean.toString(in_packet));


            if(in_packet && (dom >= HANDSHAKE_END_HZ -80 && dom <= HANDSHAKE_END_HZ+80)){
                byte_stream = extract_packet(packet);
                StringBuffer stringBuffer = new StringBuffer();
                for(int i = 0 ; i < byte_stream.size(); i++){
                    int byteToAscii = byte_stream.get(i);
                    stringBuffer.append((char)byteToAscii);
                }
                Log.d("message :" , stringBuffer.toString());

                in_packet = false;
                packet = new ArrayList<Integer>();
            }
            else if(in_packet){
                packet.add(dom);
            }
            else if(dom >= HANDSHAKE_START_HZ -80 && dom <= HANDSHAKE_START_HZ+80){
                in_packet = true;
            }
        }
    }

    public ArrayList<Integer> extract_packet(ArrayList<Integer> packet){
        ArrayList<Integer> Packet = new ArrayList<Integer>();
        Packet.add(packet.get(0));
        for(int i = 1 ; i <= Packet.size() ;i++){
            if(i % 2 == 0){
                Packet.add(packet.get(i));
            }
        }

        ArrayList<Integer> bit_chunks = new ArrayList<Integer>();
        for( int i = 0 ; i < Packet.size() ; i++) {
            int bit_chunks_1 = (int) (Math.round((Packet.get(i) - START_HZ) / STEP_HZ));
            if(bit_chunks_1 >= 0 && bit_chunks_1 < Math.pow(2,BITS)){
                bit_chunks.add(bit_chunks_1);
            }
        }

        return decode_bitchunks(BITS,bit_chunks);
    }

    private ArrayList<Integer> decode_bitchunks(int bits, ArrayList<Integer> bi){
        ArrayList<Integer> out_bytes = new ArrayList<Integer>();

        int next_read_chunk = 0;
        int next_read_bit = 0;

        int Byte = 0;
        int bits_left = 0;

        while (next_read_chunk < bi.size()){
            int can_fill = bits - next_read_bit;
            int to_fill = Math.min(bits_left,can_fill);
            int offset = bits - next_read_bit - to_fill;
            Byte <<= to_fill;
            int shifted = bi.get(next_read_chunk) & (((1 << to_fill) -1) << offset);
            Byte |= shifted >> offset;
            bits_left = bits_left - to_fill;
            next_read_bit += to_fill;
            if(bits_left <= 0) {
                out_bytes.add(Byte);
                Byte = 0;
                bits_left = 8;
            }

            if(next_read_bit >= bits){
                next_read_chunk += 1;
                next_read_bit -= bits;
            }
        }

        return out_bytes;
}

    public int findPowerSize(int size){
        int square = 1;
        while(square < size ){
            square = square*2;
        }
        if(Math.abs(square - size) < Math.abs(square/2 - size)){
            return square;
        }

        return square;

    }

    private int findFrequency(double[] toTransform){
        int len = toTransform.length;
        double[] real = new double[len];
        double[] img = new double[len];
        double realNum;
        double imgNum;
        double[] mag = new double[len];

        Complex[] complx = transform.transform(toTransform,TransformType.FORWARD);
        Double[] freq = this.fftfreq(complx.length, 1);

        for(int i = 0; i< complx.length; i++){
            realNum = complx[i].getReal();
            imgNum = complx[i].getImaginary();
            mag[i] = Math.sqrt((realNum*realNum)+ (imgNum*imgNum));
        }

        int max = 0;

        for(int i = 1 ; i < mag.length ; i++){
            if(mag[i] > mag[max]){
                max = i;
            }
        }

        double peak_freq = freq[max];

        return (int)Math.abs(peak_freq*mSampleRate);
    }

    private Double[] fftfreq(int n, int duration){
        Double[] freq = new Double[n];
        if( n%2 == 0){
            int j = 0;
            for(int i = 0 ; i < freq.length; i++){
                if( i <= (n/2)){
                    freq[i] = (double)i/(duration*n);
                }
                else{
                    freq[i] = (double)(-i+(2*j))/(duration*n);
                    j++;
                }
            }
            return freq;
        }
        int j = 0;
        for(int i = 0 ; i < freq.length; i++){
            if( i <= (n/2)){
                freq[i] = (double)i/(duration*n);
            }
            else{
                freq[i] = (double)(-i+(2*j+1))/(duration*n);
                j++;
            }
        }
        return freq;
    }
}
