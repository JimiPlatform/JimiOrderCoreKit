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
import com.jimi.jimiordercorekit.JMOrderPusherListener;
import com.jimi.jimiordercorekit.JMOrderServerListener;
import com.jimi.jimiordercorekit.JMOrderTrackerListener;
import com.jimi.jimiordercorekit.Model.JMTrackAlertInfo;
import com.jimi.jimiordercorekit.Model.JMTrackReplyControlCmdInfo;
import com.jimi.jimiordercorekit.Model.JMTrackHeartbeatInfo;
import com.jimi.jimiordercorekit.Model.JMTrackPositionInfo;
import com.jimi.jimiordercorekit.Utils.JMSDKModeEnum;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static com.jimi.jimiordercorekit.Model.JMTrackAlertInfo.JMAlertType_RearviewCameraShake;
import static com.jimi.jimiordercorekit.Model.JMTrackHeartbeatInfo.JMGSMSignal_Stronger;
import static com.jimi.jimiordercorekit.Model.JMTrackPositionInfo.JMPositionMode_Timing;

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

    /*以下是音视频编码器的相关参数，具体数据需要根据开发者的项目决定*/
    private int mVideoWidth = 640;          //视频编码宽度
    private int mVideoHeight = 480;         //视频编码高度
    private int mVideoFrameRate = 15;       //视频编码帧率
    private int mVideoBitRate = 512000;     //视频编码比特率
    private int mAudioSampleRate = 44100;   //音频编码采样率
    private int mAudioChannels = 1;         //音频编码通道数
    private int mAudioBitRate = 128000;     //音频编码比特率

    private String mIMEI = "357730091014168"; //"357730091014168";
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

        ZJLog.config.setSaveEnable(true);   //日志保存到内置存储卡

        JMOrderCoreKit.initialize(getApplication());
        JMOrderCoreKit.configMediaPara(0, mVideoWidth, mVideoHeight, mVideoFrameRate * 2 /*实际项目中不要*2*/, mVideoBitRate,
                mAudioSampleRate, mAudioChannels, mAudioBitRate);
        JMOrderCoreKit.configMediaPara(1, mVideoWidth, mVideoHeight, mVideoFrameRate * 2 /*实际项目中不要*2*/, mVideoBitRate,
                mAudioSampleRate, mAudioChannels, mAudioBitRate);
        if (!bSupportMulCamera) {   //如果设备不支持多路同时播放
            JMOrderCoreKit.closeMulCamera();        //关闭多路功能，仅支持单路播放
        }
//        JMOrderCoreKit.configGatewayServer("36.133.0.208", 31100);      //配置网关调试服务器（正式版不需要）
//        JMOrderCoreKit.configGatewayServer("8.210.110.221", 21100);      //配置网关调试服务器（正式版不需要）
//        JMOrderCoreKit.configLiveServer("", "36.133.0.208");        //配置RTMP调试服务器（正式版不需要）
        JMOrderCoreKit.closeSSL();  //关闭视频加密

        //DVR服务
        mJMOrderCoreKit = new JMOrderCoreKit(getApplicationContext(), mIMEI);
        mJMOrderCoreKit.setServerDelegate(mJMOrderServerListener);
        mJMOrderCoreKit.setTrackerDelegate(mJMOrderTrackerListener);
        mJMOrderCoreKit.setPusherDelegate(mJMOrderPusherListener);

        //DVR和Tracker分离需要调用下面的配置
//        mJMOrderCoreKit.configSDKMode(JMSDKModeEnum.Pusher);        //仅DVR实时视频模式
//        mJMOrderCoreKit.configSDKMode(JMSDKModeEnum.Tracker);     //仅Tracker模式

        //单机模式配置
//        mJMOrderCoreKit.configSDKMode(JMSDKModeEnum.Tracker_StandAlone);    //DVR单机版单，Tracker和Pusher分离，Tracker部分
        mJMOrderCoreKit.configSDKMode(JMSDKModeEnum.All_StandAlone);    //DVR单机版单APP

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
    protected void onStart() {
        super.onStart();
        final Handler mHandler = new Handler();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (mJMOrderCoreKit.getSDKMode() != JMSDKModeEnum.Tracker) {    //模拟开始摄像头
                    startBackCamera();
                    startFrontCamera();     //若设备部支持前摄像头或启动失败需要屏蔽此行
                    startAACEncoder();

//                    mJMOrderCoreKit.initPusher("rtmp://live.jimivideo.com/live/test?secret=admin_xzl_180236", false);
//                    mJMOrderCoreKit.initPusher("rtmp://10.0.17.141:1935/lzj/room", true);
//                    mJMOrderCoreKit.initPusher("rtmp://113.108.62.203/live/357730090100224?user=jimi&pass=88888888", true);
//                    mJMOrderCoreKit.initPusher("rtmp://vitrixe.vn/live/357730090100224?user=jimi&pass=88888888", true);
//                    mJMOrderCoreKit.initPusher("rtmp://live.skysoft.vn/live/357730090100224?user=jimi&pass=88888888", true);
                }
            }
        };
        mHandler.postDelayed(r, 500);
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (bStopPlay) return;

        if (camera == mCamera1 && (bSupportMulCamera || (!bSupportMulCamera && !bFrontCamera))) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            byte[] tmpBuf = new byte[size.width * size.height * 3 / 2];
            System.arraycopy(bytes, 0, tmpBuf, 0, tmpBuf.length);
            if (avcEncoder1 != null) {
                avcEncoder1.encodeNV21Data(tmpBuf);
            }
        } else if (camera == mCamera2 && (bSupportMulCamera || (!bSupportMulCamera && bFrontCamera))) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            byte[] tmpBuf = new byte[size.width * size.height * 3 / 2];
            System.arraycopy(bytes, 0, tmpBuf, 0, tmpBuf.length);
            if (avcEncoder2 != null) {
                avcEncoder2.encodeNV21Data(tmpBuf);
            }
        }
    }

    private HWAACEncoderListener mHWAACEncoderListener = new HWAACEncoderListener() {

        @Override
        public void pushAudioData(byte[] data, long timestamp) {
            if (mJMOrderCoreKit != null && !bIsPlayback1) {
                mJMOrderCoreKit.pushAudioData(0, data);
            }

            if (mJMOrderCoreKit != null && !bIsPlayback2) {
                mJMOrderCoreKit.pushAudioData(1, data);
            }
        }
    };

    private AVCEncoderListener mAVCEncoderListener = new AVCEncoderListener() {

        @Override
        public void pushVideoData(AVCEncoder avcEncoder, byte[] data, long timestamp, boolean isKey) {
            if (mJMOrderCoreKit != null) {
                if (!bSupportMulCamera && !bIsPlayback1) {   //单路
                    mJMOrderCoreKit.pushVideoData(0, data, isKey);
                } else if (avcEncoder == avcEncoder1 && !bIsPlayback1) {
                    mJMOrderCoreKit.pushVideoData(0, data, isKey);
                } else if (avcEncoder == avcEncoder2 && !bIsPlayback2) {
                    mJMOrderCoreKit.pushVideoData(1, data, isKey);
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
            if (state == 2 && (mJMOrderCoreKit.getSDKMode() != JMSDKModeEnum.Tracker)) {  //已连接，且是包含Pusher模式才启动
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
        public void onPusherPlayStatus(int channel, String appId, String url, boolean bPlay) {
            ZJLog.d("onPusherPlayStatus channel:" + channel + " AppId:" + appId + " bPlay:" + bPlay);
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

            // 配置RTSP实时推流地址
            JMOrderCoreKit.configRTSPLiveServer("Play_RTSP_Push_Url", "Play_RTSP_Push_Server_IP");
        }

        @Override
        public ArrayList<String> onPusherQueryPlaybackList(int channel, String appId, long startTime, long endTime) {

            //模拟数据，正式项目应该按照channel、startTime、endTime查询真实的视频文件列表名称
            ArrayList<String> list = new ArrayList<>();
            list.add("2020_06_10_00_00_01_01.mp4");
            list.add("2020_06_10_00_01_01_01.mp4");
            list.add("2020_06_10_00_02_01_01.mp4");
            list.add("2020_06_10_00_03_01_01.mp4");
            list.add("2020_06_10_00_04_01_01.mp4");
            list.add("2020_06_10_00_05_01_01.mp4");
            list.add("2020_06_10_00_06_01_01.mp4");
            list.add("2020_06_10_00_07_01_01.mp4");
            list.add("2020_06_10_00_08_01_01.mp4");
            list.add("2020_06_10_00_09_01_01.mp4");
            list.add("2020_06_10_00_10_01_01.mp4");
            list.add("2020_06_10_00_11_01_01.mp4");
            list.add("2020_06_10_00_12_01_01.mp4");
            list.add("2020_06_10_00_13_01_01.mp4");
            list.add("2020_06_10_00_14_01_01.mp4");
            list.add("2020_06_10_00_15_01_01.mp4");
            list.add("2020_06_10_00_16_01_01.mp4");
            list.add("2020_06_10_00_17_01_01.mp4");
            list.add("2020_06_10_00_18_01_01.mp4");
            list.add("2020_06_10_00_19_01_01.mp4");
            list.add("2020_06_10_00_20_01_01.mp4");
            list.add("2020_06_10_00_21_01_01.mp4");
            list.add("2020_06_10_00_22_01_01.mp4");
            list.add("2020_06_10_00_23_01_01.mp4");
            list.add("2020_06_10_00_24_01_01.mp4");
            list.add("2020_06_10_00_25_01_01.mp4");
            list.add("2020_06_10_00_26_01_01.mp4");
            list.add("2020_06_10_00_27_01_01.mp4");
            list.add("2020_06_10_00_28_01_01.mp4");
            list.add("2020_06_10_00_29_01_01.mp4");

            return list;
        }

        @Override
        public ArrayList<String> onPusherPlaybackList(int channel, String appId, String url, String fileNameJson) {
            ZJLog.d("onPusherPlaybackList channel:" + channel + " AppId:" + appId + " fileNameJson:" + fileNameJson);

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

                    // 配置RTSP回放推流地址
                    JMOrderCoreKit.configRTSPLiveServer("Playback_RTSP_Push_Url", "Playback_RTSP_Push_Server_IP");

                    return list;
                }
            } catch (JSONException e) {
                e.printStackTrace();
                bStopPlay = true;
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
        public void onPusherTalkStatus(String appID, String url, boolean bTalk) {
            ZJLog.d("onPusherTalkStatus AppID:" + appID + " bTalk:" + bTalk);
        }

        @Override
        public void onPusherSwitchCamera(String appID, String url, boolean bFrontCameraT) {
            ZJLog.d("onPusherSwitchCamera AppID:" + appID + " bFrontCamera:" + bFrontCameraT);
            if (!bSupportMulCamera) {
                bStopPlay = false;
                bFrontCamera = bFrontCameraT;
                startFrontCamera();
            }
        }

        @Override
        public String onPusherRecvCmdData(String appID, int cmdCode, String data, int serverFlag, short serial) {
            ZJLog.d("onPusherRecvCmdData AppID:" + appID + " cmdCode:" + cmdCode + " data:" + data);

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
            info.signalStrength = JMGSMSignal_Stronger;
        }

        @Override
        public void onTrackerReplyControl(JMTrackReplyControlCmdInfo info, String cmdContent) {
            ZJLog.d("onTrackerReplyControl cmdContent:" + cmdContent);      //正式版中类似Log的代码可以删掉

            //处理SDK内部未收录的指令，需要自行解析，之后在info.replyContent中填入相应需要回复的内容
            info.sendOK();
        }

        @Override
        public void onTrackerRecvControlData(byte[] data) {
            ZJLog.e("onTrackerRecvControlData");
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
                    ZJLog.e(e.getMessage());
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
                    ZJLog.e(e.getMessage());
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
        GPSUtils.getInstance(getApplicationContext()).getLngAndLat(new GPSUtils.OnLocationResultListener() {

            @Override
            public void onLocationResult(Location location) {
                ZJLog.d("1--->Latitude:" + location.getLatitude() + " Longitude:" + location.getLongitude());
                sendPositionInfo(location);
            }

            @Override
            public void OnLocationChange(Location location) {
                if (positionCount % 2 == 0) {       //模拟告警
                    JMTrackAlertInfo info = new JMTrackAlertInfo();
                    info.latitude = location.getLatitude();
                    info.longitude = location.getLongitude();
                    info.gpsEnable = true;
                    info.direction = 0;
                    info.alertType = JMAlertType_RearviewCameraShake;
                    info.alertContent = "Hello DVR";    //实际的报警内容，这里是模拟的
//                    info.alertContent = "Hello DVR".getBytes();
//                    info.speed = 300;

                    info.send();    //报警上报
                } else {
                    ZJLog.d("2--->Latitude:" + location.getLatitude() + " Longitude:" + location.getLongitude());
                    sendPositionInfo(location);
                }
                positionCount++;
            }
        });
    }

    private void stopPosition() {
        GPSUtils.getInstance(getApplicationContext()).removeListener();
    }

    private void sendPositionInfo(Location location) {
        if (location != null) {
            positionInfo.latitude = location.getLatitude();
            positionInfo.longitude = location.getLongitude();
            positionInfo.reportMode = JMPositionMode_Timing;
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
}
