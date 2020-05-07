package com.jimi.jimiordercorekitdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
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

import com.jimi.jimiordercorekit.JMOrderCoreKit;
import com.jimi.jimiordercorekit.JMOrderPusherCmdListener;
import com.jimi.jimiordercorekit.JMOrderPusherListener;
import com.jimi.jimiordercorekit.JMOrderServerListener;
import com.jimi.jimiordercorekit.JMOrderTrackerListener;
import com.jimi.jimiordercorekit.Model.JMTrackAlertInfo;
import com.jimi.jimiordercorekit.Model.JMTrackAlertTypeEnum;
import com.jimi.jimiordercorekit.Model.JMTrackReplyControlCmdInfo;
import com.jimi.jimiordercorekit.Model.JMTrackGPSReportModeEnum;
import com.jimi.jimiordercorekit.Model.JMTrackGSMSignalEnum;
import com.jimi.jimiordercorekit.Model.JMTrackHeartbeatInfo;
import com.jimi.jimiordercorekit.Model.JMTrackPositionInfo;
import com.jimi.jimiordercorekit.Model.JMTrackUploadFileResultInfo;
import com.jimi.jmlog.JMLog;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity implements PreviewCallback {

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

    private SharedPreferences mSettingPreferences;
    private SharedPreferences.Editor mDataEditor;    //用来保存模拟设置的数据

    /*以下是音视频编码器的相关参数，具体数据需要根据开发者的项目决定*/
    private int mVideoWidth = 640;          //视频编码宽度
    private int mVideoHeight = 480;         //视频编码高度
    private int mVideoFrameRate = 15;       //视频编码帧率
    private int mVideoBitRate = 512000;     //视频编码比特率
    private int mAudioSampleRate = 44100;   //音频编码采样率
    private int mAudioChannels = 1;         //音频编码通道数
    private int mAudioBitRate = 128000;     //音频编码比特率

    private String mIMEI = "357730091014168"; //"353376110005078"，"357730091014168";
    private boolean bSupportMulCamera = true;  //是否支持多路摄像头同时播放
    private boolean bFrontCamera = false;   //是否是切换前摄像头（单路有效）
    private boolean bStopPlay = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSurfaceView1 = (SurfaceView) this.findViewById(R.id.surfaceView1);
        mSurfaceHolder1 = mSurfaceView1.getHolder();
        mSurfaceView2 = (SurfaceView) this.findViewById(R.id.surfaceView2);
        mSurfaceHolder2 = mSurfaceView2.getHolder();

        bHasPermission = hasPermission();
        if (!bHasPermission) {
            requestPermission();
        }
        initPrefernces();

        JMLog.setTAG("JMTrack");
        JMLog.setFileName("JMOrderCoreKit_" + mIMEI);
        JMLog.setSavePathDic(Environment.getExternalStorageDirectory().getAbsolutePath() + "/JMOrderCoreKitLog" + File.separator);
        JMLog.setSaveEnable(true);   //日志保存到内置存储卡

        JMOrderCoreKit.initialize(getApplication());
        JMOrderCoreKit.configMediaPara(0, mVideoWidth, mVideoHeight, mVideoFrameRate, mVideoBitRate,
                mAudioSampleRate, mAudioChannels, mAudioBitRate);
        JMOrderCoreKit.configMediaPara(1, mVideoWidth, mVideoHeight, mVideoFrameRate, mVideoBitRate,
                mAudioSampleRate, mAudioChannels, mAudioBitRate);
        if (!bSupportMulCamera) {
            JMOrderCoreKit.closeMulCamera();
        }
        JMOrderCoreKit.configGatewayServer("36.133.0.208", 31100);      //配置网关调试服务器（正式版不需要）
        JMOrderCoreKit.configLiveServer("", "36.133.0.208");        //配置RTMP调试服务器（正式版不需要）

        mJMOrderCoreKit = new JMOrderCoreKit(getApplicationContext(), mIMEI);
        mJMOrderCoreKit.setServerDelegate(mJMOrderServerListener);
        mJMOrderCoreKit.setTrackerDelegate(mJMOrderTrackerListener);
        mJMOrderCoreKit.setPusherDelegate(mJMOrderPusherListener);

//        mJMOrderCoreKit.setPusherCmdDelegate(mJMOrderPusherCmdListener);    //监听实时视频指令和回复单独使用此监听，和setPusherDelegate对立

        mJMOrderCoreKit.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopPosition();

        closeCamera(mCamera1);
        closeCamera(mCamera2);

        if (aacEncoder != null) {
            aacEncoder.stopEncoder();
            aacEncoder = null;
        }

        mJMOrderCoreKit.release();
        mJMOrderCoreKit = null;
        JMOrderCoreKit.deInitialize();
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (bStopPlay) return;

        if (camera == mCamera1 && (bSupportMulCamera || (!bSupportMulCamera && !bFrontCamera))) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            byte[] tmpBuf = new byte[size.width * size.height * 3 / 2];
            System.arraycopy(bytes, 0, tmpBuf, 0, tmpBuf.length);
            avcEncoder1.encodeNV21Data(tmpBuf);
        } else if (camera == mCamera2 && (bSupportMulCamera || (!bSupportMulCamera && bFrontCamera))) {
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
                if (!bSupportMulCamera && !bIsPlayback1) {   //单路
                    mJMOrderCoreKit.pushVideoData(0, data, timestamp, isKey);
                } else if (avcEncoder == avcEncoder1 && !bIsPlayback1) {
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

                startBackCamera();
                startFrontCamera();
                startAACEncoder();
            }
        }
    };

    private JMOrderPusherCmdListener mJMOrderPusherCmdListener = new JMOrderPusherCmdListener() {


        @Override
        public void onPusherLiveRecvCmdData(int serverFlagId, String recvCmdDataStr) {
            mJMOrderCoreKit.sendLiveCmdToSDK(serverFlagId, recvCmdDataStr);

            //广播或其他方式将内容发给实时视频模块

            //模拟：实时视频模块收到内容，并发给SDK（实现中下面代码不需要）
            mJMOrderCoreKit.sendLiveCmdToSDK(serverFlagId, recvCmdDataStr);
        }

        @Override
        public void onPusherLiveRelayCmdData(int serverFlagId, String relayCmdDataStr) {

            //广播或其他方式将内容发给Tracker模块，再发送给服务器

            //模拟：Tracker模块收到内容，并发给服务器（实现中下面代码不需要）
            mJMOrderCoreKit.relayMsgToServer(serverFlagId, relayCmdDataStr);
        }
    };

    private JMOrderPusherListener mJMOrderPusherListener = new JMOrderPusherListener() {

        @Override
        public void onPusherPlayStatus(int channel, String appId, String url, boolean bPlay) {
            JMLog.d("onPusherPlayStatus channel:" + channel + " AppId:" + appId + " bPlay:" + bPlay);
            if (channel == 0) {
                bIsPlayback1 = false;
            } else if (channel == 1) {
                bIsPlayback2 = false;
            }

            if (bPlay) {
                bStopPlay = false;
                if (channel == 0) {
                    bFrontCamera = false;
                    //开始推送Camera 0的数据
                }
            } else {
                bStopPlay = true;
            }
        }

        @Override
        public ArrayList<String> onPusherPlaybackList(int channel, String appId, String url, String fileNameJson) {
            JMLog.d("onPusherPlaybackList channel:" + channel + " AppId:" + appId + " fileNameJson:" + fileNameJson);

            //将视频文件拼接成本地的决定路径，不需要检查文件是否存在
            try {
                bStopPlay = false;
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
                bStopPlay = true;
                JMLog.e("Failed to parse json:" + fileNameJson);
            }
            return null;
        }

        @Override
        public void onPusherPlaybackFileEnd(int channel, String appID, boolean isAllEnd, String fileName) {
            JMLog.d("onPusherPlaybackFileEnd channel:" + channel + " AppId:" + appID + " isAllEnd:" + isAllEnd + " fileName:" + fileName);
            if (isAllEnd) {
                if (channel == 0) {
                    bIsPlayback1 = false;
                } else if (channel == 1) {
                    bIsPlayback2 = false;
                }
            }
        }

        @Override
        public void onPusherTalkStatus(String appID, String url, boolean bTalk) {
            JMLog.d("onPusherTalkStatus AppID:" + appID + " bTalk:" + bTalk);
        }

        @Override
        public void onPusherSwitchCamera(String appID, String url, boolean bFrontCameraT) {
            JMLog.d("onPusherSwitchCamera AppID:" + appID + " bFrontCamera:" + bFrontCameraT);
            if (!bSupportMulCamera) {
                bStopPlay = false;
                bFrontCamera = bFrontCameraT;
                startFrontCamera();
            }
        }

        @Override
        public String onPusherRecvCmdData(String appID, int cmdCode, String data, int serverFlag, short serial) {
            JMLog.d("onPusherRecvCmdData AppID:" + appID + " cmdCode:" + cmdCode + " data:" + data);

            return "";
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
        public void onTrackerReplyControl(JMTrackReplyControlCmdInfo info, String cmdContent) {
            JMLog.d("onTrackerReplyControl cmdContent:" + cmdContent);      //正式版中类似Log的代码可以删掉

            //处理SDK内部未收录的指令，需要自行解析，之后在info.replyContent中填入相应需要回复的内容
        }

        @Override
        public void onTrackerReplyPictureTake(JMTrackUploadFileResultInfo info, int cameraId) {
            JMLog.d("onTrackerReplyPictureTake cameraId:" + cameraId + " serverFlag:" + info.getServerFlagId());

            //发送应答
            info.send();

            //对摄像头拍照上传至七牛云或几米云或其他第三个云存储平台


            // 情况1：
            // 可以上传，即空闲状态，直接调用 info.send();
            // 情况2：
            // 正在上传或处理其他Camera操作，无法拍照或上传，调用info.sendBusy();
        }

        @Override
        public void onTrackerReplyVideoRecord(JMTrackUploadFileResultInfo info, int cameraId) {
            JMLog.d("onTrackerReplyVideoRecord cameraId:" + cameraId + " serverFlag:" + info.getServerFlagId());

            //发送应答
            info.send();

            //对摄像头录像上传至七牛云或几米云或其他第三个云存储平台


            // 情况1：
            // 可以上传，即空闲状态，直接调用 info.send();
            // 情况2：
            // 正在上传或处理其他Camera操作，无法录像或上传，调用info.sendBusy();
        }

        @Override
        public void onTrackerReplyWiFiEnable(JMTrackReplyControlCmdInfo info, boolean bOpen) {
            JMLog.d("onTrackerReplyWiFiEnable bOpen:" + bOpen);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplyDVREnable(JMTrackReplyControlCmdInfo info, boolean bOpen) {
            JMLog.d("onTrackerReplyDVREnable bOpen:" + bOpen);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplyVideoFileList(int serverFlagId) {
            JMLog.d("onTrackerReplyVideoFileList");
            //处理文件相关设置或操作
        }

        @Override
        public void onTrackerReplySupportReplay(JMTrackReplyControlCmdInfo info) {
            JMLog.d("onTrackerReplySupportReplay");
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplyWiFiAPEnable(JMTrackReplyControlCmdInfo info, boolean bOpen) {
            JMLog.d("onTrackerReplyWiFiAPEnable bOpen:" + bOpen);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplyVolumeSetting(JMTrackReplyControlCmdInfo info, int volume) {
            JMLog.d("onTrackerReplyVolumeSetting volume:" + volume);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplyLedEnable(JMTrackReplyControlCmdInfo info, boolean bOpen) {
            JMLog.d("onTrackerReplyLedEnable bOpen:" + bOpen);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplyDefenseEnable(JMTrackReplyControlCmdInfo info, boolean bOpen) {
            JMLog.d("onTrackerReplyDefenseEnable bOpen:" + bOpen);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplyDefenseTime(JMTrackReplyControlCmdInfo info, int duration) {
            JMLog.d("onTrackerReplyDefenseTime duration:" + duration);
            //处理相关设置或操作
            mDataEditor.putInt("kDefenseTime", duration);
            mDataEditor.commit();
            info.sendOK();
        }

        @Override
        public void onTrackerReplyDefenseTimeQuery(JMTrackReplyControlCmdInfo info) {
            JMLog.d("onTrackerReplyDefenseTimeQuery");
            //获取本地设置的时间，然后返回
            int time = mSettingPreferences.getInt("kDefenseTime", 3);
            info.send(String.valueOf(time));
        }

        @Override
        public void onTrackerReplySpeedSetting(JMTrackReplyControlCmdInfo info, boolean bOpen, int duration, int speed) {
            JMLog.d("onTrackerReplySpeedSetting bOpen:" + bOpen + " duration:" + duration + " speed:" + speed);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplyRapidAccSetting(JMTrackReplyControlCmdInfo info, boolean bOpen, boolean bUpload, int sensitivity) {
            JMLog.d("onTrackerReplyRapidAccSetting bOpen:" + bOpen + " bUpload:" + bUpload + " sensitivity:" + sensitivity);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplyRapidDecSetting(JMTrackReplyControlCmdInfo info, boolean bOpen, boolean bUpload, int sensitivity) {
            JMLog.d("onTrackerReplyRapidDecSetting bOpen:" + bOpen + " bUpload:" + bUpload + " sensitivity:" + sensitivity);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplyRapidTurnSetting(JMTrackReplyControlCmdInfo info, boolean bOpen, boolean bUpload, int sensitivity) {
            JMLog.d("onTrackerReplyRapidTurnSetting bOpen:" + bOpen + " bUpload:" + bUpload + " sensitivity:" + sensitivity);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplySenalmSetting(JMTrackReplyControlCmdInfo info, boolean bOpen, boolean bUpload, int sensitivity, int videoTime) {
            JMLog.d("onTrackerReplySenalmSetting bOpen:" + bOpen + " bUpload:" + bUpload + " sensitivity" + sensitivity + " videoTime:" + videoTime);
            //处理相关设置或操作
            info.sendOK();
        }

        @Override
        public void onTrackerReplySosSetting(JMTrackReplyControlCmdInfo info, boolean bAdd, String sosListStr) {
            JMLog.d("onTrackerReplySosSetting bAdd:" + bAdd + " sosListStr:" + sosListStr);
            JSONArray sosJson = null;
            try {
                sosJson = new JSONArray(sosListStr);
                //获取3个号码进行其他操作
                mDataEditor.putString("kSosList", sosJson.toString());
                mDataEditor.commit();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            info.sendOK();
        }

        @Override
        public void onTrackerReplySosQuery(JMTrackReplyControlCmdInfo info) {
            JMLog.d("onTrackerReplySosQuery");

            //模拟SOS已设置的号码
            String sosListStr = mSettingPreferences.getString("kSosList", "[\"\",\"\",\"\"]");
            info.send(sosListStr);
        }

        @Override
        public void onTrackerReplyRelayEnable(JMTrackReplyControlCmdInfo info, boolean bOpen) {
            JMLog.d("onTrackerReplyRelayEnable bOpen:" + bOpen);
            //处理Relay的设置
            mDataEditor.putBoolean("kRelayEnable", bOpen);
            mDataEditor.commit();
            info.sendOK();
        }

        @Override
        public void onTrackerReplyRelayQuery(JMTrackReplyControlCmdInfo info) {
            JMLog.d("onTrackerReplyRelayQuery");
            boolean relayState = mSettingPreferences.getBoolean("kRelayEnable", false);
            info.send(String.valueOf(relayState));
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
                JMLog.e("onRequestPermissionsResult: 申请权限已拒绝");
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
        if (mCamera1 == null) {
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

                    avcEncoder1 = startAvcEncoder();
                } catch (IOException e) {
                    e.printStackTrace();
                    JMLog.e(e.getMessage());
                }
            }
        }
    }

    private void startFrontCamera() {
        if (mCamera2 == null) {
            int backCamId = findFrontCamera();
            if (backCamId < 0) {
                return;
            }
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

                    avcEncoder2 = startAvcEncoder();
                } catch (IOException e) {
                    e.printStackTrace();
                    JMLog.e(e.getMessage());
                }
            }
        }
    }

    private void closeCamera(Camera camera) {
        if (null != camera) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();

            if (camera == mCamera1) {
                closeAvcEncoder(avcEncoder1);
                avcEncoder1 = null;
                mCamera1 = null;
            } else if (camera == mCamera2) {
                closeAvcEncoder(avcEncoder2);
                avcEncoder2 = null;
                mCamera2 = null;
            }
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

    private void startAACEncoder() {
        if (aacEncoder == null) {
            aacEncoder = new HWAACEncoder(mHWAACEncoderListener);
            aacEncoder.startEncoder();
        }
    }

    private void closeAACEncoder() {
        if (aacEncoder != null) {
            aacEncoder.stopEncoder();
            aacEncoder = null;
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
                    info.alertContent = "Hello DVR";    //实际的报警内容，这里是模拟的

                    info.send();    //报警上报
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
            JMLog.d("--->Latitude:" + location.getLatitude() + " Longitude:" + location.getLongitude());
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

            positionInfo.send();    //定位上报
        }
    }

    private long getUTCTime() {
        Calendar cal = Calendar.getInstance();
        TimeZone tz = TimeZone.getTimeZone("GMT");
        cal.setTimeZone(tz);
        long time = cal.getTimeInMillis() / 1000;

        return time;
    }

    private void initPrefernces() {
        mSettingPreferences = getSharedPreferences("JimiSimulateData", Context.MODE_PRIVATE);
        mDataEditor = mSettingPreferences.edit();
    }
}
