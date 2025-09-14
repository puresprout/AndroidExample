package com.purestation.androidexample;

interface ILogResultCallback {
    void onResult(boolean ok);
    void onError(String errorMessage);
}