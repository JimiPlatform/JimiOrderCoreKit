package com.jimi.jimiordercorekitdemo;

import java.util.ArrayList;

public interface HWAACEncoderListener {

    public void pushAudioData(byte[] data, long timestamp);
}
