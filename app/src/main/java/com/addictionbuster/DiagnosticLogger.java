package com.addictionbuster;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class DiagnosticLogger {
    private static final String TAG = "AddictionBuster";
    private static final String FILE_NAME = "diagnostic.log";
    private static final String PREFS_NAME = "diagnostic_log_state";
    private static final String KEY_LAST_PRUNE_MILLIS = "last_prune_millis";
    private static final int MAX_BYTES = 220 * 1024;
    private static final int TRIM_TO_BYTES = 140 * 1024;
    private static final int TIMESTAMP_LENGTH = 23;
    private static final long DEFAULT_RETENTION_MILLIS = 60L * 60L * 1000L;
    private static final long PRUNE_INTERVAL_MILLIS = 5L * 60L * 1000L;

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
            pruneOldEntriesIfDue(context, file);
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
        pruneOldEntries(context);
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

    static synchronized String readRecent(Context context, int minutes) {
        pruneOldEntries(context);
        String log = read(context);
        if (log.startsWith("暂无") || log.startsWith("读取诊断日志失败")) {
            return log;
        }

        long cutoffMillis = System.currentTimeMillis() - minutes * 60_000L;
        String filtered = filterRecent(log, cutoffMillis);
        if (filtered.trim().isEmpty()) {
            return "暂无最近 " + minutes + " 分钟诊断日志。";
        }
        return filtered;
    }

    static synchronized String lastImportantLine(Context context) {
        String recent = readRecent(context, 60);
        if (recent.startsWith("暂无")) {
            return "暂无最近事件";
        }

        String[] lines = recent.split("\\r?\\n");
        for (int index = lines.length - 1; index >= 0; index--) {
            String line = lines[index];
            if (line.contains("[event]")
                    || line.contains("[service]")
                    || line.contains("[v2]")
                    || line.contains("[challenge]")
                    || line.contains("[media]")
                    || line.contains("[usage]")
                    || line.contains("[rule]")) {
                return line;
            }
        }
        return "暂无最近关键事件";
    }

    static synchronized void clear(Context context) {
        File file = logFile(context);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete diagnostic log");
        }
        Log.i(TAG, "diagnostic log cleared");
    }

    static synchronized void pruneOldEntries(Context context) {
        File file = logFile(context);
        if (!file.exists()) {
            return;
        }
        try {
            String original = readFile(file);
            String pruned = filterRecent(original, System.currentTimeMillis() - DEFAULT_RETENTION_MILLIS);
            if (!original.equals(pruned)) {
                String marker = now() + " [log] 已自动清理 1 小时前的诊断日志。\n";
                try (FileOutputStream output = new FileOutputStream(file, false)) {
                    output.write(marker.getBytes(StandardCharsets.UTF_8));
                    output.write(pruned.getBytes(StandardCharsets.UTF_8));
                }
            }
            rememberPrune(context);
        } catch (IOException exception) {
            Log.w(TAG, "Failed to prune diagnostic log", exception);
        }
    }

    private static File logFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    private static void pruneOldEntriesIfDue(Context context, File file) throws IOException {
        if (!file.exists()) {
            rememberPrune(context);
            return;
        }

        long nowMillis = System.currentTimeMillis();
        long lastPruneMillis = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_PRUNE_MILLIS, 0L);
        if (nowMillis - lastPruneMillis < PRUNE_INTERVAL_MILLIS) {
            return;
        }

        String original = readFile(file);
        String pruned = filterRecent(original, nowMillis - DEFAULT_RETENTION_MILLIS);
        if (!original.equals(pruned)) {
            String marker = now() + " [log] 已自动清理 1 小时前的诊断日志。\n";
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(marker.getBytes(StandardCharsets.UTF_8));
                output.write(pruned.getBytes(StandardCharsets.UTF_8));
            }
        }
        rememberPrune(context);
    }

    private static void rememberPrune(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_PRUNE_MILLIS, System.currentTimeMillis())
                .apply();
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

    private static String readFile(File file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            byte[] data = new byte[(int) input.length()];
            input.readFully(data);
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static String filterRecent(String log, long cutoffMillis) {
        List<String> kept = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(log))) {
            String line;
            while ((line = reader.readLine()) != null) {
                long timestampMillis = timestampMillis(line);
                if (timestampMillis == 0L || timestampMillis >= cutoffMillis) {
                    kept.add(line);
                }
            }
        } catch (IOException ignored) {
            return log;
        }

        StringBuilder output = new StringBuilder();
        for (String line : kept) {
            output.append(line).append('\n');
        }
        return output.toString();
    }

    private static long timestampMillis(String line) {
        if (line.length() < TIMESTAMP_LENGTH) {
            return 0L;
        }
        String value = line.substring(0, TIMESTAMP_LENGTH);
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
                    .parse(value)
                    .getTime();
        } catch (ParseException ignored) {
            return 0L;
        }
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
                .format(new Date());
    }
}
