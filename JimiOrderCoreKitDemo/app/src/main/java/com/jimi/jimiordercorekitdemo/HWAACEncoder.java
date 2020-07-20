package com.jimi.jimiordercorekitdemo;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import com.jimi.jmlog.JMLog;

/**
 * AAC编码器
 * 负责从Android设备中读取音频数据并编码为AAC格式
 * @author sandy
 *
 */
public class HWAACEncoder implements Runnable {
	private static final String TAG = "HWAACEncoder";
	
	// 音频编码相关参数
	private static final String MIME_TYPE = "audio/mp4a-latm";
	private static final int SAMPLE_RATE = 44100;
	private static final int BIT_RATE = 128000;
	private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
	private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
	private static final int SAMPLES_PER_FRAME = 1024;

	private AudioRecord audioRecoder;
	private MediaCodec mAudioEncoder;
	private boolean isRun = false;
	private Thread thread;
	private HWAACEncoderListener mListener = null;
	private boolean bPause = false;

	public HWAACEncoder(HWAACEncoderListener listener) {
		super();
		this.mListener = listener;
	}

	public void startEncoder() {
		isRun = true;
		thread = new Thread(this);
		thread.start();
	}

	@SuppressLint("NewApi")
	public void stopEncoder() {
		isRun = false;

		if (thread != null) {
			try {
				thread.join();
				thread = null;
			} catch (InterruptedException e) {
				JMLog.e(TAG, "Stop aac encoder InterruptedException->" + e.getLocalizedMessage());
			}
		}
	}

	public void pauseEncoder(boolean pause) {
		bPause = pause;
	}

	@SuppressLint("NewApi")
	private void initAudioCodec() {
		int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
		int buffer_size = SAMPLES_PER_FRAME * 10;
		if (buffer_size < minBufferSize) {
			buffer_size = ((minBufferSize / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
		}

		MediaFormat audioFormat = new MediaFormat();
		audioFormat.setString(MediaFormat.KEY_MIME, MIME_TYPE);
		audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
		audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
		audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
		audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

		try {
			mAudioEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
		} catch (IOException e) {
			e.printStackTrace();
		}
		mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mAudioEncoder.start();

		audioRecoder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
				buffer_size);
		audioRecoder.startRecording();
	}

	private void setAudioData(ByteBuffer outputBuffer, BufferInfo bufferInfo) {
		byte[] aacBuf = new byte[bufferInfo.size];
		outputBuffer.get(aacBuf);

		if (!bPause) {
			mListener.pushAudioData(aacBuf, bufferInfo.presentationTimeUs);
		}
	}

	// trying to add a ADTS
	private void addADTStoPacket(byte[] packet, int packetLen) {
		int profile = 2; // AAC Main:1,LC:2
		int freqIdx = 4; // 4.41KHz
		int chanCfg = 1; // CPE

		packet[0] = (byte) 0xFF;
		packet[1] = (byte) 0xF9;
		packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
		packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
		packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
		packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
		packet[6] = (byte) 0xFC;
	}

	@Override
	public void run() {
		initAudioCodec();
		BufferInfo bufferInfo = new BufferInfo();

		while (isRun) {
			if (audioRecoder != null) {
				ByteBuffer[] inputBuffers = mAudioEncoder.getInputBuffers();
				int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
				if (inputBufferIndex >= 0) {
					ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
					inputBuffer.clear();
					int inputLength = audioRecoder.read(inputBuffer, SAMPLES_PER_FRAME * 2);
					if (inputLength == AudioRecord.ERROR_BAD_VALUE
							|| inputLength == AudioRecord.ERROR_INVALID_OPERATION) {
						Log.e(TAG, "Audio read error!!!");
					}

					// enqueue
					mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, System.nanoTime() / 1000, 0);

					// dequeue
					ByteBuffer[] outputBuffers = mAudioEncoder.getOutputBuffers();

					while (true) {
						final int TIMEOUT_USEC = 100;
						int encoderStatus = mAudioEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
						if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
							break;
						} else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
							// not expected
							outputBuffers = mAudioEncoder.getOutputBuffers();
						} else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
							// should happen before receiving buffers, and should only happen once
							MediaFormat audioOutputFormat = mAudioEncoder.getOutputFormat();
						} else if (encoderStatus < 0) {
							JMLog.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
						} else {
							ByteBuffer encodedData = outputBuffers[encoderStatus];
							if (encodedData == null) {
								throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
							}

							if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
								bufferInfo.size = 0;
							}

							if (bufferInfo.size != 0) {
								if (isRun && mListener != null) {
									encodedData.position(bufferInfo.offset);
									encodedData.limit(bufferInfo.offset + bufferInfo.size);
									setAudioData(encodedData, bufferInfo);
								}
							}

							mAudioEncoder.releaseOutputBuffer(encoderStatus, false);
						}
					}
				}
			}
		}

		if (audioRecoder != null) {
			audioRecoder.stop();
			audioRecoder.release();
			audioRecoder = null;
		}

		if (mAudioEncoder != null) {
			mAudioEncoder.stop();
			mAudioEncoder.release();
			mAudioEncoder = null;
		}
	}
}
