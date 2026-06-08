package com.addictionbuster;

import android.content.Context;

final class V2RuntimeMode {
    private static final boolean V2_ENFORCEMENT_ENABLED = true;

    private V2RuntimeMode() {
    }

    static boolean isEnabled(Context context) {
        return V2_ENFORCEMENT_ENABLED;
    }
}
