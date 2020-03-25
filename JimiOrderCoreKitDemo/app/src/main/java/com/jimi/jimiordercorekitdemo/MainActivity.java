package com.jimi.jimiordercorekitdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.eafy.zjlog.ZJLog;
import com.jimi.jimiordercorekit.JMOrderCoreKit;
import com.jimi.jimiordercorekit.JMOrderCoreKitJni;
import com.jimi.jimiordercorekit.JMOrderPusherListener;
import com.jimi.jimiordercorekit.JMOrderServerListener;
import com.jimi.jimiordercorekit.JMOrderTrackerListener;
import com.jimi.jimiordercorekit.Model.JMTrackAlertInfo;
import com.jimi.jimiordercorekit.Model.JMTrackAlertTypeEnum;
import com.jimi.jimiordercorekit.Model.JMTrackControlCmdInfo;
import com.jimi.jimiordercorekit.Model.JMTrackGPSReportModeEnum;
import com.jimi.jimiordercorekit.Model.JMTrackGSMSignalEnum;
import com.jimi.jimiordercorekit.Model.JMTrackHeartbeatInfo;
import com.jimi.jimiordercorekit.Model.JMTrackPositionInfo;
import com.jimi.jimiordercorekit.Model.JMTrackUploadFileResultInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, PreviewCallback {

    private boolean bHasPermission = false;
    private SurfaceView mSurfaceView1;
    private SurfaceHolder mSurfaceHolder1 = null;
    private Camera mCamera1 = null;
    private SurfaceView mSurfaceView2;
    private SurfaceHolder mSurfaceHolder2 = null;
    private Camera mCamera2 = null;
    private AVCEncoder avcEncoder1 = null;
    private AVCEncoder avcEncoder2 = null;

    private HWAACEncoder aacEncoder = null;
    private JMOrderCoreKit mJMOrderCoreKit = null;
    private boolean bIsPlayback1 = false;
    private boolean bIsPlayback2 = false;

    /*以下是音视频编码器的相关参数，具体数据需要根据开发者的项目决定*/
    private int mVideoWidth = 640;          //视频编码宽度
    private int mVideoHeight = 480;         //视频编码高度
    private int mVideoFrameRate = 15;       //视频编码帧率
    private int mVideoBitRate = 512000;     //视频编码比特率
    private int mAudioSampleRate = 44100;   //音频编码采样率
    private int mAudioChannels = 1;         //音频编码通道数
    private int mAudioBitRate = 128000;     //音频编码比特率

    private String mIMEI = "357730091014168"; //"983135884798102"，"357730091014168";
    private boolean bSupportMulCamera = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSurfaceView1 = (SurfaceView) this.findViewById(R.id.surfaceView1);
        mSurfaceHolder1 = mSurfaceView1.getHolder();
        mSurfaceHolder1.addCallback(this);
        mSurfaceView2 = (SurfaceView) this.findViewById(R.id.surfaceView2);
        mSurfaceHolder2 = mSurfaceView2.getHolder();
        mSurfaceHolder2.addCallback(this);

        bHasPermission = hasPermission();
        if (!bHasPermission) {
            requestPermission();
        }

        if (MediaCodecUtil.isSupportAvcCodec()) { // 判断设备是否支持h264硬编码
            avcEncoder1 = startAvcEncoder();
            aacEncoder = new HWAACEncoder(mHWAACEncoderListener);
            aacEncoder.startEncoder();
        } else {
            ZJLog.e("device does not support avc encoder.");
        }

        ZJLog.setTAG("JMTrack");
        ZJLog.setFileName("JMOrderCoreKit_" + mIMEI);
        ZJLog.setSavePathDic(Environment.getExternalStorageDirectory().getAbsolutePath() + "/JMOrderCoreKitLog" + File.separator);
        ZJLog.setSaveEnable(true);   //日志保存到内置存储卡

        JMOrderCoreKit.initialize(getApplication());
        JMOrderCoreKit.configMediaPara(0, mVideoWidth, mVideoHeight, mVideoFrameRate, mVideoBitRate,
                mAudioSampleRate, mAudioChannels, mAudioBitRate);
        JMOrderCoreKit.configMediaPara(1, mVideoWidth, mVideoHeight, mVideoFrameRate, mVideoBitRate,
                mAudioSampleRate, mAudioChannels, mAudioBitRate);
        if (!bSupportMulCamera) {
            JMOrderCoreKit.closeMulCamera();
        }
        JMOrderCoreKit.configGatewayServer("36.133.0.208", 21100);      //配置网关调试服务器（正式版不需要）
        JMOrderCoreKit.configLiveServer("", "36.133.0.208");        //配置RTM调试P服务器（正式版不需要）

        mJMOrderCoreKit = new JMOrderCoreKit(getApplicationContext(), mIMEI);
        mJMOrderCoreKit.setServerDelegate(mJMOrderServerListener);
        mJMOrderCoreKit.setTrackerDelegate(mJMOrderTrackerListener);
        mJMOrderCoreKit.setPusherDelegate(mJMOrderPusherListener);

        mJMOrderCoreKit.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopPosition();

        closeCamera(mCamera1);
        closeCamera(mCamera2);

        closeAvcEncoder(avcEncoder1);
        closeAvcEncoder(avcEncoder2);

        if (aacEncoder != null) {
            aacEncoder.stopEncoder();
            aacEncoder = null;
        }

        mJMOrderCoreKit.release();
        mJMOrderCoreKit = null;
        JMOrderCoreKit.deInitialize();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        if (bHasPermission && surfaceHolder == mSurfaceHolder1) {
            startBackCamera();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (camera == mCamera1 && avcEncoder1 != null) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            byte[] tmpBuf = new byte[size.width * size.height * 3 / 2];
            System.arraycopy(bytes, 0, tmpBuf, 0, tmpBuf.length);
            avcEncoder1.encodeNV21Data(tmpBuf);
        } else if (camera == mCamera2 && avcEncoder2 != null) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            byte[] tmpBuf = new byte[size.width * size.height * 3 / 2];
            System.arraycopy(bytes, 0, tmpBuf, 0, tmpBuf.length);
            avcEncoder2.encodeNV21Data(tmpBuf);
        }
    }

    private HWAACEncoderListener mHWAACEncoderListener = new HWAACEncoderListener() {

        @Override
        public void pushAudioData(byte[] data, long timestamp) {
            if (mJMOrderCoreKit != null && !bIsPlayback1) {
                mJMOrderCoreKit.pushAudioData(0, data, timestamp);
            }

            if (mJMOrderCoreKit != null && !bIsPlayback2) {
                mJMOrderCoreKit.pushAudioData(1, data, timestamp);
            }
        }
    };

    private AVCEncoderListener mAVCEncoderListener = new AVCEncoderListener() {

        @Override
        public void pushVideoData(AVCEncoder avcEncoder, byte[] data, long timestamp, boolean isKey) {
            if (mJMOrderCoreKit != null) {
                if (avcEncoder == avcEncoder1 && !bIsPlayback1) {
                    mJMOrderCoreKit.pushVideoData(0, data, timestamp, isKey);
                } else if (avcEncoder == avcEncoder2 && !bIsPlayback2) {
                    mJMOrderCoreKit.pushVideoData(1, data, timestamp, isKey);
                }
            }
        }
    };

    private JMOrderServerListener mJMOrderServerListener = new JMOrderServerListener() {

        @Override
        public void onError(int errorCode, String errMsg) {

        }

        @Override
        public void onServerConnectStatus(int state) {
            if (state == 2) {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        startPosition();
                    }
                });
            }
        }
    };

    private JMOrderPusherListener mJMOrderPusherListener = new JMOrderPusherListener() {

        @Override
        public boolean onPusherPlayStatus(int channel, String appId, String url, boolean bPlay) {
            ZJLog.d("onPusherPlayStatus channel:" + channel + " AppId:" + appId + " bPlay:" + bPlay);
            if (channel == 0) {
                bIsPlayback1 = false;
            } else if (channel == 1) {
                bIsPlayback2 = false;
            }

            if (bPlay) {
                if (channel == 0) {
                    //开始推送Camera 0的数据
                } else if (channel == 1) {
                    //开始推送Camera 1的数据
                }
            } else {
                if (channel == 0) {
                    //停止推送Camera 0的数据
                } else if (channel == 1) {
                    //停止推送Camera 1的数据
                }
            }

            if (bSupportMulCamera && channel == 1) {
                if (bPlay) {    //开始推送音视频数据
                    if (avcEncoder2 == null) {
                        avcEncoder2 = startAvcEncoder();
                        startFrontCamera();
                    }
                } else {    //停止推送音视频数据
                    closeCamera(mCamera2);
                    closeAvcEncoder(avcEncoder2);
                }
            }

            return true;
        }

        @Override
        public ArrayList<String> onPusherPlaybackList(int channel, String appId, String url, String fileNameJson) {
            ZJLog.d("onPusherPlaybackList channel:" + channel + " AppId:" + appId + " fileNameJson:" + fileNameJson);

            //将视频文件拼接成本地的决定路径，不需要检查文件是否存在
            try {
                JSONArray listJsonArray = new JSONArray(fileNameJson);
                if (listJsonArray != null) {
                    ArrayList<String> list = new ArrayList<String>();
                    for (int i = 0; i < listJsonArray.length(); i++) {
                        String path = listJsonArray.optString(i);
                        path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "TimeLine"
                                + File.separator + path;
                        list.add(path);
                    }
                    if (channel == 0) {
                        bIsPlayback1 = true;
                    } else if (channel == 1) {
                        bIsPlayback2 = true;
                    }
                    return list;
                }
            } catch (JSONException e) {
                e.printStackTrace();
                ZJLog.e("Failed to parse json:" + fileNameJson);
            }
            return null;
        }

        @Override
        public void onPusherPlaybackFileEnd(int channel, String appID, boolean isAllEnd, String fileName) {
            ZJLog.d("onPusherPlaybackFileEnd channel:" + channel + " AppId:" + appID + " isAllEnd:" + isAllEnd + " fileName:" + fileName);
            if (isAllEnd) {
                if (channel == 0) {
                    bIsPlayback1 = false;
                } else if (channel == 1) {
                    bIsPlayback2 = false;
                }
            }
        }

        @Override
        public boolean onPusherTalkStatus(String appID, String url, boolean bTalk) {
            ZJLog.d("onPusherTalkStatus AppID:" + appID + " bTalk:" + bTalk);

            return true;
        }

        @Override
        public boolean onPusherSwitchCamera(String appID, String url, boolean bFrontCamera) {
            ZJLog.d("onPusherSwitchCamera AppID:" + appID + " bFrontCamera:" + bFrontCamera);
            return true;
        }

        @Override
        public void onPusherRecvCmdData(String appID, int cmdCode, String data, int serverFlag, short serial) {

        }
    };

    private JMOrderTrackerListener mJMOrderTrackerListener = new JMOrderTrackerListener() {

        @Override
        public void onTrackerReplyHeartbeat(JMTrackHeartbeatInfo info) {

            //模拟数据
            info.oilElectricEnable = true;
            info.gpsEnable = false;
            info.chargEnable = true;
            info.accEnable = true;
            info.protectEnable = false;
            info.voltage = 100;
            info.signalStrength = JMTrackGSMSignalEnum.BETTER;
        }

        @Override
        public void onTrackerReplyControl(JMTrackControlCmdInfo info, String cmdContent) {
            ZJLog.d("onTrackerReplyControl cmdContent:" + cmdContent);      //正式版中类似Log的代码可以删掉

            //处理SDK内部未收录的指令，需要自行解析，之后在info.replyContent中填入相应需要回复的内容
        }

        private int mPictureTakeServerFlag = 0;
        @Override
        public void onTrackerReplyPictureTake(int cameraId, int serverFlag) {
            ZJLog.d("onTrackerReplyPictureTake cameraId:" + cameraId + " serverFlag:" + serverFlag);

            //拍照并上传
            //模拟图片上传之后的回复
            mPictureTakeServerFlag = serverFlag;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    JMTrackUploadFileResultInfo info = new JMTrackUploadFileResultInfo();
                    info.serverFlag = mPictureTakeServerFlag;
                    info.fileType = 1;
                    info.uploadResult = 1;
                    info.time = getUTCTime();
                    info.md5 = 0x123456;
                    mJMOrderCoreKit.sendUploadFileResult(info);
                }
            }).start();
        }

        @Override
        public void onTrackerReplyVideoRecord(int cameraId, int serverFlag) {
            ZJLog.d("onTrackerReplyVideoRecord cameraId:" + cameraId + " serverFlag:" + serverFlag);
            //拍照并上传，回复服务器，参考拍照回调
        }

        @Override
        public boolean onTrackerReplyWiFiEnable(boolean bOpen) {
            ZJLog.d("onTrackerReplyWiFiEnable bOpen:" + bOpen);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplyDVREnable(boolean bOpen) {
            ZJLog.d("onTrackerReplyDVREnable bOpen:" + bOpen);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplyVideoFileList() {
            ZJLog.d("onTrackerReplyVideoFileList");
            //处理文件相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplySupportReplay(boolean bOpen) {
            ZJLog.d("onTrackerReplySupportReplay bOpen:" + bOpen);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplyWiFiAPEnable(boolean bOpen) {
            ZJLog.d("onTrackerReplyWiFiAPEnable bOpen:" + bOpen);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplyVolumeSetting(int volume) {
            ZJLog.d("onTrackerReplyVolumeSetting volume:" + volume);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplyLedEnable(boolean bOpen) {
            ZJLog.d("onTrackerReplyLedEnable bOpen:" + bOpen);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplyDefenseEnable(boolean bOpen) {
            ZJLog.d("onTrackerReplyDefenseEnable bOpen:" + bOpen);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplyDefenseTime(int duration) {
            ZJLog.d("onTrackerReplyDefenseTime duration:" + duration);
            //处理相关设置或操作
            return true;
        }

        @Override
        public int onTrackerReplyDefenseTimeQuery() {
            ZJLog.d("onTrackerReplyDefenseTimeQuery");
            //获取本地设置的时间，然后返回
            int time = 3;   //模拟数据
            return time;
        }

        @Override
        public boolean onTrackerReplySpeedSetting(boolean bOpen, int duration, int speed) {
            ZJLog.d("onTrackerReplySpeedSetting bOpen:" + bOpen + " duration:" + duration + " speed:" + speed);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplyRapidAccSetting(boolean bOpen, boolean bUpload, int sensitivity) {
            ZJLog.d("onTrackerReplyRapidAccSetting bOpen:" + bOpen + " bUpload:" + bUpload + " sensitivity:" + sensitivity);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplyRapidDecSetting(boolean bOpen, boolean bUpload, int sensitivity) {
            ZJLog.d("onTrackerReplyRapidDecSetting bOpen:" + bOpen + " bUpload:" + bUpload + " sensitivity:" + sensitivity);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplyRapidTurnSetting(boolean bOpen, boolean bUpload, int sensitivity) {
            ZJLog.d("onTrackerReplyRapidTurnSetting bOpen:" + bOpen + " bUpload:" + bUpload + " sensitivity:" + sensitivity);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplySenalmSetting(boolean bOpen, boolean bUpload, int sensitivity, int videoTime) {
            ZJLog.d("onTrackerReplySenalmSetting bOpen:" + bOpen + " bUpload:" + bUpload + " sensitivity" + sensitivity + " videoTime:" + videoTime);
            //处理相关设置或操作
            return true;
        }

        @Override
        public boolean onTrackerReplySosSetting(boolean bAdd, String sosListStr) {
            ZJLog.d("onTrackerReplySosSetting bAdd:" + bAdd + " sosListStr:" + sosListStr);
            JSONArray sosJson = null;
            try {
                sosJson = new JSONArray(sosListStr);
                //获取3个号码进行其他操作
            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }

        @Override
        public String onTrackerReplySosQuery() {
            ZJLog.d("onTrackerReplySosQuery");

            //模拟SOS已设置的号码
            JSONArray sosJson = new JSONArray();
            sosJson.put("10086");
            sosJson.put("10000");
            sosJson.put("10010");

            return sosJson.toString();
        }

        @Override
        public boolean onTrackerReplyRelayEnable(boolean bOpen) {
            ZJLog.d("onTrackerReplyRelayEnable bOpen:" + bOpen);
            //处理Relay的设置
            return true;
        }

        @Override
        public boolean onTrackerReplyRelayQuery() {
            ZJLog.d("onTrackerReplyRelayQuery");
            boolean relayState = true;  //模拟Relay的状态
            return relayState;
        }
    };


    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this,
                        "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        if (requestCode == 1) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mCamera1 == null) {
                    startBackCamera();
                }
            } else {
                ZJLog.e("onRequestPermissionsResult: 申请权限已拒绝");
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private final int findBackCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras(); // get cameras number

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo); // get camerainfo
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return camIdx;
            }
        }
        return -1;
    }

    private final int findFrontCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras(); // get cameras number

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo); // get camerainfo
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return camIdx;
            }
        }
        return -1;
    }

    private void startBackCamera() {
        int backCamId = findBackCamera();
        mCamera1 = Camera.open(backCamId);
        if (mCamera1 != null) {
            try {
                mCamera1.setPreviewCallback(this);
                mCamera1.setDisplayOrientation(90);
                Camera.Parameters parameters = mCamera1.getParameters();
                parameters.setPreviewFormat(ImageFormat.NV21);
                parameters.setPreviewSize(mVideoWidth, mVideoHeight);
                mCamera1.setParameters(parameters);
                mCamera1.setPreviewDisplay(mSurfaceHolder1);
                mCamera1.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
                ZJLog.e(e.getMessage());
            }
        }
    }

    private void startFrontCamera() {
        int backCamId = findFrontCamera();
        mCamera2 = Camera.open(backCamId);
        if (mCamera2 != null) {
            try {
                mCamera2.setPreviewCallback(this);
                mCamera2.setDisplayOrientation(90);
                Camera.Parameters parameters = mCamera2.getParameters();
                parameters.setPreviewFormat(ImageFormat.NV21);
                parameters.setPreviewSize(mVideoWidth, mVideoHeight);
                mCamera2.setParameters(parameters);
                mCamera2.setPreviewDisplay(mSurfaceHolder2);
                mCamera2.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
                ZJLog.e(e.getMessage());
            }
        }
    }

    private void closeCamera(Camera camera) {
        if (null != camera) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }

        if (avcEncoder1 != null) {
            avcEncoder1.closeEncoder();
            avcEncoder1 = null;
        }
    }

    private AVCEncoder startAvcEncoder() {
        AVCEncoder avcEncoder = new AVCEncoder(mAVCEncoderListener);
        avcEncoder.initEncoder(mVideoWidth, mVideoHeight, mVideoFrameRate, mVideoBitRate, 1);

        return avcEncoder;
    }

    private void closeAvcEncoder(AVCEncoder avcEncoder) {
        if (avcEncoder != null) {
            avcEncoder.closeEncoder();
            avcEncoder = null;
        }
    }

    private int positionCount = 0;
    private JMTrackPositionInfo positionInfo = new JMTrackPositionInfo();
    private void startPosition() {
        GPSUtils.getInstance(getApplicationContext()).getLngAndLat(new GPSUtils.OnLocationResultListener(){

            @Override
            public void onLocationResult(Location location) {
                sendPositionInfo(location);
            }

            @Override
            public void OnLocationChange(Location location) {
                if (positionCount == 1) {       //模拟告警
                    JMTrackAlertInfo info = new JMTrackAlertInfo();
                    info.time = getUTCTime();
                    info.latitude = location.getLatitude();
                    info.longitude = location.getLongitude();
                    info.gpsEnable = true;
                    info.direction = 0;
                    info.alertType = JMTrackAlertTypeEnum.REARVIEW_CAMERA_SHAKE;
                    info.alertContent = "Hello DVR";

                    mJMOrderCoreKit.sendAlert(info);
                } else {
                    sendPositionInfo(location);
                }
                positionCount ++;
            }
        });
    }

    private void stopPosition() {
        GPSUtils.getInstance(getApplicationContext()).removeListener();
    }


    private void sendPositionInfo(Location location) {
        if (location != null) {
            ZJLog.d("--->Latitude:" + location.getLatitude() + " Longitude:" + location.getLongitude());
            positionInfo.time = getUTCTime();
            positionInfo.latitude = location.getLatitude();
            positionInfo.longitude = location.getLongitude();
            positionInfo.reportMode = JMTrackGPSReportModeEnum.TIMING;
            positionInfo.gpsEnable = true;

            /*下面是模拟数据，实际设备按正确数值填写*/
            positionInfo.accEnable = true;
            positionInfo.speed = 80;
            positionInfo.positionType = 1;
            positionInfo.gpsSize = 10;
            positionInfo.direction = 90;
            positionInfo.mcc = 0x03E7;
            positionInfo.mnc = 0x00;
            positionInfo.lac = 0x00;
            positionInfo.cellId = 0x00;

            mJMOrderCoreKit.sendPosition(positionInfo);
        }
    }

    private long getUTCTime() {
        Calendar cal = Calendar.getInstance();
        TimeZone tz = TimeZone.getTimeZone("GMT");
        cal.setTimeZone(tz);
        long time = cal.getTimeInMillis() / 1000;

        return time;
    }
}
