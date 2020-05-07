package com.jimi.jimiordercorekitdemo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import com.eafy.zjlog.ZJLog;

/**
 * h264编码器
 * 输入数据必须为NV12格式的数据
 * @author sandy
 *
 */
public class AVCEncoder {
	
	private static final String TAG = "AVCEncoder";

	private MediaCodec mediaCodec;
	private int mWidth, mHeight;
	private AVCEncoderListener mListener;
	private byte[] sps_pps = null;
	private int mSupportColorFormat = -1;
	private boolean bPause = false;

	public AVCEncoder(AVCEncoderListener listener) {
		// TODO Auto-generated constructor stub
		this.mListener = listener;
	}

	/**
	 *
	 * @param width
	 * @param height
	 * @param framerate
	 * @param bitrate
	 * @param iFrameInterval
	 * @return
	 */
	public boolean initEncoder(int width, int height, int framerate, int bitrate, int iFrameInterval) {
		if (mediaCodec != null) {
			closeEncoder();
		}

		this.mWidth = width;
		this.mHeight = height;

		mSupportColorFormat = getSupportColorFormat();
		if (mSupportColorFormat < 0) {
			ZJLog.e(TAG, "get support color format failed.");
			return false;
		}

		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
		if (mediaFormat == null) {
			ZJLog.e(TAG, "create video format failed.");
			return false;
		}
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mSupportColorFormat);
//		mediaFormat.setInteger(MediaFormat.KEY_ROTATION, 180);

		try {
			mediaCodec = MediaCodec.createEncoderByType("video/avc");
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (mediaCodec == null) {
            Log.e(TAG, "create media Codec failed.");
			return false;
		}

		try {
			mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		} catch (Throwable e) {
			// TODO: handle exception
            Log.d(TAG, "media codec configure got Throwable -> " + e.getLocalizedMessage());
			return false;
		}
		mediaCodec.start();

		return true;
	}

	public void pauseEncoder(boolean pause) {
		bPause = pause;
	}

	/**
	 * 获取硬编码器支持的图像格式
	 * @return
	 */
	private int getSupportColorFormat() {
		int numCodecs = MediaCodecList.getCodecCount();
		MediaCodecInfo codecInfo = null;
		for (int i = 0; i < numCodecs && codecInfo == null; i++) {
			MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
			if (!info.isEncoder()) {
				continue;
			}
			String[] types = info.getSupportedTypes();
			boolean found = false;
			for (int j = 0; j < types.length && !found; j++) {
				if (types[j].equals("video/avc")) {
					System.out.println("found");
					found = true;
				}
			}
			if (!found)
				continue;
			codecInfo = info;
		}

		// Find a color profile that the codec supports
		MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
        Log.e(TAG, "colorFormats --- " + Arrays.toString(capabilities.colorFormats));

		for (int i = 0; i < capabilities.colorFormats.length; i++) {
			switch (capabilities.colorFormats[i]) {
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // NV12
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:		// NV21
//			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
//			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
//			case MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar:
//			case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:

				return capabilities.colorFormats[i];
			default:
				break;
			}
		}

		return -1;
	}

	public void closeEncoder() {
		if (mediaCodec != null) {
			try {
				mediaCodec.stop();
				mediaCodec.release();
				mediaCodec = null;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static int getNALULen(byte[] data)
	{
		if (data.length >= 4 && data[0] == 0x0 && data[1] == 0x0 && data[2] == 0x0 && data[3] == 0x1) {
			return 4;
		} else if (data.length >= 3 && data[0] == 0x0 && data[1] == 0x0 && data[2] == 0x1) {
			return 3;
		}

		return 0;
	}

	public static boolean containKeyFrame(byte[] data) {
		if (data == null || data.length < 5)
			return false;

		int index = getNALULen(data);
		if (index == 0) {
			return false;
		}

		switch( data[index] & 0x1F ) {
			case 0x7:   //包含SPS的I帧
			case 0x5:
				return true;

			default:
				break;
		}

		return false;
	}

	/**
	 * 编码一帧YUV420P数据
	 * @param input		输入数据，必须为YUV420P(NV21)格式，根据编码器的不同判断是否需要转换格式
	 */
	public void encodeNV21Data(byte[] input) {
		if (mediaCodec == null || input == null || mSupportColorFormat <= 0) {
            Log.e(TAG, "h264 encoder has not been initialized.");
			return;
		}

		if (input.length != (mWidth * mHeight * 3 / 2)) {
            Log.e(TAG, "h264 encoder input data length error.");
			return;
		}

		if (mSupportColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
			byte[] nv12 = new byte[this.mWidth * this.mHeight * 3 / 2];
			NV21ToNV12(input, nv12, this.mWidth, this.mHeight);
			System.arraycopy(nv12, 0, input, 0, input.length);
		}

		ByteBuffer[] inputBuffers = null;
		ByteBuffer[] outputBuffers = null;
		try {
			inputBuffers = mediaCodec.getInputBuffers();
			outputBuffers = mediaCodec.getOutputBuffers();
		} catch (IllegalStateException e) {
			// TODO: handle exception
            Log.e(TAG, "AVCEncoder get input/output buffers exception -> " + e.getLocalizedMessage());
			return;
		}

		int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
		if (inputBufferIndex >= 0) {
			ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
			inputBuffer.clear();
			inputBuffer.put(input);
			mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, System.nanoTime() / 1000, 0);
		}

		MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
		int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
		while (outputBufferIndex >= 0) {
			ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
			byte[] outData = new byte[bufferInfo.size];
			outputBuffer.get(outData);

			if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) { // sps pps
				sps_pps = new byte[bufferInfo.size];
				System.arraycopy(outData, 0, sps_pps, 0, bufferInfo.size);
				ZJLog.printBytes("SPS PPS:",sps_pps);
			} else if (sps_pps != null && bufferInfo.size > 0) {
				if (AVCEncoder.containKeyFrame(outData)) { // key frame
					byte[] keyframe = new byte[bufferInfo.size + sps_pps.length];
					System.arraycopy(sps_pps, 0, keyframe, 0, sps_pps.length);
					System.arraycopy(outData, 0, keyframe, sps_pps.length, outData.length);
					if (mListener != null && !bPause) {
						mListener.pushVideoData(this, keyframe, bufferInfo.presentationTimeUs, true);
					}
				} else {
					if (mListener != null && !bPause) {
						mListener.pushVideoData(this, outData, bufferInfo.presentationTimeUs, false);
					}
				}
			}

			mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
			outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
		}
	}

	private static void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height){
		if(nv21 == null || nv12 == null)return;
		int framesize = width*height;
		int i = 0,j = 0;
		System.arraycopy(nv21, 0, nv12, 0, framesize);
		for(i = 0; i < framesize; i++){
			nv12[i] = nv21[i];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
		  nv12[framesize + j-1] = nv21[j+framesize];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
		  nv12[framesize + j] = nv21[j+framesize-1];
		}
	}

}