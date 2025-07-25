package com.mvpapps.uae_pass_flutter

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class UAEPassWebViewActivity : Activity() {
    
    private lateinit var webView: WebView
    private var authUrl: String? = null
    private var redirectUri: String? = null
    private var scheme: String? = null
    
    companion object {
        const val EXTRA_AUTH_URL = "auth_url"
        const val EXTRA_REDIRECT_URI = "redirect_uri"
        const val EXTRA_SCHEME = "scheme"
        const val RESULT_CODE_SUCCESS = "success_code"
        const val RESULT_CODE_ERROR = "error_message"
        const val RESULT_CODE_CANCELLED = "cancelled"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up action bar with back button only - no logo
        actionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayUseLogoEnabled(false)
            setDisplayShowCustomEnabled(false)
            setIcon(null)
            setLogo(null)
            title = "UAE PASS"
            setHomeButtonEnabled(true)
        }
        
        // Get data from intent
        authUrl = intent.getStringExtra(EXTRA_AUTH_URL)
        redirectUri = intent.getStringExtra(EXTRA_REDIRECT_URI)
        scheme = intent.getStringExtra(EXTRA_SCHEME)
        // Set up WebView
        setupWebView()
        
        // Load the authentication URL
        authUrl?.let { webView.loadUrl(it) }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Handle back button press
                setResult(RESULT_CANCELED, Intent().apply {
                    putExtra(RESULT_CODE_CANCELLED, "Authentication Process Canceled By User")
                })
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            // Same as toolbar back button
            setResult(RESULT_CANCELED, Intent().apply {
                putExtra(RESULT_CODE_CANCELLED, "Authentication Process Canceled By User")
            })
            super.onBackPressed()
        }
    }
    
    private fun setupWebView() {
        webView = WebView(this)
        setContentView(webView)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowContentAccess = true
            allowFileAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { checkUrlForRedirect(it) }
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.let { 
                    if (checkUrlForRedirect(it)) {
                        return true
                    }
                }
                return false
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { checkUrlForRedirect(it) }
            }
        }
    }
    
    private fun checkUrlForRedirect(url: String): Boolean {
        // Check for success redirect
        if (url.contains(redirectUri ?: "") && url.contains("code=")) {
            val uri = Uri.parse(url)
            val code = uri.getQueryParameter("code")
            if (!code.isNullOrEmpty()) {
                setResult(RESULT_OK, Intent().apply {
                    putExtra(RESULT_CODE_SUCCESS, code)
                })
                finish()
                return true
            }
        }
        
        // Check for error or cancellation
        if (url.contains("error=access_denied") || url.contains("error=cancelled")) {
            setResult(RESULT_CANCELED, Intent().apply {
                putExtra(RESULT_CODE_CANCELLED, "Authentication Process Canceled By User")
            })
            finish()
            return true
        }
        
        // Determine the correct scheme to use
        val targetScheme = scheme?.takeIf { it.isNotBlank() } ?: "uaepass"
        
        //the url is returned as uaepassstg even for the staging environment
        // so we need to fix it to uaepassstg if the target scheme is uaepassstg
        // or uaepass if the target scheme is uaepass
        val fixedUrl = when {
            url.startsWith("uaepass://") && targetScheme == "uaepassstg" -> {
                url.replace("uaepass://", "uaepassstg://")
            }
            url.startsWith("uaepassstg://") && targetScheme == "uaepass" -> {
                url.replace("uaepassstg://", "uaepass://")
            }
            else -> url
        }
        
        if (fixedUrl.startsWith("uaepass://") || fixedUrl.startsWith("uaepassstg://")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fixedUrl)).apply {
                    // Add FLAG_ACTIVITY_NEW_TASK to avoid Activity not found exception
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Try the other scheme if the first attempt fails
                val fallbackUrl = if (fixedUrl.startsWith("uaepass://")) {
                    fixedUrl.replace("uaepass://", "uaepassstg://")
                } else {
                    fixedUrl.replace("uaepassstg://", "uaepass://")
                }
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "UAE Pass app not found", Toast.LENGTH_SHORT).show()
                }
            }
            return true
        }
        
        return false
    }
}