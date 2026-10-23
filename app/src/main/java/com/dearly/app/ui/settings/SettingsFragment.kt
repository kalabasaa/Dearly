package com.dearly.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.ItemSettingRowBinding
import com.dearly.app.databinding.FragmentSettingsBinding
import com.dearly.app.databinding.LayoutRecoveryFormBinding
import com.dearly.app.security.Session
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.PinGate
import com.dearly.app.ui.PinPrompt
import com.dearly.app.ui.RecoveryFormController
import com.dearly.app.ui.common.InputDialog
import com.dearly.app.ui.common.background
import com.dearly.app.ui.common.show
import com.dearly.app.ui.stickers.StickerPickerActivity
import com.dearly.app.utils.TextUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private val host get() = requireActivity() as BaseActivity

    private class Snapshot(
        val name: String, val dark: Boolean,
        val hasRecovery: Boolean, val showTitles: Boolean, val encrypted: Boolean
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) rebuild()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun rebuild() {
        if (_b == null || !Session.unlocked || isHidden) return
        background(work = {
            Snapshot(
                ServiceLocator.settings.userName, ServiceLocator.settings.darkMode,
                ServiceLocator.auth.hasRecovery(),
                ServiceLocator.settings.showTitlesInNotifications, ServiceLocator.secureStore.isEncrypted
            )
        }) { render(it) }
    }

    // ------------------------------------------------------------------ rows

    private fun header(res: Int) {
        val tv = LayoutInflater.from(requireContext()).inflate(R.layout.item_header, b.settingsRoot, false) as android.widget.TextView
        tv.setText(res)
        tv.setPadding(tv.paddingLeft + 0, tv.paddingTop + 8, tv.paddingRight, tv.paddingBottom)
        b.settingsRoot.addView(tv)
        (tv.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart = 16
    }

    private fun row(title: Int, summary: String? = null, onClick: (() -> Unit)? = null): ItemSettingRowBinding {
        val r = ItemSettingRowBinding.inflate(layoutInflater, b.settingsRoot, false)
        r.tvSettingTitle.setText(title)
        r.tvSettingSummary.show(!summary.isNullOrBlank())
        r.tvSettingSummary.text = summary.orEmpty()
        if (onClick != null) r.root.setOnClickListener { onClick() } else { r.root.isClickable = false; r.root.background = null }
        b.settingsRoot.addView(r.root)
        return r
    }

    private fun switchRow(title: Int, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        val r = row(title, summary)
        r.swSetting.show(true)
        r.swSetting.isChecked = checked
        r.swSetting.setOnCheckedChangeListener { _, value -> onChange(value) }
        r.root.setOnClickListener { r.swSetting.toggle() }
        r.root.isClickable = true
    }

    private fun render(s: Snapshot) {
        b.settingsRoot.removeAllViews()
        val ctx = requireContext()

        header(R.string.settings_personalization)
        row(R.string.settings_name, s.name.ifBlank { getString(R.string.not_set) }) { editName(s.name) }
        switchRow(R.string.settings_dark_mode, getString(R.string.settings_dark_mode_summary), s.dark) { dark ->
            host.background(work = { ServiceLocator.settings.darkMode = dark }) {
                AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        header(R.string.settings_security)
        row(R.string.settings_change_pin, getString(R.string.settings_change_pin_summary)) { changePin() }
        row(R.string.settings_change_recovery, getString(if (s.hasRecovery) R.string.recovery_is_set else R.string.recovery_not_set)) { changeRecovery() }
        row(R.string.settings_security_status, getString(if (s.encrypted) R.string.security_status_encrypted else R.string.security_status_fallback))

        header(R.string.settings_reminders)
        switchRow(R.string.settings_notif_titles, getString(R.string.settings_notif_titles_summary), s.showTitles) { show ->
            host.background(work = { ServiceLocator.settings.showTitlesInNotifications = show }) {}
        }
        row(R.string.settings_notification_settings, getString(R.string.settings_notification_settings_summary)) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            )
        }

        header(R.string.settings_content)
        row(R.string.settings_manage_tags) { TagManager.show(host) }
        row(R.string.settings_stickers) { startActivity(StickerPickerActivity.manageIntent(ctx)) }
        row(R.string.settings_recently_deleted, getString(R.string.settings_recently_deleted_summary)) {
            startActivity(Intent(ctx, RecentlyDeletedActivity::class.java))
        }

        header(R.string.settings_about)
        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "" }
        row(R.string.app_name, getString(R.string.settings_version, version))
        row(R.string.settings_privacy, getString(R.string.settings_privacy_summary))
    }

    // ------------------------------------------------------------------ actions

    private fun editName(current: String) {
        InputDialog.show(host, getString(R.string.settings_name), current, getString(R.string.hint_name), 30) { text ->
            val name = TextUtils.cleanName(text, 30)
            if (name.isEmpty()) return@show getString(R.string.error_name_required)
            host.background(work = { ServiceLocator.settings.userName = name }) { rebuild() }
            null
        }
    }

    private fun changePin() {
        PinGate.request(host, getString(R.string.settings_enter_current_pin)) {
            PinPrompt(host, getString(R.string.new_pin), null, onPin = { first, p1 ->
                p1.dismiss()
                PinPrompt(host, getString(R.string.recovery_confirm_pin), null, onPin = { second, p2 ->
                    if (second != first) {
                        p2.error(getString(R.string.onb_pins_mismatch))
                    } else {
                        p2.dismiss()
                        host.background(work = { ServiceLocator.auth.setPin(first) }) { host.toast(R.string.pin_changed) }
                    }
                }).show()
            }).show()
        }
    }

    private fun changeRecovery() {
        PinGate.request(host, getString(R.string.settings_enter_current_pin)) {
            val form = LayoutRecoveryFormBinding.inflate(layoutInflater)
            val controller = RecoveryFormController(form, host)
            val dialog = MaterialAlertDialogBuilder(host)
                .setTitle(R.string.settings_change_recovery)
                .setView(form.root)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val qa = controller.read() ?: return@setOnClickListener
                    host.background(work = { ServiceLocator.auth.setRecovery(qa.first, qa.second) }) {
                        dialog.dismiss()
                        host.toast(R.string.recovery_changed)
                        rebuild()
                    }
                }
            }
            dialog.show()
        }
    }
}
