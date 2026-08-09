package com.addictionbuster;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ScheduledRulesActivity extends Activity {
    private CheckBox enabledCheckBox;
    private EditText startInput;
    private EditText endInput;
    private TextView summaryView;
    private final CheckBox[] dayBoxes = new CheckBox[7];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "rule", "schedule screen opened");
        setContentView(buildContent());
        refreshSummary();
    }

    private ScrollView buildContent() {
        LinearLayout root = UiKit.screen(this);
        root.addView(UiKit.title(this, getString(R.string.schedule_title)), UiKit.matchWrap());
        root.addView(UiKit.subtitle(this, getString(R.string.schedule_subtitle)), UiKit.matchWrap());

        LinearLayout statusCard = UiKit.card(this);
        statusCard.addView(UiKit.sectionTitle(this, getString(R.string.section_schedule_status)), UiKit.matchWrap());
        enabledCheckBox = new CheckBox(this);
        enabledCheckBox.setText(R.string.field_schedule_enabled);
        enabledCheckBox.setTextColor(UiKit.COLOR_TEXT);
        enabledCheckBox.setTextSize(15);
        enabledCheckBox.setChecked(V2RuleBridge.isScheduleEnabled(this));
        enabledCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> refreshSummary());
        statusCard.addView(enabledCheckBox, UiKit.matchWrap());
        summaryView = UiKit.hint(this, "");
        summaryView.setPadding(0, UiKit.dp(this, 8), 0, 0);
        statusCard.addView(summaryView, UiKit.matchWrap());
        root.addView(statusCard, UiKit.matchWrap());

        LinearLayout timeCard = UiKit.card(this);
        timeCard.addView(UiKit.sectionTitle(this, getString(R.string.section_schedule_window)), UiKit.matchWrap());
        startInput = timeInput(V2RuleBridge.getScheduleStartMinute(this));
        endInput = timeInput(V2RuleBridge.getScheduleEndMinute(this));
        timeCard.addView(field(getString(R.string.field_schedule_start), getString(R.string.hint_schedule_start), startInput), UiKit.matchWrap());
        timeCard.addView(field(getString(R.string.field_schedule_end), getString(R.string.hint_schedule_end), endInput), UiKit.matchWrap());
        root.addView(timeCard, UiKit.spaced(this, 12));

        LinearLayout daysCard = UiKit.card(this);
        daysCard.addView(UiKit.sectionTitle(this, getString(R.string.section_schedule_days)), UiKit.matchWrap());
        Set<Integer> activeDays = new HashSet<>();
        for (int day : V2RuleBridge.getScheduleActiveDays(this)) {
            activeDays.add(day);
        }
        String[] labels = getResources().getStringArray(R.array.schedule_day_labels);
        for (int index = 0; index < dayBoxes.length; index++) {
            CheckBox box = new CheckBox(this);
            int day = index + 1;
            box.setText(labels[index]);
            box.setTextColor(UiKit.COLOR_TEXT);
            box.setTextSize(15);
            box.setChecked(activeDays.contains(day));
            box.setOnCheckedChangeListener((buttonView, isChecked) -> refreshSummary());
            dayBoxes[index] = box;
            daysCard.addView(box, UiKit.matchWrap());
        }
        root.addView(daysCard, UiKit.spaced(this, 12));

        Button saveButton = UiKit.primaryButton(this, getString(R.string.action_save_schedule));
        saveButton.setOnClickListener(v -> saveSchedule());
        root.addView(saveButton, UiKit.spaced(this, 16));

        TextView hint = UiKit.hint(this, getString(R.string.schedule_hint));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, UiKit.dp(this, 14), 0, 0);
        root.addView(hint, UiKit.matchWrap());

        return UiKit.scrollScreen(this, root);
    }

    private LinearLayout field(String title, String hint, EditText input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, UiKit.dp(this, 7), 0, UiKit.dp(this, 11));
        box.addView(UiKit.text(this, title, 15, UiKit.COLOR_TEXT, true), UiKit.matchWrap());
        TextView hintView = UiKit.hint(this, hint);
        hintView.setPadding(0, UiKit.dp(this, 3), 0, UiKit.dp(this, 5));
        box.addView(hintView, UiKit.matchWrap());
        box.addView(input, UiKit.matchWrap());
        return box;
    }

    private EditText timeInput(int minuteOfDay) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_DATETIME);
        input.setText(formatTime(minuteOfDay));
        input.setTextSize(16);
        input.setPadding(UiKit.dp(this, 8), 0, UiKit.dp(this, 8), 0);
        return input;
    }

    private void saveSchedule() {
        try {
            int startMinute = parseTime(startInput.getText().toString());
            int endMinute = parseTime(endInput.getText().toString());
            if (startMinute == endMinute) {
                Toast.makeText(this, R.string.toast_schedule_same_time, Toast.LENGTH_SHORT).show();
                return;
            }
            int[] days = selectedDays();
            if (enabledCheckBox.isChecked() && days.length == 0) {
                Toast.makeText(this, R.string.toast_schedule_select_day, Toast.LENGTH_SHORT).show();
                return;
            }
            V2RuleBridge.saveSchedule(this, enabledCheckBox.isChecked(), startMinute, endMinute, days);
            Toast.makeText(this, R.string.toast_schedule_saved, Toast.LENGTH_SHORT).show();
            finish();
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, R.string.toast_schedule_invalid_time, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException exception) {
            DiagnosticLogger.log(this, "rule", "failed to save schedule error=" + exception.getMessage());
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private int[] selectedDays() {
        List<Integer> days = new ArrayList<>();
        for (int index = 0; index < dayBoxes.length; index++) {
            if (dayBoxes[index].isChecked()) {
                days.add(index + 1);
            }
        }
        int[] result = new int[days.size()];
        for (int index = 0; index < days.size(); index++) {
            result[index] = days.get(index);
        }
        return result;
    }

    private int parseTime(String value) {
        String[] parts = value.trim().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("time must use HH:mm");
        }
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("time out of range");
        }
        return hour * 60 + minute;
    }

    private String formatTime(int minuteOfDay) {
        int safeMinute = Math.max(0, Math.min(1439, minuteOfDay));
        return String.format(Locale.US, "%02d:%02d", safeMinute / 60, safeMinute % 60);
    }

    private void refreshSummary() {
        if (summaryView == null) {
            return;
        }
        if (!enabledCheckBox.isChecked()) {
            summaryView.setText(R.string.schedule_disabled_summary);
            return;
        }
        try {
            int startMinute = parseTime(startInput.getText().toString());
            int endMinute = parseTime(endInput.getText().toString());
            summaryView.setText(getString(
                    R.string.schedule_enabled_summary,
                    formatTime(startMinute),
                    formatTime(endMinute),
                    selectedDays().length
            ));
        } catch (RuntimeException ignored) {
            summaryView.setText(R.string.schedule_invalid_summary);
        }
    }
}
