package com.addictionbuster;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MascotSettingsActivity extends Activity {
    private static final int REQUEST_ICON = 3101;
    private static final int REQUEST_VOICE_BASE = 3200;

    private TextView currentProfileView;
    private TextView iconPathView;
    private TextView volumeView;
    private CheckBox voiceEnabledCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "mascot", "mascot settings opened");
        setContentView(buildContent());
    }

    private ScrollView buildContent() {
        LinearLayout root = UiKit.screen(this);
        root.addView(UiKit.title(this, "角色与语音"), UiKit.matchWrap());
        root.addView(UiKit.subtitle(this, "先保留咕嘎、Doro 和自定义坑位；后期导入图标和语音就能直接启用。"), UiKit.matchWrap());

        LinearLayout previewCard = UiKit.card(this);
        previewCard.addView(UiKit.sectionTitle(this, "当前角色"), UiKit.matchWrap());
        previewCard.addView(MascotUi.compactStatus(this), UiKit.matchWrap());
        currentProfileView = UiKit.hint(this, "");
        currentProfileView.setPadding(0, UiKit.dp(this, 8), 0, 0);
        previewCard.addView(currentProfileView, UiKit.matchWrap());
        root.addView(previewCard, UiKit.matchWrap());

        LinearLayout profileCard = UiKit.card(this);
        profileCard.addView(UiKit.sectionTitle(this, "选择角色槽位"), UiKit.matchWrap());
        for (MascotProfile profile : MascotProfile.values()) {
            Button button = UiKit.entryButton(this, profile.displayName(), profile.description());
            button.setTag("profile_" + profile.name());
            button.setOnClickListener(v -> {
                MascotStore.saveProfile(this, profile);
                rebuild();
            });
            profileCard.addView(button, UiKit.spaced(this, profile == MascotProfile.NONE ? 0 : 8));
        }
        root.addView(profileCard, UiKit.spaced(this, 12));

        LinearLayout assetCard = UiKit.card(this);
        assetCard.addView(UiKit.sectionTitle(this, "素材导入"), UiKit.matchWrap());
        iconPathView = UiKit.hint(this, "");
        Button iconButton = UiKit.entryButton(this, "导入当前角色图标", "选择 PNG、JPG 或 WebP 图片");
        iconButton.setOnClickListener(v -> openPicker("image/*", REQUEST_ICON));
        assetCard.addView(iconButton, UiKit.matchWrap());
        assetCard.addView(iconPathView, UiKit.spaced(this, 6));
        TextView voiceHint = UiKit.hint(this, "语音不能复用：每个角色槽位需要分别导入 9 条场景语音。");
        voiceHint.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 4));
        assetCard.addView(voiceHint, UiKit.matchWrap());
        for (MascotVoiceSlot slot : MascotVoiceSlot.values()) {
            assetCard.addView(voiceSlotRow(slot), UiKit.spaced(this, 8));
        }
        root.addView(assetCard, UiKit.spaced(this, 12));

        LinearLayout voiceCard = UiKit.card(this);
        voiceCard.addView(UiKit.sectionTitle(this, "语音提示"), UiKit.matchWrap());
        voiceEnabledCheckBox = new CheckBox(this);
        voiceEnabledCheckBox.setText("开启角色语音");
        voiceEnabledCheckBox.setTextColor(UiKit.COLOR_TEXT);
        voiceEnabledCheckBox.setTextSize(15);
        voiceEnabledCheckBox.setChecked(MascotStore.isVoiceEnabled(this));
        voiceEnabledCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            MascotStore.setVoiceEnabled(this, isChecked);
            updateViews();
        });
        voiceCard.addView(voiceEnabledCheckBox, UiKit.matchWrap());

        volumeView = UiKit.hint(this, "");
        voiceCard.addView(volumeView, UiKit.spaced(this, 8));
        SeekBar volumeBar = new SeekBar(this);
        volumeBar.setMax(100);
        volumeBar.setProgress(MascotStore.getVolumePercent(this));
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MascotStore.setVolumePercent(MascotSettingsActivity.this, progress);
                    updateViews();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        voiceCard.addView(volumeBar, UiKit.matchWrap());

        Button previewButton = UiKit.primaryButton(this, "试听拦截出现语音");
        previewButton.setTag("preview_voice_" + MascotVoiceSlot.BLOCK_APPEARED.name());
        previewButton.setOnClickListener(v -> {
            if (!MascotSoundPlayer.canPlay(this, MascotVoiceSlot.BLOCK_APPEARED)) {
                Toast.makeText(this, "当前角色还没有导入拦截出现语音", Toast.LENGTH_SHORT).show();
                return;
            }
            MascotSoundPlayer.play(this, MascotVoiceSlot.BLOCK_APPEARED);
        });
        voiceCard.addView(previewButton, UiKit.spaced(this, 12));
        root.addView(voiceCard, UiKit.spaced(this, 12));

        updateViews();
        return UiKit.scrollScreen(this, root);
    }

    private LinearLayout voiceSlotRow(MascotVoiceSlot slot) {
        LinearLayout row = UiKit.card(this);
        row.addView(UiKit.sectionTitle(this, slot.displayName()), UiKit.matchWrap());
        row.addView(UiKit.hint(this, slot.description()), UiKit.matchWrap());
        TextView path = UiKit.hint(this, voiceUriText(slot));
        path.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 6));
        row.addView(path, UiKit.matchWrap());
        Button importButton = UiKit.entryButton(this, "导入" + slot.displayName() + "语音", "选择 OGG、MP3 或 WAV 音频");
        importButton.setTag("voice_" + slot.name());
        importButton.setOnClickListener(v -> openPicker("audio/*", requestCodeFor(slot)));
        row.addView(importButton, UiKit.matchWrap());
        Button previewButton = UiKit.entryButton(this, "试听" + slot.displayName(), "使用当前音量播放这一条语音");
        previewButton.setTag("preview_voice_" + slot.name());
        previewButton.setOnClickListener(v -> {
            if (!MascotSoundPlayer.canPlay(this, slot)) {
                Toast.makeText(this, "当前角色还没有导入" + slot.displayName() + "语音", Toast.LENGTH_SHORT).show();
                return;
            }
            MascotSoundPlayer.play(this, slot);
        });
        row.addView(previewButton, UiKit.spaced(this, 8));
        return row;
    }

    private void rebuild() {
        setContentView(buildContent());
    }

    private void updateViews() {
        MascotProfile profile = MascotStore.getProfile(this);
        if (currentProfileView != null) {
            currentProfileView.setText("当前槽位：" + profile.displayName());
        }
        if (iconPathView != null) {
            String iconUri = MascotStore.getIconUri(this, profile);
            iconPathView.setText(iconUri.isEmpty() ? "当前槽位还没有图标。" : "图标已导入。");
        }
        if (volumeView != null) {
            volumeView.setText("音量：" + MascotStore.getVolumePercent(this) + "%");
        }
        if (voiceEnabledCheckBox != null && voiceEnabledCheckBox.isChecked() != MascotStore.isVoiceEnabled(this)) {
            voiceEnabledCheckBox.setChecked(MascotStore.isVoiceEnabled(this));
        }
    }

    private void openPicker(String type, int requestCode) {
        if (MascotStore.getProfile(this) == MascotProfile.NONE) {
            Toast.makeText(this, "请先选择咕嘎、Doro 或自定义槽位", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException exception) {
                DiagnosticLogger.log(this, "mascot", "persist uri permission failed error=" + exception.getMessage());
            }
        }

        MascotProfile profile = MascotStore.getProfile(this);
        if (requestCode == REQUEST_ICON) {
            MascotStore.setIconUri(this, profile, uri.toString());
            Toast.makeText(this, "图标已导入", Toast.LENGTH_SHORT).show();
        } else {
            MascotVoiceSlot slot = voiceSlotForRequest(requestCode);
            if (slot != null) {
                MascotStore.setVoiceUri(this, profile, slot, uri.toString());
                Toast.makeText(this, slot.displayName() + "语音已导入", Toast.LENGTH_SHORT).show();
            }
        }
        rebuild();
    }

    private String voiceUriText(MascotVoiceSlot slot) {
        String voiceUri = MascotStore.getVoiceUri(this, MascotStore.getProfile(this), slot);
        return voiceUri.isEmpty() ? "未导入：" + slot.displayName() : "已导入：" + slot.displayName();
    }

    private int requestCodeFor(MascotVoiceSlot slot) {
        return REQUEST_VOICE_BASE + slot.ordinal();
    }

    private MascotVoiceSlot voiceSlotForRequest(int requestCode) {
        int ordinal = requestCode - REQUEST_VOICE_BASE;
        MascotVoiceSlot[] values = MascotVoiceSlot.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return null;
        }
        return values[ordinal];
    }
}
