package com.jimi.jimiordercorekitdemo;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import com.jimi.jmlog.JMLog;

public class MediaCodecUtil {

    /**
     * "video/x-vnd.on2.vp8" - VP8 video
     * "video/x-vnd.on2.vp9" - VP9 video
     * "video/avc" - H.264/AVC video
     * "video/hevc" - H.265/HEVC video
     * "video/mp4v-es" - MPEG4 video
     * "video/3gpp" - H.263 video
     * "audio/3gpp" - AMR narrowband audio
     * "audio/amr-wb" - AMR wideband audio
     * "audio/mpeg" - MPEG1/2 audio layer III
     * "audio/mp4a-latm" - AAC audio (note, this is raw AAC packets, not packaged in LATM!)
     * "audio/vorbis" - vorbis audio
     * "audio/g711-alaw" - G.711 alaw audio
     * "audio/g711-mlaw" - G.711 ulaw audio
     * @param  decoderType 编码类型
     * @return
     */
    static public boolean isSupportMediaCodecHardDecoder(String decoderType) {
        boolean isHardcode = false;
        if (Build.VERSION.SDK_INT >= 18) {
            MediaCodecList mediaCodecList = new MediaCodecList(1);
            MediaCodecInfo[] infos = mediaCodecList.getCodecInfos();
            for(MediaCodecInfo info : infos){
                String[] types = info.getSupportedTypes();
                for (int i = 0; i < types.length; i++) {
                    if(decoderType.equals(types[i])){
                        isHardcode = true;
                        break;
                    }
                }

                if(isHardcode)
                    break;
            }
        }

        if (isHardcode) {
            JMLog.i("Support MediaCodec to decode video data: " + decoderType);
        } else {
            JMLog.e("Not Support MediaCodec to decode video data: " + decoderType);
        }
        return isHardcode;
    }

    public static boolean isSupportAvcCodec() {
        if (Build.VERSION.SDK_INT >= 18) {
            for (int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);

                String[] types = codecInfo.getSupportedTypes();
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equalsIgnoreCase("video/avc")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    //获取NALU起始码长度(0x0 0x0 0x1 or 0x0 0x0 0x0 0x1)
    public static int getNALULen(byte[] data)
    {
        if (data.length >= 4 && data[0] == 0x0 && data[1] == 0x0 && data[2] == 0x0 && data[3] == 0x1) {
            return 4;
        } else if (data.length >= 3 && data[0] == 0x0 && data[1] == 0x0 && data[2] == 0x1) {
            return 3;
        }

        return 0;
    }

    //是否包含关键帧
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
}
