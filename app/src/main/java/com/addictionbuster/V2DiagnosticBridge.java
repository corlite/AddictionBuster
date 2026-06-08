package com.addictionbuster;

import android.content.Context;

public final class V2DiagnosticBridge {
    private V2DiagnosticBridge() {
    }

    public static void log(Context context, String category, String message) {
        DiagnosticLogger.log(context, category, message);
    }
}
