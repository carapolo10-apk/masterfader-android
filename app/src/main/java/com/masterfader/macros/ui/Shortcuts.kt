package com.masterfader.macros.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.masterfader.macros.MacroApp
import com.masterfader.macros.R
import com.masterfader.macros.automation.MasterFaderService
import com.masterfader.macros.core.Macro

/** Home-screen shortcuts: one icon per macro, so a macro really is one tap from the launcher. */
object Shortcuts {
    const val EXTRA_MACRO_ID = "macro_id"

    fun pin(context: Context, macro: Macro) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, "Your launcher doesn't support pinned shortcuts", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(context, RunMacroActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_MACRO_ID, macro.id)
        val shortcut = ShortcutInfoCompat.Builder(context, "macro-${macro.id}")
            .setShortLabel(macro.name.take(24))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}

/** Invisible activity a home-screen shortcut opens: starts the macro and finishes at once. */
class RunMacroActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(Shortcuts.EXTRA_MACRO_ID)
        val macro = id?.let { (application as MacroApp).repository.macro(it) }
        when {
            macro == null -> Toast.makeText(this, "That macro was deleted", Toast.LENGTH_LONG).show()
            !MasterFaderService.run(macro) -> {
                Toast.makeText(this, "Turn on Fader Macros in Accessibility settings", Toast.LENGTH_LONG).show()
                MasterFaderService.openAccessibilitySettings(this)
            }
        }
        finish()
    }
}
