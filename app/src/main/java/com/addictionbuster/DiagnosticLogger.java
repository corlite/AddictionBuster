package com.addictionbuster;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DiagnosticLogger {
    private static final String TAG = "AddictionBuster";
    private static final String FILE_NAME = "diagnostic.log";
    private static final int MAX_BYTES = 220 * 1024;
    private static final int TRIM_TO_BYTES = 140 * 1024;

    private DiagnosticLogger() {
    }

    static synchronized void log(Context context, String category, String message) {
        String line = now() + " [" + category + "] " + message;
        Log.i(TAG, category + ": " + message);

        if (context == null) {
            return;
        }

        try {
            File file = logFile(context);
            trimIfNeeded(file);
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(line);
                writer.write('\n');
            }
        } catch (IOException exception) {
            Log.w(TAG, "Failed to write diagnostic log", exception);
        }
    }

    static synchronized String read(Context context) {
        File file = logFile(context);
        if (!file.exists()) {
            return "暂无诊断日志。";
        }

        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            byte[] data = new byte[(int) input.length()];
            input.readFully(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            Log.w(TAG, "Failed to read diagnostic log", exception);
            return "读取诊断日志失败：" + exception.getMessage();
        }
    }

    static synchronized void clear(Context context) {
        File file = logFile(context);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete diagnostic log");
        }
        Log.i(TAG, "diagnostic log cleared");
    }

    private static File logFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    private static void trimIfNeeded(File file) throws IOException {
        if (!file.exists() || file.length() <= MAX_BYTES) {
            return;
        }

        int tailLength = (int) Math.min(TRIM_TO_BYTES, file.length());
        byte[] tail = new byte[tailLength];
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(file.length() - tailLength);
            input.readFully(tail);
        }

        String marker = now() + " [log] 日志超过上限，已保留最近片段。\n";
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(marker.getBytes(StandardCharsets.UTF_8));
            output.write(tail);
        }
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
                .format(new Date());
    }
}
