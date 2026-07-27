package com.pmsuryaghar.docprocessor.data.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

object IntentManager {

    private const val GEMINI_PACKAGE = "com.google.android.apps.bard"
    private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: Exception) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                launchIntent != null
            } catch (ex: Exception) {
                false
            }
        }
    }

    /**
     * Launches the configured AI app (Gemini or ChatGPT) with document attachments and prompt text.
     */
    fun launchAiAgent(context: Context, agent: String, promptText: String, files: List<File>) {
        val targetPackage = if (agent.equals("ChatGPT", ignoreCase = true)) CHATGPT_PACKAGE else GEMINI_PACKAGE
        val appName = if (agent.equals("ChatGPT", ignoreCase = true)) "ChatGPT" else "Gemini"

        if (!isAppInstalled(context, targetPackage)) {
            Toast.makeText(context, "$appName app is not installed. Redirecting to Google Play Store...", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$targetPackage"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$targetPackage"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
            return
        }

        val fileUris = ArrayList<Uri>()
        for (file in files) {
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "com.pmsuryaghar.docprocessor.fileprovider",
                    file
                )
                fileUris.add(uri)
            }
        }

        // 1. Copy prompt text to clipboard as a fallback
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("PM Surya Ghar Prompt", promptText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Prompt copied to clipboard! Paste it in $appName.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy prompt to clipboard")
        }

        // 2. Build share intent with explicit ClipData permission grant for all attachment URIs
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
            putExtra(Intent.EXTRA_TEXT, promptText)
            `package` = targetPackage

            if (fileUris.isNotEmpty()) {
                val clipData = android.content.ClipData.newRawUri("Attachment Files", fileUris[0])
                for (i in 1 until fileUris.size) {
                    clipData.addItem(android.content.ClipData.Item(fileUris[i]))
                }
                setClipData(clipData)
            }

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error launching $appName intent directly")
            try {
                context.startActivity(Intent.createChooser(intent, "Open $appName with Documents").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (ex: Exception) {
                Toast.makeText(context, "Failed to open $appName: ${ex.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Backward compatible wrapper for launching Gemini directly.
     */
    fun launchGemini(context: Context, promptText: String, files: List<File>) {
        launchAiAgent(context, "Gemini", promptText, files)
    }

    /**
     * Opens WhatsApp (or WhatsApp Business) and attaches the generated ZIP archive.
     */
    fun shareZipToWhatsApp(context: Context, zipFile: File, customerName: String, destinationNumber: String) {
        val whatsAppPackage = when {
            isAppInstalled(context, WHATSAPP_PACKAGE) -> WHATSAPP_PACKAGE
            isAppInstalled(context, WHATSAPP_BUSINESS_PACKAGE) -> WHATSAPP_BUSINESS_PACKAGE
            else -> null
        }

        if (whatsAppPackage == null) {
            Toast.makeText(context, "WhatsApp is not installed.", Toast.LENGTH_LONG).show()
            return
        }

        val zipUri = FileProvider.getUriForFile(
            context,
            "com.pmsuryaghar.docprocessor.fileprovider",
            zipFile
        )

        val messageText = """
            PM Surya Ghar Application Documents

            Customer:
            $customerName

            ZIP attached.
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, zipUri)
            putExtra(Intent.EXTRA_TEXT, messageText)
            `package` = whatsAppPackage
            
            // Try to pre-fill the WhatsApp chat if target number is set
            if (destinationNumber.isNotEmpty()) {
                val cleanedNumber = destinationNumber.replace("+", "").replace(" ", "").trim()
                putExtra("jid", "$cleanedNumber@s.whatsapp.net")
            }
            
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error sharing ZIP to WhatsApp")
            Toast.makeText(context, "Failed to share via WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
