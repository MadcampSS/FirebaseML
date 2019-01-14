package com.example.voicerecognition;

public interface Cancelable extends Runnable {

    void cancel();
}