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
        root.addView(UiKit.title(this, getString(R.string.mascot_settings_title)), UiKit.matchWrap());
        root.addView(UiKit.subtitle(this, getString(R.string.mascot_settings_subtitle)), UiKit.matchWrap());

        LinearLayout previewCard = UiKit.card(this);
        previewCard.addView(UiKit.sectionTitle(this, getString(R.string.section_current_mascot)), UiKit.matchWrap());
        previewCard.addView(MascotUi.compactStatus(this), UiKit.matchWrap());
        currentProfileView = UiKit.hint(this, "");
        currentProfileView.setPadding(0, UiKit.dp(this, 8), 0, 0);
        previewCard.addView(currentProfileView, UiKit.matchWrap());
        root.addView(previewCard, UiKit.matchWrap());

        LinearLayout profileCard = UiKit.card(this);
        profileCard.addView(UiKit.sectionTitle(this, getString(R.string.section_choose_mascot_slot)), UiKit.matchWrap());
        for (MascotProfile profile : MascotProfile.values()) {
            Button button = UiKit.entryButton(this, profile.displayName(this), profile.description(this));
            button.setTag("profile_" + profile.name());
            button.setOnClickListener(v -> {
                MascotStore.saveProfile(this, profile);
                rebuild();
            });
            profileCard.addView(button, UiKit.spaced(this, profile == MascotProfile.NONE ? 0 : 8));
        }
        root.addView(profileCard, UiKit.spaced(this, 12));

        LinearLayout assetCard = UiKit.card(this);
        assetCard.addView(UiKit.sectionTitle(this, getString(R.string.section_mascot_assets)), UiKit.matchWrap());
        iconPathView = UiKit.hint(this, "");
        Button iconButton = UiKit.entryButton(this, getString(R.string.action_import_mascot_icon), getString(R.string.action_import_mascot_icon_subtitle));
        iconButton.setOnClickListener(v -> openPicker("image/*", REQUEST_ICON));
        assetCard.addView(iconButton, UiKit.matchWrap());
        assetCard.addView(iconPathView, UiKit.spaced(this, 6));
        TextView voiceHint = UiKit.hint(this, getString(R.string.mascot_voice_reuse_hint));
        voiceHint.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 4));
        assetCard.addView(voiceHint, UiKit.matchWrap());
        for (MascotVoiceSlot slot : MascotVoiceSlot.values()) {
            assetCard.addView(voiceSlotRow(slot), UiKit.spaced(this, 8));
        }
        root.addView(assetCard, UiKit.spaced(this, 12));

        LinearLayout voiceCard = UiKit.card(this);
        voiceCard.addView(UiKit.sectionTitle(this, getString(R.string.section_voice_prompts)), UiKit.matchWrap());
        voiceEnabledCheckBox = new CheckBox(this);
        voiceEnabledCheckBox.setText(R.string.field_voice_enabled);
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

        Button previewButton = UiKit.primaryButton(this, getString(R.string.action_preview_block_voice));
        previewButton.setTag("preview_voice_" + MascotVoiceSlot.BLOCK_APPEARED.name());
        previewButton.setOnClickListener(v -> {
            if (!MascotSoundPlayer.canPlay(this, MascotVoiceSlot.BLOCK_APPEARED)) {
                Toast.makeText(this, getString(R.string.toast_voice_missing, MascotVoiceSlot.BLOCK_APPEARED.displayName(this)), Toast.LENGTH_SHORT).show();
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
        row.addView(UiKit.sectionTitle(this, slot.displayName(this)), UiKit.matchWrap());
        row.addView(UiKit.hint(this, slot.description(this)), UiKit.matchWrap());
        TextView path = UiKit.hint(this, voiceUriText(slot));
        path.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 6));
        row.addView(path, UiKit.matchWrap());
        Button importButton = UiKit.entryButton(
                this,
                getString(R.string.action_import_voice_format, slot.displayName(this)),
                getString(R.string.action_import_voice_subtitle)
        );
        importButton.setTag("voice_" + slot.name());
        importButton.setOnClickListener(v -> openPicker("audio/*", requestCodeFor(slot)));
        row.addView(importButton, UiKit.matchWrap());
        Button previewButton = UiKit.entryButton(
                this,
                getString(R.string.action_preview_voice_format, slot.displayName(this)),
                getString(R.string.action_preview_voice_subtitle)
        );
        previewButton.setTag("preview_voice_" + slot.name());
        previewButton.setOnClickListener(v -> {
            if (!MascotSoundPlayer.canPlay(this, slot)) {
                Toast.makeText(this, getString(R.string.toast_voice_missing, slot.displayName(this)), Toast.LENGTH_SHORT).show();
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
            currentProfileView.setText(getString(R.string.current_mascot_slot_format, profile.displayName(this)));
        }
        if (iconPathView != null) {
            String iconUri = MascotStore.getIconUri(this, profile);
            iconPathView.setText(iconUri.isEmpty() ? R.string.mascot_icon_missing : R.string.mascot_icon_imported);
        }
        if (volumeView != null) {
            volumeView.setText(getString(R.string.voice_volume_format, MascotStore.getVolumePercent(this)));
        }
        if (voiceEnabledCheckBox != null && voiceEnabledCheckBox.isChecked() != MascotStore.isVoiceEnabled(this)) {
            voiceEnabledCheckBox.setChecked(MascotStore.isVoiceEnabled(this));
        }
    }

    private void openPicker(String type, int requestCode) {
        if (MascotStore.getProfile(this) == MascotProfile.NONE) {
            Toast.makeText(this, R.string.toast_choose_mascot_first, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, R.string.toast_icon_imported, Toast.LENGTH_SHORT).show();
        } else {
            MascotVoiceSlot slot = voiceSlotForRequest(requestCode);
            if (slot != null) {
                MascotStore.setVoiceUri(this, profile, slot, uri.toString());
                Toast.makeText(this, getString(R.string.toast_voice_imported, slot.displayName(this)), Toast.LENGTH_SHORT).show();
            }
        }
        rebuild();
    }

    private String voiceUriText(MascotVoiceSlot slot) {
        String voiceUri = MascotStore.getVoiceUri(this, MascotStore.getProfile(this), slot);
        return getString(
                voiceUri.isEmpty() ? R.string.voice_not_imported_format : R.string.voice_imported_format,
                slot.displayName(this)
        );
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
