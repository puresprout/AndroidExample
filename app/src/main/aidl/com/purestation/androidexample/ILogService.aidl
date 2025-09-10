// ILogService.aidl
package com.purestation.androidexample;

// Declare any non-default types here with import statements
import com.purestation.androidexample.service.LogEntry;

interface ILogService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
            double aDouble, String aString);

    oneway void sendLog(in LogEntry entry);
}