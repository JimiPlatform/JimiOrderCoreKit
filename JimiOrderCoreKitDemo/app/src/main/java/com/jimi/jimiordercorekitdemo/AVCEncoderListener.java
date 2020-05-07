package com.jimi.jimiordercorekitdemo;

import java.util.ArrayList;

public interface AVCEncoderListener {

    public void pushVideoData(AVCEncoder avcEncoder, byte[] data, long timestamp, boolean isKey);
}
