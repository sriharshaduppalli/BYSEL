package com.bysel.trader.ui.components

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bysel.trader.ui.theme.LocalAppTheme

enum class LegalDocument(val title: String, val assetFile: String) {
    Privacy("Privacy Policy", "legal/privacy.html"),
    Terms("Terms of Service", "legal/terms.html"),
    Licenses("Open Source Licenses", "legal/licenses.html"),
}

/**
 * Large dialog that renders a legal HTML document shipped in app assets.
 * Avoids AlertDialog + WebView sizing bugs (blank content) and works offline
 * even when marketing-site legal URLs are not deployed.
 *
 * Security: JS off; navigation limited to bundled `file:///android_asset/` pages.
 */
@Composable
fun LegalDocumentDialog(
    document: LegalDocument,
    onDismiss: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(16.dp),
            color = theme.card,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = document.title,
                        color = theme.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = theme.primary)
                    }
                }
                HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.2f))
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val url = request?.url?.toString().orEmpty()
                                    return !url.startsWith("file:///android_asset/")
                                }
                            }
                            settings.javaScriptEnabled = false
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = true
                            settings.allowContentAccess = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            loadUrl("file:///android_asset/${document.assetFile}")
                        }
                    },
                    update = { webView ->
                        val target = "file:///android_asset/${document.assetFile}"
                        if (webView.url != target) {
                            webView.loadUrl(target)
                        }
                    },
                )
            }
        }
    }
}
