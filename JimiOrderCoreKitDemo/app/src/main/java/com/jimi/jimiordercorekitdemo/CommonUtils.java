package com.jimi.jimiordercorekitdemo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.telephony.TelephonyManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.jimi.jmlog.JMLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.UUID;

public class CommonUtils {
    static private Thread mPingThread = null;
    static private Thread mPingLoopThread = null;
    static private boolean bIsRungPing = false;
    static private Process mPingProcess = null;
    static private String mPingHosts = null;

    //获取随机IMEI
    public static String getRandomImei() {
        UUID uuid = UUID.randomUUID();
        String str_uuid = uuid.toString().replace("-", "");
        return str_uuid;
    }

    private static boolean isExistContextCompat() {
        try {
            Class.forName("android.support.v4.content.ContextCompat");
            Class.forName("android.support.v4.content.PermissionChecker");
        } catch (ClassNotFoundException e) {
            JMLog.e("Error: Not exist android.support.v4.content.*");
            return false;
        }
        return true;
    }

    /*是否有读取手机状态的权限*/
    public static boolean isReadPhoneState(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context != null) {
                return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                        PackageManager.PERMISSION_GRANTED;
            } else {
                return false;
            }
        }

        return true;
    }

    /*是否有读取存储卡的权限*/
    public static boolean isReadExternalStorage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context != null) {
                return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
            } else {
                return false;
            }
        }

        return true;
    }

    /*是否有写入存储卡的权限*/
    public static boolean isWriteExternalStorage(Context context) {
        if (!CommonUtils.isExistContextCompat()) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context != null) {
                return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
            } else {
                return false;
            }
        } else {
            return PermissionChecker.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED;
        }
    }

    /*是否有麦克风权限的权限*/
    public static boolean isRecordAudio(Context context) {
        if (!CommonUtils.isExistContextCompat()) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context != null) {
                return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;
            } else {
                return false;
            }
        } else {
            return PermissionChecker.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED;
        }
    }

    public static boolean isMainThread() {
        return Looper.getMainLooper().getThread().getId() == Thread.currentThread().getId();
    }

    /*默认根目录下TimeLine文件夹，可根据自身项目决定是否使用*/
    public static String getPlaybackFilePath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "TimeLine"
                + File.separator;
    }

    /*Ping服务器，时间间隔5秒每次*/
    public static void pingStart() {
        if (mPingLoopThread != null && mPingLoopThread.isAlive()) {
            return;
        }
        mPingLoopThread = null;

        if (mPingLoopThread == null) {
            bIsRungPing = true;
            mPingLoopThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (bIsRungPing) {
                        ping("live.jimivideo.com");
                        try {
                            Thread.sleep(5 * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    JMLog.i("Ping server " + "live.jimivideo.com" + " thread exit");
                }
            });
            mPingLoopThread.start();
        }
    }

    /*停止PING*/
    public static void pingStop() {
        bIsRungPing = false;
        if (mPingThread != null) {
            mPingThread.interrupt();
            try {
                mPingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mPingThread = null;
        }

        if (mPingLoopThread != null) {
            mPingLoopThread.interrupt();
            try {
                mPingLoopThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mPingLoopThread = null;
        }
    }


    /*PING一次服务器*/
    public static void pingOnce(String hosts) {
        bIsRungPing = true;
        if (hosts == null || hosts.isEmpty()) {
            ping("live.jimivideo.com");
        } else {
            ping(hosts);
        }
    }

    private static void ping(String str) {
        if (mPingThread != null && mPingThread.isAlive()) {
            return;
        } else if (mPingThread != null && !mPingThread.isAlive()) {
            mPingThread = null;
        }

        mPingHosts = str;
        if (mPingThread == null && mPingHosts != null) {
            mPingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mPingProcess == null) {
                            //ping -c 3 -w 100  中  ，-c 是指ping的次数 3是指ping 3次 ，-w 100  以秒为单位指定超时间隔，是指超时时间为100秒
                            mPingProcess = Runtime.getRuntime().exec("ping -c 1 -w 1 " + mPingHosts);

                            InputStream input = mPingProcess.getInputStream();
                            BufferedReader in = new BufferedReader(new InputStreamReader(input));
                            StringBuffer buffer = new StringBuffer();
                            String line = "";
                            while (bIsRungPing && (line = in.readLine()) != null) {
                                buffer.append(line);
                            }

                            String resultStr = null;
                            if (bIsRungPing) {
                                String splitStr1[] = buffer.toString().split("--- " + mPingHosts + " ping statistics ---");
                                if (splitStr1.length == 1 || splitStr1[0].contains("icmp_seq=")) {
                                    resultStr = splitStr1[0];
                                    JMLog.i(resultStr);
                                } else {
                                    resultStr = splitStr1[0] + splitStr1[1];
                                    JMLog.i(resultStr);
                                }
                            }

                            try {
                                mPingProcess.exitValue();
                                mPingProcess.destroy();
                            } catch (IllegalThreadStateException e) {
                            }
                            mPingProcess = null;

                            if (bIsRungPing && (resultStr == null || resultStr.isEmpty())) {
                                JMLog.w("TEST server failed: " + mPingHosts);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            mPingThread.start();
        }
    }

    public static String getNetworkType(Object contextObj) {
        if (contextObj == null) return "None";

        Context context = (Context) contextObj;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null) {
                if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    return "WiFi";
                } else if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                    int nSubType = networkInfo.getSubtype();
                    TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                    if (nSubType == TelephonyManager.NETWORK_TYPE_LTE
                            && !telephonyManager.isNetworkRoaming()) {
                        return "4G";
                    } else if (nSubType == TelephonyManager.NETWORK_TYPE_UMTS
                            || nSubType == TelephonyManager.NETWORK_TYPE_HSDPA
                            || nSubType == TelephonyManager.NETWORK_TYPE_EVDO_0
                            && !telephonyManager.isNetworkRoaming()) {
                        return "3G";
                    } else if (nSubType == TelephonyManager.NETWORK_TYPE_GPRS
                            || nSubType == TelephonyManager.NETWORK_TYPE_EDGE
                            || nSubType == TelephonyManager.NETWORK_TYPE_CDMA
                            && !telephonyManager.isNetworkRoaming()) {
                        return "2G";
                    } else {
                        return "4G/3G";
                    }
                } else {
                    return "None";
                }
            }
        }
        return "None";
    }


    public static Location getMyLocation(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        List<String> list = locationManager.getProviders(true);

        String provider = null;
        if (list.contains(locationManager.GPS_PROVIDER)) {
            provider = locationManager.GPS_PROVIDER;//GPS定位
        } else if (list.contains(locationManager.NETWORK_PROVIDER)) {
            provider = locationManager.NETWORK_PROVIDER;//网络定位
        }

        if (provider != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            Location lastKnownLocation = locationManager.getLastKnownLocation(provider);

            return lastKnownLocation;
        } else {
            JMLog.e("请检查网络或GPS是否打开");
        }


        return null;
    }

}
