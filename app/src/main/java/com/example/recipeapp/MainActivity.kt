package com.example.recipeapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        // 1. 修复：拦截网页的 alert 和 confirm 弹窗，让删除/清空时有系统弹窗
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result?.confirm() }
                    .setNegativeButton("取消") { _, _ -> result?.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }
        }

        // 2. 修复：挂载原生桥接对象，让分享和备份能复制到剪贴板
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        
        // 3. 修复：拦截手机返回键，子页面回主页，主页才退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){return (window.handleAndroidBack && window.handleAndroidBack()) ? '1' : '0';})()"
                ) { result ->
                    if (result != "\"1\"") {
                        finish()
                    }
                }
            }
        })

        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun copyToClipboard(text: String) {
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("菜谱数据", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@MainActivity, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun saveBackup(json: String, fileName: String): String {
            return try {
                val file = java.io.File(filesDir, fileName)
                file.writeText(json)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "备份已保存：${file.name}", Toast.LENGTH_LONG).show()
                }
                file.absolutePath
            } catch (e: Exception) {
                "ERROR:${e.message}"
            }
        }
    }
}
