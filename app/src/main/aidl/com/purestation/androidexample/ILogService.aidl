// ILogService.aidl
package com.purestation.androidexample;

// Declare any non-default types here with import statements
import com.purestation.androidexample.service.LogEntry;
import com.purestation.androidexample.ILogResultCallback;

interface ILogService {
    oneway void sendLog(in LogEntry entry);

    void sendLogWithCallback(in LogEntry entry, ILogResultCallback cb);
}