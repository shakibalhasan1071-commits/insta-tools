package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebStorage
import android.webkit.WebView
import com.example.data.model.AutomationStep
import com.example.data.model.RecordedDomAction
import com.example.data.model.StepActionType
import com.example.data.model.VariableType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BrowserAutomationEngine(
    private val context: Context,
    private val onRecordedAction: (RecordedDomAction) -> Unit,
    private val onSecurityCheckpointDetected: (String) -> Unit
) {
    companion object {
        const val DEFAULT_INSTAGRAM_URL = "https://www.instagram.com/?theme=dark"
    }
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val jsClickHelpers = """
        function showVisualClickIndicator(x, y) {
            try {
                var marker = document.createElement('div');
                marker.style.position = 'fixed';
                marker.style.left = (x - 22) + 'px';
                marker.style.top = (y - 22) + 'px';
                marker.style.width = '44px';
                marker.style.height = '44px';
                marker.style.borderRadius = '50%';
                marker.style.border = '4px solid #00E5FF';
                marker.style.backgroundColor = 'rgba(0, 229, 255, 0.45)';
                marker.style.boxShadow = '0 0 25px #00E5FF';
                marker.style.pointerEvents = 'none';
                marker.style.zIndex = '9999999';
                marker.style.transition = 'transform 0.8s ease-out, opacity 0.8s ease-out';
                marker.style.transform = 'scale(0.3)';
                marker.style.opacity = '1';
                
                document.body.appendChild(marker);
                marker.offsetHeight;
                marker.style.transform = 'scale(2.5)';
                marker.style.opacity = '0';
                setTimeout(function() {
                    try { document.body.removeChild(marker); } catch(e){}
                }, 1000);
            } catch(e){}
        }

        function triggerReactHandler(el) {
            if (!el) return false;
            try {
                for (var k in el) {
                    if (k.startsWith('__reactProps') || k.startsWith('__reactEventHandlers') || k.startsWith('__reactFiber')) {
                        var p = el[k];
                        if (p) {
                            if (typeof p.onClick === 'function') { p.onClick({ preventDefault: function(){}, stopPropagation: function(){} }); return true; }
                            if (typeof p.onPress === 'function') { p.onPress({ preventDefault: function(){}, stopPropagation: function(){} }); return true; }
                            if (p.memoizedProps) {
                                if (typeof p.memoizedProps.onClick === 'function') { p.memoizedProps.onClick({ preventDefault: function(){}, stopPropagation: function(){} }); return true; }
                                if (typeof p.memoizedProps.onPress === 'function') { p.memoizedProps.onPress({ preventDefault: function(){}, stopPropagation: function(){} }); return true; }
                            }
                        }
                    }
                }
            } catch(e) {}
            return false;
        }

        function simulateTapSync(el) {
            if (!el) return;
            try {
                try { el.scrollIntoView({ behavior: 'instant', block: 'center' }); } catch(e){ el.scrollIntoView(); }
                try { el.focus(); } catch(e){}

                var rect = el.getBoundingClientRect();
                var x = rect.left + rect.width / 2 + (window.__clickXOffset || 0);
                var y = rect.top + rect.height / 2 + (window.__clickYOffset || 0);

                showVisualClickIndicator(x, y);

                var originalOutline = el.style.outline;
                el.style.outline = "4px solid #00E5FF";
                el.style.outlineOffset = "2px";
                setTimeout(function() {
                    try { el.style.outline = originalOutline; } catch(e){}
                }, 2000);
                
                var opts = { bubbles: true, cancelable: true, clientX: x, clientY: y, view: window };
                try { el.dispatchEvent(new PointerEvent('pointerdown', opts)); } catch(e){}
                
                try {
                    if (typeof Touch === 'function') {
                        var touchObj = new Touch({
                            identifier: Date.now(),
                            target: el,
                            clientX: x,
                            clientY: y,
                            screenX: x,
                            screenY: y,
                            pageX: x,
                            pageY: y
                        });
                        var tStart = new TouchEvent('touchstart', {
                            bubbles: true,
                            cancelable: true,
                            view: window,
                            touches: [touchObj],
                            targetTouches: [touchObj],
                            changedTouches: [touchObj]
                        });
                        el.dispatchEvent(tStart);
                    }
                } catch(e){}

                try { el.dispatchEvent(new MouseEvent('mousedown', opts)); } catch(e){}
                try { el.dispatchEvent(new PointerEvent('pointerup', opts)); } catch(e){}
                try { el.dispatchEvent(new MouseEvent('mouseup', opts)); } catch(e){}

                try {
                    if (typeof Touch === 'function') {
                        var touchObjEnd = new Touch({
                            identifier: Date.now(),
                            target: el,
                            clientX: x,
                            clientY: y,
                            screenX: x,
                            screenY: y,
                            pageX: x,
                            pageY: y
                        });
                        var tEnd = new TouchEvent('touchend', {
                            bubbles: true,
                            cancelable: true,
                            view: window,
                            touches: [],
                            targetTouches: [],
                            changedTouches: [touchObjEnd]
                        });
                        el.dispatchEvent(tEnd);
                    }
                } catch(e){}

                try { el.dispatchEvent(new MouseEvent('click', opts)); } catch(e){}
                try { el.click(); } catch(e){}
                
                triggerReactHandler(el);

                var topEl = document.elementFromPoint(x, y);
                if (topEl && topEl !== el) {
                    var elTxt = (el.innerText || el.textContent || "").toLowerCase();
                    var topTxt = (topEl.innerText || topEl.textContent || "").toLowerCase();
                    if (!elTxt.includes("cancel") && (topTxt.includes("cancel") || topTxt.includes("back") || topTxt.includes("close") || topTxt.includes("বাতিল"))) {
                        // Protect against misdirected topEl clicking Cancel
                    } else {
                        try { topEl.click(); } catch(e){}
                        triggerReactHandler(topEl);
                    }
                }

                try {
                    if (window.IGAutomationBridge && window.IGAutomationBridge.triggerNativeAndroidTouch) {
                        window.IGAutomationBridge.triggerNativeAndroidTouch(x, y);
                    }
                } catch(e) {}
            } catch(e) {}
        }
        
        function simulateTap(el) {
            simulateTapSync(el);
        }
    """.trimIndent()

    var isRecording: Boolean = false
    var onUsernameAdvanceDetected: ((String) -> Unit)? = null
    var onNameAdvanceDetected: ((String) -> Unit)? = null
    var onAccountDetailsFilled: ((String, String) -> Unit)? = null
    var capturedUsername: String = ""
    private var autoWatcherRunnable: Runnable? = null
    var pendingSessionToRestore: String? = null

    fun attachWebView(view: WebView) {
        this.webView = view
        setupWebViewBridge(view)
        startContinuousWatcher()
    }

    private fun startContinuousWatcher() {
        stopContinuousWatcher()
        autoWatcherRunnable = object : Runnable {
            override fun run() {
                if (webView != null) {
                    injectAutoAccountCreatorWatcher()
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }
        mainHandler.postDelayed(autoWatcherRunnable!!, 400L)
    }

    private fun stopContinuousWatcher() {
        autoWatcherRunnable?.let { mainHandler.removeCallbacks(it) }
        autoWatcherRunnable = null
    }

    fun stopAutomation() {
        stopContinuousWatcher()
        evaluateJs("window.__muse_automation_enabled = false; window.__ig_automation_enabled = false;")
    }

    // Persistent storage for tab-specific cookies
    private val tabCookieMap = mutableMapOf<String, String>()

    fun saveTabCookies(tabId: String, url: String) {
        val cookies = CookieManager.getInstance().getCookie(url) ?: ""
        tabCookieMap[tabId] = cookies
    }

    fun restoreTabCookies(tabId: String, url: String) {
        val cookies = tabCookieMap[tabId]
        if (!cookies.isNullOrBlank()) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setCookie(url, cookies)
        }
    }

    fun clearTabCookies(tabId: String) {
        tabCookieMap.remove(tabId)
    }

    fun detachWebView() {
        stopContinuousWatcher()
        this.webView = null
    }

    @SuppressLint("JavascriptInterface")
    private fun setupWebViewBridge(view: WebView) {
        view.addJavascriptInterface(AutomationJsBridge(), "IGAutomationBridge")
        view.addJavascriptInterface(Android2FANativeBridge(), "Android2FANative")
    }

    inner class Android2FANativeBridge {
        @JavascriptInterface
        fun getFloatingData(): String {
            return context.getSharedPreferences("Android2FANativePrefs", Context.MODE_PRIVATE)
                .getString("floating_data", "") ?: ""
        }

        @JavascriptInterface
        fun saveFloatingData(data: String) {
            context.getSharedPreferences("Android2FANativePrefs", Context.MODE_PRIVATE)
                .edit().putString("floating_data", data).apply()
        }

        @JavascriptInterface
        fun clearBrowsingData() {
            mainHandler.post {
                clearBrowserSessionData {}
            }
        }

        @JavascriptInterface
        fun downloadCsv(base64: String, fileName: String): Boolean {
            return try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = java.io.File(downloadsDir, fileName)
                java.io.FileOutputStream(file).use { it.write(bytes) }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    inner class AutomationJsBridge {
        @JavascriptInterface
        fun onElementClicked(selector: String, tag: String, text: String, x: Float, y: Float) {
            if (!isRecording) return
            mainHandler.post {
                val cleanDesc = when {
                    text.isNotBlank() && text.length < 30 -> "$tag \"${text.trim()}\""
                    selector.contains("username") -> "Username Field"
                    selector.contains("password") -> "Password Field"
                    selector.contains("submit") -> "Submit Button"
                    else -> "$tag ($selector)"
                }
                onRecordedAction(
                    RecordedDomAction(
                        type = StepActionType.CLICK,
                        selector = selector,
                        description = "Click $cleanDesc",
                        delayMs = 1000L
                    )
                )
            }
        }

        @JavascriptInterface
        fun onCheckpointDetected(reason: String) {
            mainHandler.post {
                onSecurityCheckpointDetected(reason)
            }
        }

        @JavascriptInterface
        fun onUserAdvancedUsername() {
            mainHandler.post {
                onUsernameAdvanceDetected?.invoke("")
            }
        }

        @JavascriptInterface
        fun onUserAdvancedUsernameWithValue(enteredUsername: String) {
            mainHandler.post {
                onUsernameAdvanceDetected?.invoke(enteredUsername)
            }
        }

        @JavascriptInterface
        fun onUserAdvancedName() {
            mainHandler.post {
                onNameAdvanceDetected?.invoke("")
            }
        }

        @JavascriptInterface
        fun onUserAdvancedNameWithValue(enteredName: String) {
            mainHandler.post {
                onNameAdvanceDetected?.invoke(enteredName)
            }
        }

        @JavascriptInterface
        fun triggerNativeAndroidTouch(clientX: Float, clientY: Float) {
            mainHandler.post {
                val targetWebView = webView ?: return@post
                val density = targetWebView.resources.displayMetrics.density
                val xPx = clientX * density
                val yPx = clientY * density
                val w = targetWebView.width
                val h = targetWebView.height
                if (w <= 0 || h <= 0) return@post

                val clampX = xPx.coerceIn(10f, w - 10f)
                val clampY = yPx.coerceIn(10f, h - 10f)

                val downTime = android.os.SystemClock.uptimeMillis()
                val eventDown = android.view.MotionEvent.obtain(
                    downTime, downTime,
                    android.view.MotionEvent.ACTION_DOWN,
                    clampX, clampY, 0
                )
                val eventUp = android.view.MotionEvent.obtain(
                    downTime, downTime + 80,
                    android.view.MotionEvent.ACTION_UP,
                    clampX, clampY, 0
                )

                targetWebView.dispatchTouchEvent(eventDown)
                targetWebView.dispatchTouchEvent(eventUp)

                eventDown.recycle()
                eventUp.recycle()
            }
        }

        @JavascriptInterface
        fun onAutoAccountDetailsFilled(firstName: String, lastName: String) {
            mainHandler.post {
                onAccountDetailsFilled?.invoke(firstName, lastName)
            }
        }
    }

    fun injectRecorderScript() {
        val script = """
            (function() {
                if (window.__ig_recorder_installed) return;
                window.__ig_recorder_installed = true;

                function getBestSelector(el) {
                    if (!el || el === document.body) return 'body';
                    if (el.id) return '#' + el.id;
                    if (el.name) return el.tagName.toLowerCase() + "[name='" + el.name + "']";
                    if (el.getAttribute('data-testid')) return "[data-testid='" + el.getAttribute('data-testid') + "']";
                    if (el.getAttribute('aria-label')) return el.tagName.toLowerCase() + "[aria-label='" + el.getAttribute('aria-label') + "']";
                    if (el.getAttribute('placeholder')) return el.tagName.toLowerCase() + "[placeholder='" + el.getAttribute('placeholder') + "']";
                    if (el.type) return el.tagName.toLowerCase() + "[type='" + el.type + "']";
                    if (el.className && typeof el.className === 'string') {
                        var firstClass = el.className.trim().split(/\s+/)[0];
                        if (firstClass && firstClass.length > 2) return el.tagName.toLowerCase() + '.' + firstClass;
                    }
                    return el.tagName.toLowerCase();
                }

                document.addEventListener('click', function(e) {
                    if (!window.__ig_recording_active) return;
                    var target = e.target;
                    var selector = getBestSelector(target);
                    var text = target.innerText || target.value || target.getAttribute('aria-label') || '';
                    var rect = target.getBoundingClientRect();
                    
                    // Visual touch indicator
                    var overlay = document.createElement('div');
                    overlay.style.position = 'fixed';
                    overlay.style.left = rect.left + 'px';
                    overlay.style.top = rect.top + 'px';
                    overlay.style.width = rect.width + 'px';
                    overlay.style.height = rect.height + 'px';
                    overlay.style.border = '2px solid #38BDF8';
                    overlay.style.backgroundColor = 'rgba(56, 189, 248, 0.25)';
                    overlay.style.pointerEvents = 'none';
                    overlay.style.zIndex = '999999';
                    overlay.style.borderRadius = '4px';
                    overlay.style.transition = 'opacity 0.6s ease';
                    document.body.appendChild(overlay);
                    setTimeout(function() {
                        overlay.style.opacity = '0';
                        setTimeout(function() { overlay.remove(); }, 600);
                    }, 400);

                    if (window.IGAutomationBridge) {
                        window.IGAutomationBridge.onElementClicked(selector, target.tagName, text.substring(0, 30), e.clientX, e.clientY);
                    }
                }, true);
            })();
        """.trimIndent()

        evaluateJs(script)
    }

    fun injectAutoAccountCreatorWatcher() {
        val script = """
            (function() {
                var firstNames = [
                    "Liam", "Noah", "Oliver", "James", "Elijah", "William", "Henry", "Lucas", "Benjamin", "Theodore",
                    "Mateo", "Levi", "Sebastian", "Daniel", "Jack", "Michael", "Alexander", "Owen", "Asher", "Samuel",
                    "Ethan", "John", "David", "Joseph", "Luke", "Julian", "Leo", "Dylan", "Anthony", "Isaac",
                    "Emma", "Olivia", "Ava", "Sophia", "Isabella", "Mia", "Amelia", "Harper", "Evelyn", "Abigail",
                    "Emily", "Ella", "Elizabeth", "Camila", "Luna", "Sofia", "Avery", "Mila", "Aria", "Scarlett",
                    "Penelope", "Layla", "Chloe", "Victoria", "Madison", "Eleanor", "Grace", "Nora", "Riley", "Zoey",
                    "Hannah", "Lily", "Zoe", "Stella", "Hazel", "Aurora", "Natalie", "Emilia", "Violet", "Maya",
                    "Elena", "Adam", "Nathan", "Aaron", "Caleb", "Ryan", "Adrian", "Christian", "Colton", "Jordan"
                ];

                var lastNames = [
                    "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
                    "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin",
                    "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson",
                    "Walker", "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores",
                    "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell", "Carter", "Roberts",
                    "Gomez", "Phillips", "Evans", "Turner", "Diaz", "Parker", "Cruz", "Edwards", "Collins", "Reyes"
                ];

                function getUniqueRandomName() {
                    var lastUsed = "";
                    try { lastUsed = window.sessionStorage.getItem("__last_muse_name") || ""; } catch(e){}
                    var f = firstNames[Math.floor(Math.random() * firstNames.length)];
                    var l = lastNames[Math.floor(Math.random() * lastNames.length)];
                    var candidate = f + " " + l;
                    if (candidate === lastUsed) {
                        f = firstNames[(firstNames.indexOf(f) + 1) % firstNames.length];
                        l = lastNames[(lastNames.indexOf(l) + 1) % lastNames.length];
                    }
                    try { window.sessionStorage.setItem("__last_muse_name", f + " " + l); } catch(e){}
                    return { first: f, last: l };
                }

                function setInputValue(el, value) {
                    if (!el) return;
                    try {
                        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        el.focus();
                        el.click();
                        
                        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
                        if (nativeSetter && nativeSetter.set) {
                            nativeSetter.set.call(el, value);
                        } else {
                            el.value = value;
                        }
                        
                        el.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                        el.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: value.slice(-1) }));
                        el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: value.slice(-1) }));
                        el.dispatchEvent(new Event('blur', { bubbles: true }));
                    } catch(e) {
                        el.value = value;
                        try { el.dispatchEvent(new Event('input', { bubbles: true })); } catch(e2){}
                    }
                }

                function setBirthdayValues() {
                    var adultYears = ["1994", "1995", "1996", "1997", "1998", "1999", "2000", "2001", "2002"];
                    var randomYear = adultYears[Math.floor(Math.random() * adultYears.length)];
                    var randomMonth = String(Math.floor(Math.random() * 12) + 1);
                    var randomDay = String(Math.floor(Math.random() * 28) + 1);

                    // 1. Native select elements
                    var selects = Array.from(document.querySelectorAll("select"));
                    selects.forEach(function(sel) {
                        var a = (sel.getAttribute('aria-label') || "").toLowerCase();
                        var n = (sel.name || "").toLowerCase();
                        var t = (sel.title || "").toLowerCase();
                        var setter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, "value");

                        // Year
                        if (a.includes("year") || n.includes("year") || t.includes("year") || Array.from(sel.options).some(function(o) { return o.value === "2026" || o.value === "2025"; })) {
                            var yOpt = Array.from(sel.options).find(function(o) { return o.value === randomYear || o.text === randomYear; });
                            if (yOpt) {
                                if (setter && setter.set) setter.set.call(sel, yOpt.value);
                                sel.value = yOpt.value;
                                sel.dispatchEvent(new Event('change', { bubbles: true }));
                                sel.dispatchEvent(new Event('input', { bubbles: true }));
                            }
                        }
                        // Month
                        else if (a.includes("month") || n.includes("month") || t.includes("month") || Array.from(sel.options).some(function(o) { return o.text.toLowerCase().includes("jan") || o.text.toLowerCase().includes("oct"); })) {
                            var mOpt = Array.from(sel.options).find(function(o) { return o.value === randomMonth || o.value === "10" || o.value === "5"; });
                            if (mOpt) {
                                if (setter && setter.set) setter.set.call(sel, mOpt.value);
                                sel.value = mOpt.value;
                                sel.dispatchEvent(new Event('change', { bubbles: true }));
                                sel.dispatchEvent(new Event('input', { bubbles: true }));
                            }
                        }
                        // Day
                        else if (a.includes("day") || n.includes("day") || t.includes("day") || Array.from(sel.options).some(function(o) { return o.value === "4" || o.value === "1"; })) {
                            var dOpt = Array.from(sel.options).find(function(o) { return o.value === randomDay || o.value === "15"; });
                            if (dOpt) {
                                if (setter && setter.set) setter.set.call(sel, dOpt.value);
                                sel.value = dOpt.value;
                                sel.dispatchEvent(new Event('change', { bubbles: true }));
                                sel.dispatchEvent(new Event('input', { bubbles: true }));
                            }
                        }
                    });

                    // 2. Custom Dropdowns / Comboboxes
                    var clickables = Array.from(document.querySelectorAll("button, [role='button'], [role='combobox'], div, span"));
                    var yearDisplay = clickables.find(function(el) {
                        var txt = (el.innerText || "").trim();
                        return txt === "2026" || txt === "2025" || txt === "2024";
                    });
                    if (yearDisplay) {
                        yearDisplay.click();
                        setTimeout(function() {
                            var opts = Array.from(document.querySelectorAll("[role='option'], li, div, span, button"));
                            var targetOpt = opts.find(function(o) {
                                var txt = (o.innerText || "").trim();
                                return txt === randomYear || txt === "1998" || txt === "1997" || txt === "1995";
                            });
                            if (targetOpt) {
                                targetOpt.click();
                            }
                        }, 300);
                    }
                }

                function clickConfirmButton() {
                    // 1. Query real interactive buttons first
                    var realButtons = Array.from(document.querySelectorAll("button, [role='button'], input[type='submit'], form button, a[role='button'], button[type='submit']"));
                    
                    var targetBtn = realButtons.find(function(b) {
                        var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                        return txt === "confirm" || txt === "continue" || txt === "done" || txt === "next" || txt === "submit" || txt === "নিশ্চিত করুন" ||
                               txt.startsWith("confirm") || txt.startsWith("continue") ||
                               (txt.length < 25 && (txt.includes("confirm") || txt.includes("continue")));
                    });

                    // 2. Fallback to leaf text elements (spans with no children) inside buttons/forms
                    if (!targetBtn) {
                        var subElements = Array.from(document.querySelectorAll("span, div, p")).filter(function(el) {
                            var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                            return el.children.length === 0 && (txt === "confirm" || txt === "continue" || txt === "done" || txt === "নিশ্চিত করুন");
                        });
                        if (subElements.length > 0) {
                            targetBtn = subElements[0].closest("button, [role='button'], a, input[type='submit']") || subElements[0];
                        }
                    }

                    // 3. Fallback to blue submit button on form
                    if (!targetBtn && realButtons.length > 0) {
                        targetBtn = realButtons[0];
                    }

                    if (targetBtn) {
                        try { targetBtn.scrollIntoView({ behavior: 'instant', block: 'center', inline: 'center' }); } catch(e){}
                        try { targetBtn.focus(); } catch(e){}

                        var rect = targetBtn.getBoundingClientRect();
                        var cx = Math.round(rect.left + rect.width / 2);
                        var cy = Math.round(rect.top + rect.height / 2);

                        // A) Geometric element at point
                        var pointEl = document.elementFromPoint(cx, cy) || targetBtn;
                        var targets = [targetBtn, pointEl, targetBtn.closest("button, [role='button'], form"), pointEl.closest("button, [role='button'], form")].filter(Boolean);

                        targets.forEach(function(target) {
                            try {
                                var opts = {
                                    bubbles: true, cancelable: true, view: window,
                                    clientX: cx, clientY: cy, screenX: cx, screenY: cy,
                                    button: 0, buttons: 1, isPrimary: true, pointerId: 1, pointerType: 'touch'
                                };
                                target.dispatchEvent(new PointerEvent('pointerdown', opts));
                                target.dispatchEvent(new MouseEvent('mousedown', opts));
                                target.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, cancelable: true, touches: [{ clientX: cx, clientY: cy, identifier: 1, target: target }] }));
                                target.dispatchEvent(new PointerEvent('pointerup', opts));
                                target.dispatchEvent(new MouseEvent('mouseup', opts));
                                target.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true, touches: [] }));
                                target.dispatchEvent(new MouseEvent('click', opts));
                                try { target.click(); } catch(e){}
                            } catch(e) {}
                        });

                        // B) Direct Form Submit
                        var form = targetBtn.closest("form") || (document.forms.length > 0 ? document.forms[0] : null);
                        if (form) {
                            try { form.requestSubmit(); } catch(e) {
                                try { form.submit(); } catch(e2) {}
                            }
                        }

                        // C) Trigger Hardware Android Touch at (cx, cy)
                        try {
                            if (window.IGAutomationBridge && window.IGAutomationBridge.triggerNativeAndroidTouch) {
                                window.IGAutomationBridge.triggerNativeAndroidTouch(cx, cy);
                            }
                        } catch(e) {}

                        return true;
                    }
                    return false;
                }

                function processFinishAccountPage() {
                    var bodyText = (document.body ? document.body.innerText : "") || "";
                    var bodyLower = bodyText.toLowerCase();

                    var isAccountPage = 
                        bodyLower.includes("finish creating your account") ||
                        (bodyLower.includes("first name") && bodyLower.includes("last name") && (bodyLower.includes("birthday") || bodyLower.includes("month") || bodyLower.includes("year"))) ||
                        (bodyLower.includes("provide my birthday") && (bodyLower.includes("confirm") || bodyLower.includes("continue")));

                    if (!isAccountPage) return;

                    var inputs = Array.from(document.querySelectorAll("input:not([type='hidden']):not([type='submit'])")).filter(function(i) {
                        var style = window.getComputedStyle(i);
                        return style.display !== 'none' && style.visibility !== 'hidden' && i.offsetWidth > 0;
                    });

                    var firstNameInput = inputs.find(function(i) {
                        var p = (i.placeholder || "").toLowerCase();
                        var a = (i.getAttribute('aria-label') || "").toLowerCase();
                        var n = (i.name || "").toLowerCase();
                        return p.includes("first") || a.includes("first") || n.includes("first") || n.includes("fname");
                    });

                    var lastNameInput = inputs.find(function(i) {
                        var p = (i.placeholder || "").toLowerCase();
                        var a = (i.getAttribute('aria-label') || "").toLowerCase();
                        var n = (i.name || "").toLowerCase();
                        return p.includes("last") || a.includes("last") || n.includes("last") || n.includes("lname");
                    });

                    if ((!firstNameInput || !lastNameInput) && inputs.length >= 2) {
                        firstNameInput = inputs[0];
                        lastNameInput = inputs[1];
                    }

                    if (firstNameInput && lastNameInput) {
                        var hasFirstName = (firstNameInput.value || "").trim().length > 0;
                        var hasLastName = (lastNameInput.value || "").trim().length > 0;

                        if (hasFirstName && hasLastName) {
                            // Already filled: Ensure birthday values are set and click Confirm immediately!
                            setBirthdayValues();
                            setTimeout(clickConfirmButton, 300);
                            setTimeout(clickConfirmButton, 800);
                            setTimeout(clickConfirmButton, 1600);
                            return;
                        }

                        // Fill First Name and Last Name with unique realistic name
                        var name = getUniqueRandomName();

                        setInputValue(firstNameInput, name.first);
                        firstNameInput.dataset.autoFilled = "done";

                        setTimeout(function() {
                            setInputValue(lastNameInput, name.last);
                            lastNameInput.dataset.autoFilled = "done";
                        }, 500);

                        setTimeout(function() {
                            setBirthdayValues();
                        }, 1100);

                        // Trigger Confirm with realistic human pause
                        setTimeout(function() {
                            clickConfirmButton();
                            try {
                                if (window.IGAutomationBridge && window.IGAutomationBridge.onAutoAccountDetailsFilled) {
                                    window.IGAutomationBridge.onAutoAccountDetailsFilled(name.first, name.last);
                                }
                            } catch(e) {}
                        }, 2200);

                        setTimeout(function() {
                            clickConfirmButton();
                        }, 3600);
                    }
                }

                function simulateFullClick(el) {
                    if (!el) return false;
                    
                    var target = el.closest("button, [role='button'], a, li, div[tabindex]") || el;
                    
                    var rect = target.getBoundingClientRect();
                    var cx = rect.left + rect.width / 2;
                    var cy = rect.top + rect.height / 2;

                    try { target.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch(e){}
                    try { target.focus(); } catch(e){}

                    try { target.dispatchEvent(new MouseEvent('mouseover', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, view: window })); } catch(e){}
                    try { target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, view: window, buttons: 1 })); } catch(e){}
                    try { target.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, isPrimary: true, button: 0, buttons: 1, pointerType: 'touch' })); } catch(e){}
                    try { target.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, isPrimary: true, button: 0, pointerType: 'touch' })); } catch(e){}
                    try { target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, view: window })); } catch(e){}
                    try { target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, clientX: cx, clientY: cy, view: window })); } catch(e){}
                    try { target.click(); } catch(e){}

                    return true;
                }

                var waitlistDetectedTime = 0;
                var lastActionTime = 0;

                function processWaitlistSettingsFlow() {
                    // Step 4 now directly loads https://auth.meta.com/forgot_password/ in ViewModel
                    return;
                }

                window.__muse_automation_enabled = true;

                if (!window.__muse_account_watcher_initialized) {
                    window.__muse_account_watcher_initialized = true;
                    setInterval(function() {
                        if (window.__muse_automation_enabled === false) return;
                        processFinishAccountPage();
                        processWaitlistSettingsFlow();
                    }, 1000);

                    if (window.MutationObserver && document.body) {
                        var observer = new MutationObserver(function() {
                            if (window.__muse_automation_enabled === false) return;
                            processFinishAccountPage();
                            processWaitlistSettingsFlow();
                        });
                        observer.observe(document.body, { childList: true, subtree: true });
                    }
                }

                if (window.__muse_automation_enabled !== false) {
                    processFinishAccountPage();
                    processWaitlistSettingsFlow();
                }
            })();
        """.trimIndent()

        evaluateJs(script)
    }

    fun injectMuseEmailInput(email: String, onComplete: (Boolean) -> Unit = {}) {
        val cleanEmail = email.trim()
        val script = """
            (function() {
                var cleanEmail = "$cleanEmail";
                if (!cleanEmail) return 'false';

                var allInputs = Array.from(document.querySelectorAll("input:not([type='hidden']):not([type='submit'])"));
                var emailInput = allInputs.find(function(i) {
                    var p = (i.placeholder || "").toLowerCase();
                    var a = (i.getAttribute('aria-label') || "").toLowerCase();
                    var n = (i.name || "").toLowerCase();
                    var id = (i.id || "").toLowerCase();
                    var t = (i.type || "").toLowerCase();
                    return p.includes("email") || p.includes("mobile") || p.includes("number") ||
                           a.includes("email") || a.includes("mobile") || a.includes("number") ||
                           n.includes("email") || n.includes("username") || id.includes("email") ||
                           t === "email" || t === "text" || t === "tel";
                });

                if (!emailInput && allInputs.length > 0) {
                    emailInput = allInputs[0];
                }

                if (emailInput) {
                    try {
                        emailInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        emailInput.focus();
                        emailInput.click();

                        // Native React setter + _valueTracker reset
                        if (emailInput._valueTracker) {
                            try { emailInput._valueTracker.setValue(''); } catch(e) {}
                        }

                        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
                        if (nativeSetter && nativeSetter.set) {
                            nativeSetter.set.call(emailInput, cleanEmail);
                        } else {
                            emailInput.value = cleanEmail;
                        }

                        // Dispatch synthetic and DOM events for React/Next.js compatibility
                        try {
                            emailInput.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, data: cleanEmail }));
                        } catch(e) {}
                        try {
                            emailInput.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, data: cleanEmail }));
                        } catch(e) {}
                        emailInput.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                        emailInput.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                        emailInput.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true }));
                        emailInput.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, cancelable: true }));
                        emailInput.dispatchEvent(new Event('blur', { bubbles: true }));

                        // Click Continue / Submit Button after small pause (300ms)
                        setTimeout(function() {
                            var btns = Array.from(document.querySelectorAll("button, [role='button'], div[role='button'], input[type='submit'], a"));
                            var submitBtn = btns.find(function(b) {
                                var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                                return txt === "continue" || txt === "next" || txt === "sign up" || txt === "get started" || txt === "submit" ||
                                       txt.startsWith("continue") || txt.startsWith("next") || txt.startsWith("sign up") || txt.startsWith("get started");
                            }) || btns.find(function(b) { return b.type === 'submit'; });

                            if (submitBtn) {
                                submitBtn.focus();
                                submitBtn.click();
                                try {
                                    submitBtn.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, cancelable: true }));
                                    submitBtn.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true }));
                                } catch(e) {}
                                try {
                                    submitBtn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                                } catch(e) {}
                            }
                        }, 300);

                        return 'true';
                    } catch(e) {
                        return 'error: ' + e.message;
                    }
                }
                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { result ->
            val success = result.contains("true")
            onComplete(success)
        }
    }

    fun injectMuseOtpCode(otp: String, onComplete: (Boolean) -> Unit = {}) {
        val cleanOtp = otp.trim()
        val script = """
            (function() {
                var singleInput = document.querySelector("input[name*='code' i], input[placeholder*='code' i], input[placeholder*='######'], input[autocomplete='one-time-code']");
                var multiInputs = Array.from(document.querySelectorAll("input[maxlength='1'], .code-input input, input[inputmode='numeric']")).filter(function(i) {
                    var style = window.getComputedStyle(i);
                    return style.display !== 'none' && style.visibility !== 'hidden' && i.offsetWidth > 0;
                });
                
                var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
                
                if (multiInputs.length >= 4) {
                    for (var i = 0; i < Math.min(multiInputs.length, '$cleanOtp'.length); i++) {
                        (function(index) {
                            setTimeout(function() {
                                var char = '$cleanOtp'[index];
                                var inp = multiInputs[index];
                                if (inp) {
                                    inp.focus();
                                    if (nativeSetter && nativeSetter.set) nativeSetter.set.call(inp, char);
                                    else inp.value = char;
                                    inp.dispatchEvent(new Event('input', { bubbles: true }));
                                    inp.dispatchEvent(new Event('change', { bubbles: true }));
                                }
                            }, index * 80);
                        })(i);
                    }
                } else if (singleInput) {
                    singleInput.focus();
                    if (nativeSetter && nativeSetter.set) nativeSetter.set.call(singleInput, "$cleanOtp");
                    else singleInput.value = "$cleanOtp";
                    singleInput.dispatchEvent(new Event('input', { bubbles: true }));
                    singleInput.dispatchEvent(new Event('change', { bubbles: true }));
                }
                
                // Human pause after entering OTP before clicking Submit (2x Speed: 700ms)
                setTimeout(function() {
                    var btns = Array.from(document.querySelectorAll("button, [role='button'], input[type='submit']"));
                    var submitBtn = btns.find(function(b) {
                        var txt = (b.innerText || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                        return txt === "confirm" || txt === "next" || txt === "continue" || txt === "verify" || txt === "submit" ||
                               txt.startsWith("confirm") || txt.startsWith("next") || txt.startsWith("continue") || txt.startsWith("verify");
                    });
                    if (submitBtn) {
                        submitBtn.click();
                        try { submitBtn.dispatchEvent(new MouseEvent('click', { bubbles: true })); } catch(e){}
                    }
                }, 700);
                
                return 'true';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { result ->
            onComplete(result.contains("true"))
        }
    }

    fun clickMuseThreeDotsMenu(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                var allButtons = Array.from(document.querySelectorAll("button, [role='button'], div, span, a, svg"));
                var threeDotsBtn = allButtons.find(function(b) {
                    var a = (b.getAttribute('aria-label') || "").toLowerCase();
                    var t = (b.title || "").toLowerCase();
                    var text = (b.innerText || b.textContent || "").trim();
                    return a.includes("more") || a.includes("menu") || a.includes("options") || a.includes("settings") ||
                           t.includes("more") || t.includes("menu") || text === "•••" || text === "..." || text === "···";
                });

                if (!threeDotsBtn) {
                    var topBtns = Array.from(document.querySelectorAll("button, [role='button'], div[tabindex]")).filter(function(b) {
                        var r = b.getBoundingClientRect();
                        return r.width > 0 && r.height > 0 && r.top < 150 && r.right > (window.innerWidth - 120);
                    });
                    if (topBtns.length > 0) {
                        threeDotsBtn = topBtns[0];
                    }
                }

                if (threeDotsBtn) {
                    try { threeDotsBtn.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch(e){}
                    threeDotsBtn.click();
                    try { threeDotsBtn.dispatchEvent(new MouseEvent('click', { bubbles: true })); } catch(e){}
                    return 'true';
                }
                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { result ->
            onComplete(result.contains("true"))
        }
    }

    fun clickMuseGeneralSettings(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                var allEls = Array.from(document.querySelectorAll("div, span, button, a, li, [role='button'], p"));
                var generalBtn = allEls.find(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim();
                    return txt === "General" || txt.startsWith("General\n") || txt.startsWith("General ") || (txt.includes("General") && txt.length < 25);
                });
                if (generalBtn) {
                    try { generalBtn.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch(e){}
                    generalBtn.click();
                    try { generalBtn.dispatchEvent(new MouseEvent('click', { bubbles: true })); } catch(e){}
                    return 'true';
                }
                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { result ->
            onComplete(result.contains("true"))
        }
    }

    fun clickMuseMetaAccount(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                var allEls = Array.from(document.querySelectorAll("div, span, button, a, li, [role='button'], p"));
                var metaBtn = allEls.find(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim();
                    return txt.includes("Meta Account") || txt.includes("personal details") || txt.includes("Password, security");
                });
                if (metaBtn) {
                    try { metaBtn.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch(e){}
                    metaBtn.click();
                    try { metaBtn.dispatchEvent(new MouseEvent('click', { bubbles: true })); } catch(e){}
                    return 'true';
                }
                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { result ->
            onComplete(result.contains("true"))
        }
    }

    fun injectMetaForgotPasswordEmail(email: String) {
        val script = """
            (function() {
                var targetEmail = "$email";
                if (!targetEmail) return;

                var attempts = 0;
                var interval = setInterval(function() {
                    attempts++;
                    if (attempts > 30) { clearInterval(interval); return; }

                    var inputs = Array.from(document.querySelectorAll("input"));
                    var targetInput = inputs.find(function(i) {
                        var p = (i.placeholder || "").toLowerCase();
                        var a = (i.getAttribute('aria-label') || "").toLowerCase();
                        var n = (i.name || "").toLowerCase();
                        var id = (i.id || "").toLowerCase();
                        return p.includes("email") || p.includes("mobile") || p.includes("number") ||
                               a.includes("email") || a.includes("mobile") ||
                               n.includes("email") || n.includes("identify") || id.includes("email");
                    });

                    if (!targetInput && inputs.length > 0) {
                        targetInput = inputs[0];
                    }

                    if (targetInput) {
                        if (targetInput.dataset.autoInjected === "done") return;
                        
                        try { targetInput.focus(); } catch(e){}
                        try {
                            var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                            if (nativeInputValueSetter) {
                                nativeInputValueSetter.call(targetInput, targetEmail);
                            } else {
                                targetInput.value = targetEmail;
                            }
                        } catch(e) {
                            targetInput.value = targetEmail;
                        }

                        targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                        targetInput.dispatchEvent(new Event('change', { bubbles: true }));
                        targetInput.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
                        targetInput.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                        targetInput.dataset.autoInjected = "done";

                        setTimeout(function() {
                            var buttons = Array.from(document.querySelectorAll("button, [role='button'], input[type='submit'], div[role='button']"));
                            var submitBtn = buttons.find(function(b) {
                                var txt = (b.innerText || b.textContent || b.value || "").trim().toLowerCase();
                                return txt.includes("find account") || txt.includes("search") || txt.includes("continue") || txt.includes("next") || txt.includes("submit");
                            });

                            if (!submitBtn && buttons.length > 0) {
                                submitBtn = buttons[0];
                            }

                            if (submitBtn) {
                                try { submitBtn.focus(); } catch(e){}
                                try { submitBtn.click(); } catch(e){}
                                try { submitBtn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window })); } catch(e){}
                                clearInterval(interval);
                            }
                        }, 800);
                    }
                }, 500);
            })();
        """.trimIndent()
        evaluateJs(script)
    }

    fun injectMetaSecondOtpCode(otpCode: String) {
        val script = """
            (function() {
                var code = "$otpCode";
                if (!code) return;

                var attempts = 0;
                var interval = setInterval(function() {
                    attempts++;
                    if (attempts > 30) { clearInterval(interval); return; }

                    var inputs = Array.from(document.querySelectorAll("input"));
                    var targetInput = inputs.find(function(i) {
                        var p = (i.placeholder || "").toLowerCase();
                        var a = (i.getAttribute('aria-label') || "").toLowerCase();
                        var n = (i.name || "").toLowerCase();
                        var id = (i.id || "").toLowerCase();
                        return p.includes("code") || a.includes("code") || n.includes("code") || id.includes("code") || n.includes("otp");
                    });

                    if (!targetInput && inputs.length > 0) {
                        targetInput = inputs[0];
                    }

                    if (targetInput) {
                        if (targetInput.dataset.autoInjected === "done") return;

                        try { targetInput.focus(); } catch(e){}
                        try {
                            var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                            if (nativeInputValueSetter) {
                                nativeInputValueSetter.call(targetInput, code);
                            } else {
                                targetInput.value = code;
                            }
                        } catch(e) {
                            targetInput.value = code;
                        }

                        targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                        targetInput.dispatchEvent(new Event('change', { bubbles: true }));
                        targetInput.dataset.autoInjected = "done";

                        setTimeout(function() {
                            var buttons = Array.from(document.querySelectorAll("button, [role='button'], input[type='submit'], div[role='button']"));
                            var continueBtn = buttons.find(function(b) {
                                var txt = (b.innerText || b.textContent || b.value || "").trim().toLowerCase();
                                return txt === "continue" || txt.startsWith("continue") || txt.includes("continue");
                            });

                            if (!continueBtn) {
                                continueBtn = buttons.find(function(b) {
                                    var txt = (b.innerText || b.textContent || b.value || "").trim().toLowerCase();
                                    return !txt.includes("resend") && !txt.includes("cancel");
                                });
                            }

                            if (continueBtn) {
                                try { continueBtn.focus(); } catch(e){}
                                try { continueBtn.click(); } catch(e){}
                                try { continueBtn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window })); } catch(e){}
                                clearInterval(interval);
                            }
                        }, 800);
                    }
                }, 500);
            })();
        """.trimIndent()
        evaluateJs(script)
    }

    fun injectMetaNewPassword(password: String = "Shakib@2008") {
        val script = """
            (function() {
                var pass = "$password";
                if (!pass) return;

                var attempts = 0;
                var interval = setInterval(function() {
                    attempts++;
                    if (attempts > 30) { clearInterval(interval); return; }

                    var inputs = Array.from(document.querySelectorAll("input"));
                    var targetInput = inputs.find(function(i) {
                        var type = (i.type || "").toLowerCase();
                        var p = (i.placeholder || "").toLowerCase();
                        var a = (i.getAttribute('aria-label') || "").toLowerCase();
                        var n = (i.name || "").toLowerCase();
                        return type === "password" || p.includes("password") || a.includes("password") || n.includes("pass") || n.includes("password");
                    });

                    if (!targetInput && inputs.length > 0) {
                        targetInput = inputs[0];
                    }

                    if (targetInput) {
                        if (targetInput.dataset.autoInjected === "done") return;

                        try { targetInput.focus(); } catch(e){}
                        try {
                            var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                            if (nativeInputValueSetter) {
                                nativeInputValueSetter.call(targetInput, pass);
                            } else {
                                targetInput.value = pass;
                            }
                        } catch(e) {
                            targetInput.value = pass;
                        }

                        targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                        targetInput.dispatchEvent(new Event('change', { bubbles: true }));
                        targetInput.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
                        targetInput.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                        targetInput.dataset.autoInjected = "done";

                        setTimeout(function() {
                            var buttons = Array.from(document.querySelectorAll("button, [role='button'], input[type='submit'], div[role='button']"));
                            var continueBtn = buttons.find(function(b) {
                                var txt = (b.innerText || b.textContent || b.value || "").trim().toLowerCase();
                                return txt === "continue" || txt.startsWith("continue") || txt.includes("continue") || txt.includes("submit");
                            });

                            if (!continueBtn && buttons.length > 0) {
                                continueBtn = buttons[0];
                            }

                            if (continueBtn) {
                                try { continueBtn.focus(); } catch(e){}
                                try { continueBtn.click(); } catch(e){}
                                try { continueBtn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window })); } catch(e){}
                                clearInterval(interval);
                            }
                        }, 800);
                    }
                }, 500);
            })();
        """.trimIndent()
        evaluateJs(script)
    }

    fun injectMetaClickUseMobileOrEmail() {
        val script = """
            (function() {
                function showVisualClickIndicator(x, y) {
                    try {
                        var marker = document.createElement('div');
                        marker.style.position = 'fixed';
                        marker.style.left = (x - 20) + 'px';
                        marker.style.top = (y - 20) + 'px';
                        marker.style.width = '40px';
                        marker.style.height = '40px';
                        marker.style.borderRadius = '50%';
                        marker.style.border = '4px solid #FFED00'; // Neon yellow for ultra-high visibility
                        marker.style.backgroundColor = 'rgba(255, 237, 0, 0.5)';
                        marker.style.boxShadow = '0 0 15px #FFED00';
                        marker.style.pointerEvents = 'none';
                        marker.style.zIndex = '9999999';
                        marker.style.transition = 'transform 0.8s ease-out, opacity 0.8s ease-out';
                        marker.style.transform = 'scale(0.3)';
                        marker.style.opacity = '1';
                        
                        document.body.appendChild(marker);
                        
                        // Force reflow
                        marker.offsetHeight;
                        
                        // Animate ripple scale-up and fade-out
                        marker.style.transform = 'scale(2.5)';
                        marker.style.opacity = '0';
                        
                        setTimeout(function() {
                            try { document.body.removeChild(marker); } catch(e){}
                        }, 1000);
                    } catch(e){}
                }

                function simulateTap(el) {
                    if (!el) return;
                    try {
                        var rect = el.getBoundingClientRect();
                        var x = rect.left + rect.width / 2;
                        var y = rect.top + rect.height / 2;
                        
                        // Show visual ripple
                        showVisualClickIndicator(x, y);

                        // Highlight element outline in RED
                        var originalOutline = el.style.outline;
                        el.style.outline = "4px solid #FFED00";
                        el.style.outlineOffset = "2px";
                        setTimeout(function() {
                            try { el.style.outline = originalOutline; } catch(e){}
                        }, 2000);
                        
                        // 1. Dispatch TouchStart
                        var tStart = new TouchEvent('touchstart', {
                            bubbles: true,
                            cancelable: true,
                            view: window,
                            touches: [new Touch({
                                identifier: Date.now(),
                                target: el,
                                clientX: x,
                                clientY: y,
                                screenX: x,
                                screenY: y,
                                pageX: x,
                                pageY: y
                            })]
                        });
                        el.dispatchEvent(tStart);
                        
                        // 2. Dispatch TouchEnd
                        var tEnd = new TouchEvent('touchend', {
                            bubbles: true,
                            cancelable: true,
                            view: window,
                            changedTouches: [new Touch({
                                identifier: Date.now(),
                                target: el,
                                clientX: x,
                                clientY: y,
                                screenX: x,
                                screenY: y,
                                pageX: x,
                                pageY: y
                            })]
                        });
                        el.dispatchEvent(tEnd);
                        
                        // 3. Dispatch Click
                        var mClick = new MouseEvent('click', {
                            clientX: x,
                            clientY: y,
                            bubbles: true,
                            cancelable: true,
                            view: window
                        });
                        el.dispatchEvent(mClick);
                        
                        // 4. Native click fallback
                        el.click();
                    } catch(e) {}
                }

                var attempts = 0;
                var interval = setInterval(function() {
                    attempts++;
                    if (attempts > 30) { clearInterval(interval); return; }

                    var elements = Array.from(document.querySelectorAll("button, [role='button'], div, span, a, label, p")).reverse();
                    var targetBtn = elements.find(function(el) {
                        var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                        return txt.includes("use mobile number or email address") ||
                               txt.includes("mobile number or email address") ||
                               txt.includes("use mobile number or email") ||
                               txt.includes("mobile number or email") ||
                               txt.includes("email address") ||
                               txt.includes("use mobile number") ||
                               txt.includes("or email");
                    });

                    if (targetBtn) {
                        var el = targetBtn;
                        
                        // Simulate full touch and click on the element itself
                        simulateTap(el);
                        
                        // Simulate click on closest Meta interactive wrapper (if any)
                        var closestWrapper = el.closest("div.x1i10hfl") || el.closest("div.xjbqb8w");
                        if (closestWrapper) {
                            simulateTap(closestWrapper);
                        }

                        // Simulate click on parent and grandparent fallback
                        if (el.parentElement) {
                            simulateTap(el.parentElement);
                        }
                        if (el.parentElement && el.parentElement.parentElement) {
                            simulateTap(el.parentElement.parentElement);
                        }

                        clearInterval(interval);
                    }
                }, 500);
            })();
        """.trimIndent()
        evaluateJs(script)
    }

    fun injectMetaEmailAndContinue(email: String) {
        val script = """
            (function() {
                var targetEmail = "$email";
                if (!targetEmail) return;

                var attempts = 0;
                var interval = setInterval(function() {
                    attempts++;
                    if (attempts > 30) { clearInterval(interval); return; }

                    var inputs = Array.from(document.querySelectorAll("input"));
                    var targetInput = inputs.find(function(i) {
                        var p = (i.placeholder || "").toLowerCase();
                        var a = (i.getAttribute('aria-label') || "").toLowerCase();
                        var n = (i.name || "").toLowerCase();
                        var id = (i.id || "").toLowerCase();
                        return p.includes("email") || p.includes("mobile") || p.includes("number") ||
                               a.includes("email") || a.includes("mobile") ||
                               n.includes("email") || n.includes("identify") || id.includes("email");
                    });

                    if (!targetInput && inputs.length > 0) {
                        targetInput = inputs[0];
                    }

                    if (targetInput) {
                        try { targetInput.focus(); } catch(e){}
                        try {
                            var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                            if (nativeInputValueSetter) {
                                nativeInputValueSetter.call(targetInput, targetEmail);
                            } else {
                                targetInput.value = targetEmail;
                            }
                        } catch(e) {
                            targetInput.value = targetEmail;
                        }

                        targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                        targetInput.dispatchEvent(new Event('change', { bubbles: true }));
                        targetInput.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
                        targetInput.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));

                        setTimeout(function() {
                            var buttons = Array.from(document.querySelectorAll("button, [role='button'], input[type='submit'], div[role='button']"));
                            var submitBtn = buttons.find(function(b) {
                                var txt = (b.innerText || b.textContent || b.value || "").trim().toLowerCase();
                                return txt.includes("continue") || txt.includes("next") || txt.includes("submit");
                            });

                            if (!submitBtn && buttons.length > 0) {
                                submitBtn = buttons[0];
                            }

                            if (submitBtn) {
                                try { submitBtn.focus(); } catch(e){}
                                try { submitBtn.click(); } catch(e){}
                                try { submitBtn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window })); } catch(e){}
                                clearInterval(interval);
                            }
                        }, 800);
                    }
                }, 500);
            })();
        """.trimIndent()
        evaluateJs(script)
    }

    fun injectMetaClickEnterPasswordInstead() {
        val script = """
            (function() {
                function showVisualClickIndicator(x, y) {
                    try {
                        var marker = document.createElement('div');
                        marker.style.position = 'fixed';
                        marker.style.left = (x - 20) + 'px';
                        marker.style.top = (y - 20) + 'px';
                        marker.style.width = '40px';
                        marker.style.height = '40px';
                        marker.style.borderRadius = '50%';
                        marker.style.border = '4px solid #FFED00'; // Neon yellow
                        marker.style.backgroundColor = 'rgba(255, 237, 0, 0.5)';
                        marker.style.boxShadow = '0 0 15px #FFED00';
                        marker.style.pointerEvents = 'none';
                        marker.style.zIndex = '9999999';
                        marker.style.transition = 'transform 0.8s ease-out, opacity 0.8s ease-out';
                        marker.style.transform = 'scale(0.3)';
                        marker.style.opacity = '1';
                        
                        document.body.appendChild(marker);
                        
                        // Force reflow
                        marker.offsetHeight;
                        
                        // Animate ripple scale-up and fade-out
                        marker.style.transform = 'scale(2.5)';
                        marker.style.opacity = '0';
                        
                        setTimeout(function() {
                            try { document.body.removeChild(marker); } catch(e){}
                        }, 1000);
                    } catch(e){}
                }

                function simulateTap(el) {
                    if (!el) return;
                    try {
                        var rect = el.getBoundingClientRect();
                        var x = rect.left + rect.width / 2;
                        var y = rect.top + rect.height / 2;
                        
                        // Show visual ripple
                        showVisualClickIndicator(x, y);

                        // Highlight element outline in RED
                        var originalOutline = el.style.outline;
                        el.style.outline = "4px solid #FFED00";
                        el.style.outlineOffset = "2px";
                        setTimeout(function() {
                            try { el.style.outline = originalOutline; } catch(e){}
                        }, 2000);
                        
                        // 1. Dispatch TouchStart
                        var tStart = new TouchEvent('touchstart', {
                            bubbles: true,
                            cancelable: true,
                            view: window,
                            touches: [new Touch({
                                identifier: Date.now(),
                                target: el,
                                clientX: x,
                                clientY: y,
                                screenX: x,
                                screenY: y,
                                pageX: x,
                                pageY: y
                            })]
                        });
                        el.dispatchEvent(tStart);
                        
                        // 2. Dispatch TouchEnd
                        var tEnd = new TouchEvent('touchend', {
                            bubbles: true,
                            cancelable: true,
                            view: window,
                            changedTouches: [new Touch({
                                identifier: Date.now(),
                                target: el,
                                clientX: x,
                                clientY: y,
                                screenX: x,
                                screenY: y,
                                pageX: x,
                                pageY: y
                            })]
                        });
                        el.dispatchEvent(tEnd);
                        
                        // 3. Dispatch Click
                        var mClick = new MouseEvent('click', {
                            clientX: x,
                            clientY: y,
                            bubbles: true,
                            cancelable: true,
                            view: window
                        });
                        el.dispatchEvent(mClick);
                        
                        // 4. Native click fallback
                        el.click();
                    } catch(e) {}
                }

                var attempts = 0;
                var interval = setInterval(function() {
                    attempts++;
                    if (attempts > 30) { clearInterval(interval); return; }

                    try { document.body.click(); } catch(e){}

                    var elements = Array.from(document.querySelectorAll("button, [role='button'], div, span, a, p")).reverse();
                    var targetBtn = elements.find(function(el) {
                        var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                        return txt.includes("enter password instead") ||
                               txt.includes("password instead") ||
                               txt.includes("use password");
                    });

                    if (targetBtn) {
                        var el = targetBtn;
                        
                        simulateTap(el);
                        
                        var closestWrapper = el.closest("div.x1i10hfl") || el.closest("div.xjbqb8w");
                        if (closestWrapper) {
                            simulateTap(closestWrapper);
                        }

                        if (el.parentElement) {
                            simulateTap(el.parentElement);
                        }
                        if (el.parentElement && el.parentElement.parentElement) {
                            simulateTap(el.parentElement.parentElement);
                        }

                        clearInterval(interval);
                    }
                }, 500);
            })();
        """.trimIndent()
        evaluateJs(script)
    }

    fun injectMetaPasswordAndContinue(password: String) {
        val script = """
            (function() {
                var targetPass = "$password";

                var attempts = 0;
                var interval = setInterval(function() {
                    attempts++;
                    if (attempts > 30) { clearInterval(interval); return; }

                    var inputs = Array.from(document.querySelectorAll("input[type='password'], input"));
                    var targetInput = inputs.find(function(i) {
                        var t = (i.type || "").toLowerCase();
                        var p = (i.placeholder || "").toLowerCase();
                        var a = (i.getAttribute('aria-label') || "").toLowerCase();
                        var n = (i.name || "").toLowerCase();
                        return t === "password" || p.includes("password") || a.includes("password") || n.includes("pass");
                    });

                    if (!targetInput && inputs.length > 0) {
                        targetInput = inputs[0];
                    }

                    if (targetInput) {
                        try { targetInput.focus(); } catch(e){}
                        try {
                            var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                            if (nativeInputValueSetter) {
                                nativeInputValueSetter.call(targetInput, targetPass);
                            } else {
                                targetInput.value = targetPass;
                            }
                        } catch(e) {
                            targetInput.value = targetPass;
                        }

                        targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                        targetInput.dispatchEvent(new Event('change', { bubbles: true }));
                        targetInput.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
                        targetInput.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));

                        setTimeout(function() {
                            var buttons = Array.from(document.querySelectorAll("button, [role='button'], input[type='submit'], div[role='button']"));
                            var submitBtn = buttons.find(function(b) {
                                var txt = (b.innerText || b.textContent || b.value || "").trim().toLowerCase();
                                return txt.includes("continue") || txt.includes("log in") || txt.includes("next") || txt.includes("submit");
                            });

                            if (!submitBtn && buttons.length > 0) {
                                submitBtn = buttons[0];
                            }

                            if (submitBtn) {
                                try { submitBtn.focus(); } catch(e){}
                                try { submitBtn.click(); } catch(e){}
                                try { submitBtn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window })); } catch(e){}
                                clearInterval(interval);
                            }
                        }, 800);
                    }
                }, 500);
            })();
        """.trimIndent()
        evaluateJs(script)
    }

    fun injectMetaClickSaveLoginInfo(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                function showVisualClickIndicator(x, y) {
                    try {
                        var marker = document.createElement('div');
                        marker.style.position = 'fixed';
                        marker.style.left = (x - 20) + 'px';
                        marker.style.top = (y - 20) + 'px';
                        marker.style.width = '40px';
                        marker.style.height = '40px';
                        marker.style.borderRadius = '50%';
                        marker.style.border = '4px solid #00C6FF';
                        marker.style.backgroundColor = 'rgba(0, 198, 255, 0.5)';
                        marker.style.boxShadow = '0 0 15px #00C6FF';
                        marker.style.pointerEvents = 'none';
                        marker.style.zIndex = '9999999';
                        marker.style.transition = 'transform 0.8s ease-out, opacity 0.8s ease-out';
                        marker.style.transform = 'scale(0.3)';
                        marker.style.opacity = '1';
                        
                        document.body.appendChild(marker);
                        marker.offsetHeight;
                        marker.style.transform = 'scale(2.5)';
                        marker.style.opacity = '0';
                        
                        setTimeout(function() {
                            try { document.body.removeChild(marker); } catch(e){}
                        }, 1000);
                    } catch(e){}
                }

                function simulateTap(el) {
                    if (!el) return;
                    try {
                        var rect = el.getBoundingClientRect();
                        var x = rect.left + rect.width / 2 + (window.__clickXOffset || 0);
                        var y = rect.top + rect.height / 2 + (window.__clickYOffset || 0);
                        showVisualClickIndicator(x, y);

                        var originalOutline = el.style.outline;
                        el.style.outline = "4px solid #00C6FF";
                        el.style.outlineOffset = "2px";
                        setTimeout(function() {
                            try { el.style.outline = originalOutline; } catch(e){}
                        }, 2000);

                        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        el.focus();
                        el.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, cancelable: true }));
                        el.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true }));
                        el.click();
                        el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                    } catch(e) {}
                }

                // 1. Search true buttons and clickable controls only
                var buttons = Array.from(document.querySelectorAll("button, [role='button'], div[role='button'], input[type='submit'], a[role='button']"));
                
                // Exclude any container elements that wrap other buttons/inputs
                var leafButtons = buttons.filter(function(b) {
                    var childBtns = b.querySelectorAll("button, [role='button'], input[type='submit']");
                    return childBtns.length === 0;
                });

                // First priority: Exact match for Save / Save info / সেভ
                var saveBtn = leafButtons.find(function(b) {
                    var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                    return txt === "save" ||
                           txt === "save info" ||
                           txt === "save login info" ||
                           txt === "save login" ||
                           txt === "সেভ" ||
                           txt === "সেভ করুন";
                });

                // Second priority: Starts with Save (excluding question titles)
                if (!saveBtn) {
                    saveBtn = leafButtons.find(function(b) {
                        var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                        return (txt.startsWith("save") || txt.includes("সেভ")) && !txt.includes("?") && !txt.includes("so that you won't");
                    });
                }

                // Third priority: Any button containing Save text that is not a modal/card wrapper
                if (!saveBtn) {
                    saveBtn = Array.from(document.querySelectorAll("button, [role='button'], input[type='submit']")).find(function(b) {
                        var txt = (b.innerText || b.textContent || b.value || "").trim().toLowerCase();
                        return (txt === "save" || txt.startsWith("save info")) && b.children.length <= 2;
                    });
                }

                if (saveBtn) {
                    simulateTap(saveBtn);
                    return 'true';
                }
                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaClickInstagramManageButton(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                function showVisualClickIndicator(x, y) {
                    try {
                        var marker = document.createElement('div');
                        marker.style.position = 'fixed';
                        marker.style.left = (x - 22) + 'px';
                        marker.style.top = (y - 22) + 'px';
                        marker.style.width = '44px';
                        marker.style.height = '44px';
                        marker.style.borderRadius = '50%';
                        marker.style.border = '4px solid #00E5FF';
                        marker.style.backgroundColor = 'rgba(0, 229, 255, 0.45)';
                        marker.style.boxShadow = '0 0 20px #00E5FF';
                        marker.style.pointerEvents = 'none';
                        marker.style.zIndex = '9999999';
                        marker.style.transition = 'transform 0.8s ease-out, opacity 0.8s ease-out';
                        marker.style.transform = 'scale(0.3)';
                        marker.style.opacity = '1';
                        
                        document.body.appendChild(marker);
                        marker.offsetHeight;
                        marker.style.transform = 'scale(2.5)';
                        marker.style.opacity = '0';
                        setTimeout(function() {
                            try { document.body.removeChild(marker); } catch(e){}
                        }, 1000);
                    } catch(e){}
                }

                function simulateTap(el) {
                    if (!el) return;
                    try {
                        var rect = el.getBoundingClientRect();
                        var x = rect.left + rect.width / 2;
                        var y = rect.top + rect.height / 2;
                        showVisualClickIndicator(x, y);

                        var originalOutline = el.style.outline;
                        el.style.outline = "4px solid #00E5FF";
                        el.style.outlineOffset = "2px";
                        setTimeout(function() {
                            try { el.style.outline = originalOutline; } catch(e){}
                        }, 2000);

                        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        el.focus();
                        el.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, cancelable: true }));
                        el.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true }));
                        el.click();
                        el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                    } catch(e) {}
                }

                // 1. Dismiss "Something went wrong" popup if present
                var okButtons = Array.from(document.querySelectorAll("button, [role='button'], div, span"));
                var okBtn = okButtons.find(function(b) {
                    var txt = (b.innerText || b.textContent || "").trim().toLowerCase();
                    return txt === "ok" || txt === "dismiss";
                });
                if (okBtn) {
                    simulateTap(okBtn);
                }

                var targetManageBtn = null;

                // 2. Visual Human-like Search: find the exact element with text "Instagram"
                var igTextEl = Array.from(document.querySelectorAll("div, span, h1, h2, h3, h4, p")).find(function(el) {
                    var t = (el.innerText || el.textContent || "").trim();
                    return t === "Instagram";
                });

                // Climb up parents and look for the specific Manage button inside the Instagram block
                if (igTextEl) {
                    var parent = igTextEl.parentElement;
                    for (var depth = 0; depth < 5 && parent; depth++) {
                        var manageBtn = Array.from(parent.querySelectorAll("button, div[role='button'], a, div.x1i10hfl, div.xjbqb8w, div, span")).find(function(el) {
                            var t = (el.innerText || el.textContent || "").trim().toLowerCase();
                            return t === "manage" || t === "manage account";
                        });
                        if (manageBtn) {
                            targetManageBtn = manageBtn;
                            break;
                        }
                        parent = parent.parentElement;
                    }
                }

                // Fallback to first Manage button if Instagram-specific not found
                if (!targetManageBtn) {
                    var allButtons = Array.from(document.querySelectorAll("button, div[role='button'], a, [tabindex='0'], div.x1i10hfl, div.xjbqb8w, div, span"));
                    var manageButtons = allButtons.filter(function(b) {
                        var t = (b.innerText || b.textContent || b.getAttribute('aria-label') || "").trim().toLowerCase();
                        return t === "manage" || t === "manage account" || t.startsWith("manage") || t.includes("manage");
                    });
                    if (manageButtons.length > 0) {
                        targetManageBtn = manageButtons[0];
                    }
                }

                if (targetManageBtn) {
                    var clickable = targetManageBtn.closest("button, div[role='button'], a, div.x1i10hfl, div.xjbqb8w") || targetManageBtn;
                    simulateTapSync(clickable);
                    try { clickable.click(); } catch(e){}
                    if (clickable !== targetManageBtn) {
                        try { targetManageBtn.click(); } catch(e){}
                    }
                    return 'true';
                }

                return okBtn ? 'dismissed_ok' : 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true") || res.contains("dismissed_ok"))
        }
    }

    fun injectAutoClickInstagramProfileAndNavigateToUsername(onComplete: (String) -> Unit = {}) {
        val script = """
            (function() {
                try {
                    function showVisualClickIndicator(x, y) {
                        try {
                            var marker = document.createElement('div');
                            marker.style.position = 'fixed';
                            marker.style.left = (x - 20) + 'px';
                            marker.style.top = (y - 20) + 'px';
                            marker.style.width = '40px';
                            marker.style.height = '40px';
                            marker.style.borderRadius = '50%';
                            marker.style.border = '3px solid #00E5FF';
                            marker.style.backgroundColor = 'rgba(0, 229, 255, 0.4)';
                            marker.style.boxShadow = '0 0 15px #00E5FF';
                            marker.style.zIndex = '2147483647';
                            marker.style.pointerEvents = 'none';
                            marker.style.transition = 'all 0.5s ease-out';
                            document.body.appendChild(marker);
                            setTimeout(function() {
                                try { document.body.removeChild(marker); } catch(e){}
                            }, 800);
                        } catch(e){}
                    }

                    function simulateTap(el) {
                        if (!el) return;
                        try {
                            var rect = el.getBoundingClientRect();
                            // Target upper/center part of the row card to avoid hitting bottom padding or buttons below
                            var x = rect.left + rect.width / 2;
                            var y = rect.top + Math.min(rect.height / 2, 24);
                            showVisualClickIndicator(x, y);
                            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            el.focus();
                            el.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, cancelable: true }));
                            el.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true }));
                            el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                            el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                            el.click();
                            el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                        } catch(e) {
                            try { el.click(); } catch(e2){}
                        }
                    }

                    // 1. If already at username URL, ensure input stays focused and keyboard open
                    var curUrl = window.location.href || "";
                    if (curUrl.includes("/username/")) {
                        var inputs = Array.from(document.querySelectorAll("input[type='text'], input:not([type='password']):not([type='hidden'])"));
                        var targetInput = inputs.find(function(i) {
                            var p = (i.placeholder || '').toLowerCase();
                            var l = (i.getAttribute('aria-label') || '').toLowerCase();
                            return p.includes('name') || l.includes('name') || i.offsetParent !== null;
                        }) || inputs[0];
                        if (targetInput) {
                            targetInput.focus();
                        }
                        return "already_at_username: " + curUrl;
                    }

                    // 2. Check if current URL already has the numeric profile ID: e.g. /profiles/178414.../
                    var curProfileMatch = curUrl.match(/\/profiles\/(\d+)/i);
                    if (curProfileMatch && curProfileMatch[1]) {
                        var directUrl = "https://accountscenter.instagram.com/profiles/" + curProfileMatch[1] + "/username/?entrypoint=fb_account_center";
                        window.location.href = directUrl;
                        return "navigated_from_url: " + directUrl;
                    }

                    // 3. Check if any link with /profiles/<ID>/ or /username/ already exists in DOM
                    var links = Array.from(document.querySelectorAll("a[href*='/profiles/'], a[href*='/username/']"));
                    for (var i = 0; i < links.length; i++) {
                        var h = links[i].href || links[i].getAttribute('href') || "";
                        var pm = h.match(/\/profiles\/(\d+)/i);
                        if (pm && pm[1]) {
                            var isIg = (links[i].innerText || links[i].textContent || h).toLowerCase().includes("instagram") || links.length === 1;
                            if (isIg || links.length === 1) {
                                var targetUsernameUrl = "https://accountscenter.instagram.com/profiles/" + pm[1] + "/username/?entrypoint=fb_account_center";
                                window.location.href = targetUsernameUrl;
                                return "navigated_from_link: " + targetUsernameUrl;
                            }
                        }
                    }

                    // 4. Dismiss any popup/dialog if present
                    var popButtons = Array.from(document.querySelectorAll("button, [role='button'], div, span"));
                    var okBtn = popButtons.find(function(b) {
                        var txt = (b.innerText || b.textContent || "").trim().toLowerCase();
                        return txt === "ok" || txt === "dismiss";
                    });
                    if (okBtn) { simulateTap(okBtn); }

                    // 5. Find the exact Instagram Profile card and click its UPPER area
                    var igProfileCard = null;

                    var igElements = Array.from(document.querySelectorAll("div, span, h1, h2, h3, h4, p, a, [role='button']")).filter(function(el) {
                        var t = (el.innerText || el.textContent || "").trim().toLowerCase();
                        var a = (el.getAttribute('aria-label') || "").trim().toLowerCase();
                        return t === "instagram" || a === "instagram" || (t.includes("instagram") && !t.includes("facebook") && !t.includes("add accounts"));
                    });

                    for (var j = 0; j < igElements.length; j++) {
                        var candidate = igElements[j].closest("div[role='button'], a[href*='profiles'], div[tabindex='0'], li, div.x1i10hfl") || igElements[j];
                        if (candidate && candidate.offsetHeight > 20 && candidate.offsetWidth > 40) {
                            var textContent = (candidate.innerText || candidate.textContent || "").toLowerCase();
                            if (!textContent.includes("add accounts")) {
                                igProfileCard = candidate;
                                break;
                            }
                        }
                    }

                    if (!igProfileCard) {
                        var profileRows = Array.from(document.querySelectorAll("div[role='button'], a[href*='profiles'], div[tabindex='0']")).filter(function(el) {
                            var t = (el.innerText || el.textContent || "").trim().toLowerCase();
                            return !t.includes("add accounts") && !t.includes("settings") && el.offsetHeight > 30;
                        });
                        if (profileRows.length > 0) {
                            igProfileCard = profileRows[0];
                        }
                    }

                    var actionTaken = "";
                    if (igProfileCard) {
                        simulateTap(igProfileCard);
                        actionTaken = "clicked_instagram_profile";
                    }

                    // 6. Install live watcher that continuously intercepts URL / history changes and redirects to /username/
                    if (!window.__ig_nav_to_username_installed) {
                        window.__ig_nav_to_username_installed = true;

                        function checkAndRedirect() {
                            var url = window.location.href || "";
                            if (url.includes("/username/")) {
                                var inputs = Array.from(document.querySelectorAll("input[type='text'], input:not([type='password']):not([type='hidden'])"));
                                var targetInput = inputs.find(function(i) {
                                    var p = (i.placeholder || '').toLowerCase();
                                    var l = (i.getAttribute('aria-label') || '').toLowerCase();
                                    return p.includes('name') || l.includes('name') || i.offsetParent !== null;
                                }) || inputs[0];
                                if (targetInput) {
                                    targetInput.focus();
                                }
                                return true;
                            }

                            var m = url.match(/\/profiles\/(\d+)/i);
                            if (m && m[1]) {
                                window.location.href = "https://accountscenter.instagram.com/profiles/" + m[1] + "/username/?entrypoint=fb_account_center";
                                return true;
                            }

                            var userBtns = Array.from(document.querySelectorAll("button, [role='button'], a, div, span"));
                            var uBtn = userBtns.find(function(b) {
                                var txt = (b.innerText || b.textContent || "").trim().toLowerCase();
                                return txt === "username" || txt === "ইউজারনেম" || (b.getAttribute('href') || "").includes("/username/");
                            });
                            if (uBtn) {
                                var linkHref = uBtn.getAttribute('href') || uBtn.href || "";
                                if (linkHref.includes("/profiles/")) {
                                    var matchId = linkHref.match(/\/profiles\/(\d+)/i);
                                    if (matchId && matchId[1]) {
                                        window.location.href = "https://accountscenter.instagram.com/profiles/" + matchId[1] + "/username/?entrypoint=fb_account_center";
                                        return true;
                                    }
                                }
                                simulateTap(uBtn);
                                return true;
                            }
                            return false;
                        }

                        var origPushState = history.pushState;
                        history.pushState = function() {
                            origPushState.apply(this, arguments);
                            setTimeout(checkAndRedirect, 50);
                        };
                        var origReplaceState = history.replaceState;
                        history.replaceState = function() {
                            origReplaceState.apply(this, arguments);
                            setTimeout(checkAndRedirect, 50);
                        };

                        var watchInterval = setInterval(function() {
                            if (checkAndRedirect()) {
                                clearInterval(watchInterval);
                            }
                        }, 200);

                        setTimeout(function() {
                            clearInterval(watchInterval);
                        }, 15000);
                    }

                    return actionTaken.length > 0 ? actionTaken : "searching";
                } catch(e) {
                    return "error: " + e.message;
                }
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res ?: "")
        }
    }

    fun injectMetaExtractAndNavigateToUsernameUrl(onComplete: (String) -> Unit = {}) {
        injectAutoClickInstagramProfileAndNavigateToUsername(onComplete)
    }

    fun injectMetaExtractAndNavigateToInstagramRemoveUrl(fallbackUrl: String = "https://accountscenter.meta.com/manage/17841466786633408/remove/?accountType=INSTAGRAM", onComplete: (String) -> Unit = {}) {
        val script = """
            (function() {
                try {
                    // 1. Look for existing link with /remove/ and INSTAGRAM
                    var links = Array.from(document.querySelectorAll("a[href*='/remove/'], a[href*='manage'], a[href*='accountType=INSTAGRAM']"));
                    var match = links.find(function(a) {
                        var h = (a.href || a.getAttribute('href') || "");
                        return h.includes("/remove/") && (h.includes("INSTAGRAM") || h.includes("instagram"));
                    });
                    if (match && match.href) {
                        window.location.href = match.href;
                        return match.href;
                    }

                    // 2. Look in page HTML source for dynamic regex pattern: /manage/<account_id>/remove/?accountType=INSTAGRAM
                    var html = document.documentElement.innerHTML;
                    var regex = /\/manage\/(\d+)\/remove\/\?accountType=INSTAGRAM/i;
                    var found = html.match(regex);
                    if (found && found[0]) {
                        var fullUrl = "https://accountscenter.meta.com" + found[0];
                        window.location.href = fullUrl;
                        return fullUrl;
                    }
                } catch(e){}

                // 3. Fallback to specified URL
                window.location.href = "$fallbackUrl";
                return "$fallbackUrl";
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res)
        }
    }

    fun injectMetaMoveAccountStep1(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < window.innerHeight;
                }

                // 1. Direct Target: Meta's atomic selector a.x1i10hfl.x1qjc9v5 for "Move out of this Accounts Center"
                var links = Array.from(document.querySelectorAll("a.x1i10hfl, a[class*='x1i10hfl'], a, div[role='button'], button, [tabindex='0']")).filter(isVisible);
                var targetLink = links.find(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    if (txt.includes("deactivation") || txt.includes("memorialization")) return false;
                    return txt.includes("move out of") || txt.includes("move your instagram");
                });

                if (targetLink) {
                    var clickable = targetLink.closest("a, div[role='button'], button") || targetLink;
                    simulateTapSync(clickable);
                    try { clickable.click(); } catch(e){}
                    return 'true';
                }

                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaMoveAccountStep2(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < window.innerHeight;
                }

                // 1. Search for "Move account" or "lose access" or "Yes, continue" card
                var allItems = Array.from(document.querySelectorAll("div.x1i10hfl, div[class*='x1i10hfl'], button, div[role='button'], a, [tabindex='0'], span, div")).filter(isVisible);
                
                var candidates = allItems.filter(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    if (!txt || txt.includes("cancel") || txt.includes("back") || txt.includes("other options") || txt.includes("manage how you follow")) return false;
                    var r = el.getBoundingClientRect();
                    if (r.height > 350 || r.height === 0) return false;
                    
                    return txt === "move account" ||
                           txt.startsWith("move account") ||
                           txt.includes("move account") ||
                           txt.includes("lose access") ||
                           txt.includes("yes, continue") ||
                           txt.includes("yes continue");
                });

                // Sort by area ascending to get the specific title/card element
                candidates.sort(function(a, b) {
                    var rA = a.getBoundingClientRect();
                    var rB = b.getBoundingClientRect();
                    var areaA = rA.width * rA.height;
                    var areaB = rB.width * rB.height;
                    return areaA - areaB;
                });

                var target = candidates[0];

                if (target) {
                    var clickable = target.closest("div[role='button'], button, a, div.x1i10hfl") || target;
                    simulateTapSync(clickable);
                    try { clickable.click(); } catch(e){}
                    if (clickable !== target) {
                        try { target.click(); } catch(e){}
                    }
                    return 'true';
                }

                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaClickProfileCard(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers
                var allEls = Array.from(document.querySelectorAll("div, span, p, a, [role='button']"));
                var target = allEls.find(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    return txt === "1 profile" || txt === "1 profiles" || (txt.includes("profile") && !txt.includes("meta account") && !txt.includes("about your"));
                });
                if (target) {
                    var clickable = target.closest("div[role='button'], a, button, li") || target;
                    simulateTapSync(clickable);
                    return "true";
                }
                return "false";
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res -> onComplete(res.contains("true")) }
    }

    fun injectMetaClickEmailCard(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers
                var allEls = Array.from(document.querySelectorAll("div, span, p, a, [role='button']"));
                var target = allEls.find(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    return txt.includes("@") && (txt.includes(".") || txt.includes("frostypeak"));
                });
                if (target) {
                    var clickable = target.closest("div[role='button'], a, button, li") || target;
                    simulateTapSync(clickable);
                    return "true";
                }
                return "false";
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res -> onComplete(res.contains("true")) }
    }

    fun injectMetaClickAddOrEditContact(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers
                var allEls = Array.from(document.querySelectorAll("div, span, p, a, [role='button']"));
                var target = allEls.find(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    return txt.includes("add or edit contact info") || txt.includes("add or edit contact");
                });
                if (target) {
                    var clickable = target.closest("div[role='button'], a, button, li") || target;
                    simulateTapSync(clickable);
                    return "true";
                }
                return "false";
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res -> onComplete(res.contains("true")) }
    }

    fun injectMetaClickAddNewContact(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.top < (window.innerHeight + 250) && r.bottom > -250;
                }

                // If "Add email" is ALREADY open, click it immediately!
                var openEmailOption = Array.from(document.querySelectorAll("div[role='button'], button, a, li, [tabindex='0'], span, p")).find(function(el) {
                    if (!isVisible(el)) return false;
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    return (txt === "add email address" || txt === "add email" || (txt.includes("add email") && !txt.includes("contact info"))) && !txt.includes("mobile");
                });
                if (openEmailOption) {
                    var c = openEmailOption.closest("div[role='button'], button, a, li, [tabindex='0']") || openEmailOption;
                    simulateTapSync(c);
                    try { c.click(); } catch(e){}
                    try { openEmailOption.click(); } catch(e){}
                    triggerReactHandler(c);
                    triggerReactHandler(openEmailOption);
                    return "true";
                }

                var allEls = Array.from(document.querySelectorAll("div[role='button'], button, a, [tabindex='0'], li, span, p, div")).filter(isVisible);
                var target = allEls.find(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    return (txt === "add new contact info" || txt === "add new contact" || txt === "add contact") && el.children.length <= 2;
                });
                if (!target) {
                    target = allEls.find(function(el) {
                        var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                        return (txt === "add new contact info" || txt === "add new contact" || txt === "add contact");
                    });
                }
                if (!target) {
                    target = allEls.find(function(el) {
                        var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                        return (txt.includes("add new contact") || txt.includes("add contact info")) && !txt.includes("mobile") && !txt.includes("email") && txt.length < 45;
                    });
                }
                if (target) {
                    var clickable = target.closest("div[role='button'], button, a, li, [tabindex='0']") || target;
                    var cText = (clickable.innerText || "").trim().toLowerCase();
                    if (cText.includes("mobile") || cText.includes("email")) {
                        clickable = target;
                    }
                    simulateTapSync(clickable);
                    try { clickable.click(); } catch(e){}
                    try { target.click(); } catch(e){}
                    triggerReactHandler(clickable);
                    triggerReactHandler(target);

                    // Polling watcher: As soon as the modal bottom sheet opens with "Add email address", click it!
                    var pollCount = 0;
                    var interval = setInterval(function() {
                        pollCount++;
                        var emailOpt = Array.from(document.querySelectorAll("div[role='button'], button, a, li, [tabindex='0'], span, p")).find(function(el) {
                            if (!isVisible(el)) return false;
                            var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                            return (txt === "add email address" || txt === "add email" || (txt.includes("add email") && !txt.includes("contact info"))) && !txt.includes("mobile");
                        });
                        if (emailOpt) {
                            clearInterval(interval);
                            var optClickable = emailOpt.closest("div[role='button'], button, a, li, [tabindex='0']") || emailOpt;
                            simulateTapSync(optClickable);
                            try { optClickable.click(); } catch(e){}
                            try { emailOpt.click(); } catch(e){}
                            triggerReactHandler(optClickable);
                            triggerReactHandler(emailOpt);
                        } else if (pollCount > 12) {
                            clearInterval(interval);
                        }
                    }, 250);

                    return "true";
                }
                return "false";
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res -> onComplete(res.contains("true")) }
    }

    fun injectMetaClickAddEmailAddressOption(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.top < window.innerHeight && r.bottom > 0;
                }

                // If already on the email form with a visible input field, DO NOT click anything (avoids clicking Back)!
                var existingInput = Array.from(document.querySelectorAll("input[type='text'], input[type='email'], input[name*='email'], input[placeholder*='email']")).find(isVisible);
                if (existingInput) {
                    return "true";
                }

                var container = document.querySelector("[role='dialog'], [role='menu'], [aria-modal='true']") || document.body;
                var allEls = Array.from(container.querySelectorAll("div[role='button'], button, a, li, [tabindex='0'], span, p, div")).filter(isVisible);
                
                // Exclude any Back or Close button
                allEls = allEls.filter(function(el) {
                    var aria = (el.getAttribute('aria-label') || "").toLowerCase();
                    var txt = (el.innerText || "").toLowerCase();
                    if (aria.includes("back") || aria.includes("close") || txt.includes("back to")) return false;
                    return true;
                });

                var target = allEls.find(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    return (txt === "add email address" || txt === "add email" || txt === "email address" || txt === "email") &&
                           !txt.includes("mobile") && !txt.includes("contact info");
                });
                if (!target) {
                    target = allEls.find(function(el) {
                        var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                        return (txt.includes("add email") || txt.includes("email address")) &&
                               !txt.includes("mobile") && !txt.includes("contact info") && txt.length < 40;
                    });
                }
                if (!target) {
                    var clickables = Array.from(container.querySelectorAll("div[role='button'], button, a, li, [tabindex='0']")).filter(isVisible);
                    target = clickables.find(function(el) {
                        var txt = (el.innerText || "").trim().toLowerCase();
                        var aria = (el.getAttribute('aria-label') || "").toLowerCase();
                        if (aria.includes("back") || aria.includes("close")) return false;
                        return txt.includes("email") && !txt.includes("mobile") && !txt.includes("phone") && !txt.includes("manage your");
                    });
                }
                if (target) {
                    var clickable = target.closest("div[role='button'], button, a, li, [tabindex='0']") || target;
                    var cText = (clickable.innerText || "").trim().toLowerCase();
                    var cAria = (clickable.getAttribute('aria-label') || "").toLowerCase();
                    if (cAria.includes("back") || cAria.includes("close")) {
                        return "false";
                    }
                    if (cText.includes("mobile") || cText.includes("choose contact type")) {
                        clickable = target;
                    }
                    simulateTapSync(clickable);
                    try { clickable.click(); } catch(e){}
                    try { target.click(); } catch(e){}
                    triggerReactHandler(clickable);
                    triggerReactHandler(target);
                    return "true";
                }
                return "false";
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res -> onComplete(res.contains("true")) }
    }

    fun injectMetaInputTempEmailAndSubmit(tempEmail: String, onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.top < window.innerHeight && r.bottom > 0;
                }

                // 1. Locate the visible email input field
                var allInputs = Array.from(document.querySelectorAll("input")).filter(isVisible);
                var emailInput = allInputs.find(function(inp) {
                    var type = (inp.type || "").toLowerCase();
                    var name = (inp.name || "").toLowerCase();
                    var placeholder = (inp.placeholder || "").toLowerCase();
                    var aria = (inp.getAttribute('aria-label') || "").toLowerCase();
                    return type === "email" ||
                           name.includes("email") ||
                           name.includes("contact_point") ||
                           placeholder.includes("email") ||
                           placeholder.includes("enter") ||
                           aria.includes("email");
                }) || allInputs.find(function(inp) {
                    var type = (inp.type || "").toLowerCase();
                    return type === "text" || type === "email";
                });

                if (emailInput) {
                    try { emailInput.scrollIntoView({ behavior: 'instant', block: 'center' }); } catch(e){}
                    emailInput.focus();

                    // Clear React 18 internal value tracker
                    if (emailInput._valueTracker) {
                        try { emailInput._valueTracker.setValue(""); } catch(e){}
                    }

                    try {
                        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value") ||
                                           Object.getOwnPropertyDescriptor(emailInput.constructor.prototype, "value");
                        if (nativeSetter && nativeSetter.set) {
                            nativeSetter.set.call(emailInput, "$tempEmail");
                        } else {
                            emailInput.value = "$tempEmail";
                        }
                    } catch(e) {
                        emailInput.value = "$tempEmail";
                    }

                    try { emailInput.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, data: "$tempEmail" })); } catch(e) {}
                    try { emailInput.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, data: "$tempEmail" })); } catch(e) {}
                    emailInput.dispatchEvent(new Event('input', { bubbles: true }));
                    emailInput.dispatchEvent(new Event('change', { bubbles: true }));
                    
                    try {
                        for (var k in emailInput) {
                            if (k.startsWith('__reactProps') || k.startsWith('__reactEventHandlers')) {
                                var p = emailInput[k];
                                if (p && typeof p.onChange === 'function') {
                                    p.onChange({ target: { value: "$tempEmail" } });
                                }
                            }
                        }
                    } catch(e){}
                }

                // 2. Select the Instagram account checkbox SAFELY (strictly avoid back button and header)
                var checkboxes = Array.from(document.querySelectorAll("input[type='checkbox'], [role='checkbox'], input[type='radio'], [role='radio']")).filter(isVisible);
                var checkedAny = false;
                checkboxes.forEach(function(cb) {
                    try {
                        var isChecked = cb.checked || cb.getAttribute('aria-checked') === 'true' || cb.getAttribute('data-state') === 'checked';
                        if (!isChecked) {
                            cb.click();
                            triggerReactHandler(cb);
                            checkedAny = true;
                        }
                    } catch(e){}
                });

                // Fallback: If no checkbox element, select account row strictly BELOW the email input
                if (!checkedAny && emailInput) {
                    var emailRect = emailInput.getBoundingClientRect();
                    var accountItems = Array.from(document.querySelectorAll("div[role='button'], div[role='listitem'], li")).filter(function(el) {
                        if (!isVisible(el)) return false;
                        var r = el.getBoundingClientRect();
                        if (r.top <= emailRect.bottom + 10) return false;
                        var t = (el.innerText || "").toLowerCase();
                        var aria = (el.getAttribute('aria-label') || "").toLowerCase();
                        if (aria.includes("back") || aria.includes("close") || t.includes("back") || t.includes("cancel") || t.includes("next")) return false;
                        return t.includes("instagram") || t.includes("@") || t.includes("profile");
                    });

                    if (accountItems.length > 0) {
                        var firstAccount = accountItems[0];
                        try {
                            firstAccount.click();
                            triggerReactHandler(firstAccount);
                        } catch(e){}
                    }
                }

                // 3. Click the Next button
                setTimeout(function() {
                    var allButtons = Array.from(document.querySelectorAll("button, div[role='button'], [tabindex='0']")).filter(isVisible);
                    var nextBtn = allButtons.find(function(el) {
                        var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                        var aria = (el.getAttribute('aria-label') || "").toLowerCase();
                        if (aria.includes("back") || aria.includes("close") || aria.includes("cancel")) return false;
                        if (txt.includes("back") || txt.includes("cancel") || txt.includes("close")) return false;
                        return (txt === "next" || txt === "নেক্সট" || txt.startsWith("next"));
                    });
                    if (nextBtn) {
                        var clickable = nextBtn.closest("div[role='button'], button") || nextBtn;
                        simulateTapSync(clickable);
                        try { clickable.click(); } catch(e){}
                        triggerReactHandler(clickable);
                    }
                }, 1000);

                return "true";
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res -> onComplete(res.contains("true")) }
    }

    fun injectMetaInputOtpAndSubmit(otp: String, onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers
                var otpInput = document.querySelector("input[type='text'], input[name*='code'], input[placeholder*='code'], input[inputmode='numeric']");
                if (otpInput) {
                    try {
                        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
                        if (nativeSetter && nativeSetter.set) {
                            nativeSetter.set.call(otpInput, "$otp");
                        } else {
                            otpInput.value = "$otp";
                        }
                    } catch(e) {
                        otpInput.value = "$otp";
                    }

                    try { otpInput.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, data: "$otp" })); } catch(e) {}
                    try { otpInput.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, data: "$otp" })); } catch(e) {}
                    otpInput.dispatchEvent(new Event('input', { bubbles: true }));
                    otpInput.dispatchEvent(new Event('change', { bubbles: true }));
                    
                    try {
                        var key = Object.keys(otpInput).find(function(k){return k.startsWith('__reactProps')||k.startsWith('__reactEventHandlers');});
                        if (key && otpInput[key] && typeof otpInput[key].onChange === 'function') {
                            otpInput[key].onChange({ target: { value: "$otp" } });
                        }
                    } catch(e){}
                }

                setTimeout(function() {
                    var nextBtn = Array.from(document.querySelectorAll("button, div[role='button'], [tabindex='0']")).find(function(el) {
                        var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                        return txt === "next" || txt.startsWith("next") || txt.includes("next");
                    });
                    if (nextBtn) {
                        var clickable = nextBtn.closest("div[role='button'], button") || nextBtn;
                        simulateTapSync(clickable);
                    }
                }, 800);

                return "true";
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res -> onComplete(res.contains("true")) }
    }

    fun injectMetaCheckCurrentStep(callback: (String) -> Unit) {
        val script = """
            (function() {
                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.top < window.innerHeight && r.bottom > 0;
                }

                var bodyText = (document.body ? document.body.innerText : "") || "";
                var lower = bodyText.toLowerCase();
                var pwdInput = document.querySelector("input[type='password'], input[name*='pass']");
                var hasVisiblePasswordInput = pwdInput && isVisible(pwdInput);

                // --- EXISTING AUTO 2 STEPS (UNCHANGED) ---

                // 0.1. "To create an Instagram account with your Meta account, allow the following" / "Allow and continue"
                if (lower.includes("to create an instagram account") || 
                    lower.includes("allow the following") || 
                    (lower.includes("allow and continue") && !lower.includes("finish moving"))) {
                    return "STEP_ALLOW_AND_CONTINUE";
                }

                // 0.2. Meta Login Screen with "Log in or create an account" -> "Continue with Instagram"
                if (lower.includes("continue with instagram") || 
                    (lower.includes("log in or create an account") && lower.includes("instagram"))) {
                    return "STEP_META_LOGIN_CONTINUE_WITH_IG";
                }

                // 0.25. Meta Login Screen with "Continue as <Name>"
                if (lower.includes("log in with your meta account") || 
                    lower.includes("continue as ") || 
                    (lower.includes("log in to another account") && lower.includes("continue as"))) {
                    return "STEP_META_LOGIN_CONTINUE_AS";
                }

                // 1. Finish moving final step ("Finish moving", "Yes, finish moving", "Yes. finish moving")
                if (lower.includes("finish moving") || lower.includes("yes, finish moving") || lower.includes("yes. finish moving") || lower.includes("yes finish moving") ||
                    lower.includes("finish move") || lower.includes("yes, move account") || lower.includes("yes move account") || lower.includes("সরানো শেষ") || lower.includes("মুভ শেষ")) {
                    return "STEP_3_5_FINISH_MOVING";
                }

                // 1.5. Create Password step ("You need to create a password", "Create password")
                if (lower.includes("create a password") || lower.includes("create password") || lower.includes("you need to create a password")) {
                    return "STEP_CREATE_PASSWORD";
                }

                // 2. Visible Password input on screen
                if (hasVisiblePasswordInput || lower.includes("re-enter your password") || lower.includes("enter your password")) {
                    return "STEP_4_PASSWORD";
                }

                // 3. Step 2: "Are you sure you want to move" OR "Yes, continue" / "Yes continue" / "Yes. continue"
                if (lower.includes("are you sure you want to move") || 
                    lower.includes("yes, continue") || 
                    lower.includes("yes continue") || 
                    lower.includes("yes. continue") ||
                    lower.includes("move this instagram profile")) {
                    return "STEP_2_YES_CONTINUE";
                }

                // 4. Step 3: Warning screen ("lose access", "move account", "simplified login", "cross-profile experiences", "settings in one place")
                if (lower.includes("lose access") || lower.includes("move account") || lower.includes("connected experiences") || lower.includes("simplified login") || lower.includes("cross-profile experiences") || lower.includes("settings in one place")) {
                    return "STEP_3_WARNING";
                }

                // 5. Step 1: Manage modal sheet ("Move out of this Meta Account", "Move out of this Accounts Center", "Move your Instagram profile")
                if (lower.includes("move out of this meta account") || lower.includes("move out of this accounts center") || lower.includes("move your instagram profile") || lower.includes("move your instagram account")) {
                    return "STEP_1_MOVE";
                }

                // 0. Step 0: Accounts Center Manage Page
                if (window.location.href.includes("/manage") || lower.includes("manage account") || lower.includes("manage accounts") || lower.includes("control which profiles") || lower.includes("control which accounts")) {
                    return "STEP_0_MANAGE";
                }

                // --- NEW EXTENDED DIRECT URL CONTACT INFO STEPS ---

                // Meta Horizon: "Allow and agree" screen (Replaces steps 9 & 10 for Horizon accounts!)
                var horizonAllowBtn = Array.from(document.querySelectorAll("button, div[role='button'], a, [type='submit']")).find(function(el) {
                    if (!isVisible(el)) return false;
                    var txt = (el.innerText || el.textContent || el.getAttribute('aria-label') || "").trim().toLowerCase();
                    return (txt.includes("allow") && txt.includes("agree")) || txt.includes("allow and agree") || txt.includes("allow & agree");
                });
                if (horizonAllowBtn || (lower.includes("horizon") && (lower.includes("allow and agree") || lower.includes("agree and continue")))) {
                    return "STEP_HORIZON_ALLOW_AND_AGREE";
                }

                // 12. Confirm OTP Screen (Screenshot 4)
                var otpInput = document.querySelector("input[name*='code'], input[placeholder*='code'], input[inputmode='numeric']");
                var hasVisibleOtpInput = otpInput && isVisible(otpInput);
                if (hasVisibleOtpInput || lower.includes("confirmation code") || lower.includes("enter your confirmation code")) {
                    return "STEP_12_CONFIRM_OTP_CODE";
                }

                // 11. Add Email Input screen (Input temp email) (Screenshot 3)
                var allInputs = Array.from(document.querySelectorAll("input[type='text'], input[type='email'], input[name*='email'], input[placeholder*='email']")).filter(isVisible);
                var visibleEmailInput = allInputs.find(function(inp) {
                    var p = (inp.placeholder || inp.getAttribute('aria-label') || "").toLowerCase();
                    var n = (inp.name || "").toLowerCase();
                    return p.includes("email") || n.includes("email") || inp.type === "email" || p.includes("enter");
                }) || (allInputs.length === 1 && !hasVisiblePasswordInput ? allInputs[0] : null);

                if (visibleEmailInput && (lower.includes("add email") || lower.includes("enter email") || lower.includes("email address") || document.querySelector("input[type='checkbox'], [role='checkbox']"))) {
                    return "STEP_11_INPUT_TEMP_EMAIL";
                }

                // 10. Add Email/Mobile bottom popup menu: "Add email" or "Add mobile" option is present on screen WITHOUT text input
                var hasVisibleEmailInput = !!visibleEmailInput;
                var hasBottomActionSheet = !hasVisibleEmailInput && (
                    lower.includes("add mobile") ||
                    lower.includes("add mobile number") ||
                    Array.from(document.querySelectorAll("div[role='button'], button, a, li, [tabindex='0'], span, p")).some(function(el) {
                        if (!isVisible(el)) return false;
                        var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                        return (txt === "add email" || txt === "add email address" || txt === "add mobile" || txt === "add mobile number" ||
                               (txt.includes("add email") && !txt.includes("contact info") && txt.length < 35));
                    })
                );

                if (hasBottomActionSheet) {
                    return "STEP_10_CLICK_ADD_EMAIL";
                }

                // 9. Contact Info Add New screen (Screenshot 1)
                var hasAddNewContactButton = Array.from(document.querySelectorAll("div[role='button'], button, a, [tabindex='0'], span, p")).some(function(el) {
                    if (!isVisible(el)) return false;
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    return (txt === "add new contact info" || txt === "add new contact" || txt === "add contact" ||
                           (txt.includes("add new contact") && !txt.includes("mobile") && !txt.includes("email")));
                });
                if (!hasBottomActionSheet && (hasAddNewContactButton || lower.includes("contact info") || lower.includes("contact information"))) {
                    return "STEP_9_CLICK_ADD_NEW_CONTACT";
                }

                return "UNKNOWN";
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            val clean = res.replace("\"", "").trim()
            callback(clean)
        }
    }

    fun injectMetaClickContinueWithInstagram(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < window.innerHeight;
                }

                var allBtns = Array.from(document.querySelectorAll("button, div[role='button'], a, [type='submit'], [tabindex='0'], div, span")).filter(isVisible);
                var candidates = allBtns.filter(function(b) {
                    var txt = (b.innerText || b.textContent || b.getAttribute('aria-label') || "").trim().toLowerCase();
                    var isMatch = txt.includes("continue with instagram") || (txt.includes("instagram") && (txt.includes("continue") || txt.includes("log in")));
                    if (!isMatch) return false;
                    var r = b.getBoundingClientRect();
                    return r.height > 0 && r.height < 180;
                });

                candidates.sort(function(a, b) {
                    var rA = a.getBoundingClientRect();
                    var rB = b.getBoundingClientRect();
                    return (rA.width * rA.height) - (rB.width * rB.height);
                });

                var targetBtn = candidates[0];

                if (targetBtn) {
                    var clickable = targetBtn;
                    var parentButton = targetBtn.closest("button, div[role='button'], a, [type='submit']");
                    if (parentButton && parentButton.getBoundingClientRect().height < 200) {
                        clickable = parentButton;
                    }
                    simulateTapSync(clickable);
                    return 'true';
                }
                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaClickAllowAndContinue(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < window.innerHeight;
                }

                var allBtns = Array.from(document.querySelectorAll("button, div[role='button'], a, [type='submit'], [tabindex='0'], div, span")).filter(isVisible);
                var candidates = allBtns.filter(function(b) {
                    var txt = (b.innerText || b.textContent || b.getAttribute('aria-label') || "").trim().toLowerCase();
                    var isMatch = (txt.includes("allow") && txt.includes("continue")) || txt === "allow and continue";
                    if (!isMatch) return false;
                    var r = b.getBoundingClientRect();
                    return r.height > 0 && r.height < 180;
                });

                candidates.sort(function(a, b) {
                    var rA = a.getBoundingClientRect();
                    var rB = b.getBoundingClientRect();
                    return (rA.width * rA.height) - (rB.width * rB.height);
                });

                var targetBtn = candidates[0];

                if (targetBtn) {
                    var clickable = targetBtn;
                    var parentButton = targetBtn.closest("button, div[role='button'], a, [type='submit']");
                    if (parentButton && parentButton.getBoundingClientRect().height < 200) {
                        clickable = parentButton;
                    }
                    simulateTapSync(clickable);
                    return 'true';
                }
                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaClickContinueAsAccountCard(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < window.innerHeight;
                }

                // 1. Find title text element starting with "continue as"
                var allEls = Array.from(document.querySelectorAll("div, span, p, h1, h2, h3, h4, button, div[role='button'], a, [tabindex='0']")).filter(isVisible);
                var titleEl = allEls.find(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    if (txt.includes("log in to another account") && txt.length > 50) return false;
                    return txt.startsWith("continue as") && txt.length < 60;
                });

                if (titleEl) {
                    var card = titleEl.closest("button, div[role='button'], a, [tabindex='0']") || titleEl;
                    simulateTapSync(titleEl);
                    try { card.click(); } catch(e){}
                    return 'true';
                }

                // 2. Fallback candidate search
                var candidates = allEls.filter(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    if (!txt.includes("continue as")) return false;
                    var r = el.getBoundingClientRect();
                    return r.height > 0 && r.height < 180 && r.width > 50;
                });

                candidates.sort(function(a, b) {
                    var rA = a.getBoundingClientRect();
                    var rB = b.getBoundingClientRect();
                    return (rA.width * rA.height) - (rB.width * rB.height);
                });

                var targetCard = candidates[0];
                if (targetCard) {
                    simulateTapSync(targetCard);
                    var p = targetCard.closest("button, div[role='button'], a, [tabindex='0']");
                    if (p) try { p.click(); } catch(e){}
                    return 'true';
                }

                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaClickAllowAndAgree(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < window.innerHeight;
                }

                var allBtns = Array.from(document.querySelectorAll("button, div[role='button'], a, [type='submit'], [tabindex='0']")).filter(isVisible);
                var targetBtn = allBtns.find(function(b) {
                    var txt = (b.innerText || b.textContent || b.getAttribute('aria-label') || "").trim().toLowerCase();
                    return (txt.includes("allow") && txt.includes("agree")) || txt.includes("allow and agree") || txt.includes("allow & agree");
                });

                if (!targetBtn) {
                    targetBtn = allBtns.find(function(b) {
                        var txt = (b.innerText || b.textContent || "").trim().toLowerCase();
                        return txt.includes("agree") && (txt.includes("allow") || txt.includes("horizon"));
                    });
                }

                if (targetBtn) {
                    var clickable = targetBtn.closest("div[role='button'], button, a") || targetBtn;
                    simulateTapSync(clickable);
                    return 'true';
                }
                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaClickFinishMovingButton(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < window.innerHeight;
                }

                function isCancelOrBack(el) {
                    if (!el) return false;
                    var txt = (el.innerText || el.textContent || el.value || el.getAttribute('aria-label') || "").trim().toLowerCase();
                    return txt.includes("cancel") || txt.includes("back") || txt.includes("close") || 
                           txt.includes("বাতিল") || txt.includes("dismiss") || txt.includes("not now") ||
                           txt === "না" || txt.includes("leave");
                }

                // Step A: Check if there is a Radio / Checkbox option for "Yes, finish moving" / "Move account"
                var allSelectables = Array.from(document.querySelectorAll("div[role='radio'], div[role='checkbox'], input[type='radio'], input[type='checkbox'], [role='option'], div.x1i10hfl, div[class*='x1i10hfl']")).filter(isVisible);
                
                var optionCard = allSelectables.find(function(el) {
                    if (isCancelOrBack(el)) return false;
                    var txt = (el.innerText || el.textContent || el.value || el.getAttribute('aria-label') || "").trim().toLowerCase();
                    if (txt.includes("?")) return false;
                    return (txt.includes("yes") && (txt.includes("finish") || txt.includes("move"))) ||
                           (txt.includes("finish") && txt.includes("moving")) ||
                           txt.includes("yes, finish moving") ||
                           txt.includes("yes finish moving") ||
                           txt.includes("yes, move account");
                });

                if (optionCard) {
                    var isChecked = optionCard.getAttribute("aria-checked") === "true" || optionCard.checked === true;
                    if (!isChecked) {
                        simulateTapSync(optionCard);
                        try { optionCard.click(); } catch(e){}
                    }
                }

                // Step B: Find the Action Button to Finish Moving
                var allClickables = Array.from(document.querySelectorAll("button, div[role='button'], a[role='button'], a, input[type='submit'], [tabindex='0'], div.x1i10hfl, div.xjbqb8w")).filter(isVisible);

                // Filter out ANY element containing cancel/back/close/dismiss/?/re-enter
                var validCandidates = allClickables.filter(function(b) {
                    if (isCancelOrBack(b)) return false;
                    var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                    if (!txt || txt.includes("?") || txt.includes("re-enter")) return false;

                    // Must not be a giant wrapper containing other buttons
                    var childBtns = b.querySelectorAll("button, [role='button']");
                    if (childBtns.length > 1) return false;

                    return true;
                });

                // 1. Primary Priority: Exact text matching "yes" and "finish", or "finish moving", or "yes, finish moving"
                var targetBtn = validCandidates.find(function(b) {
                    var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                    return (txt.includes("yes") && txt.includes("finish")) ||
                           (txt.includes("finish") && txt.includes("moving")) ||
                           txt.includes("yes, finish moving") ||
                           txt.includes("yes finish moving") ||
                           txt.includes("yes, move account") ||
                           txt.includes("সরানো শেষ") ||
                           txt.includes("মুভ শেষ");
                });

                // 2. Secondary Priority: Contains "finish" or "move" or "yes"
                if (!targetBtn) {
                    targetBtn = validCandidates.find(function(b) {
                        var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                        return txt === "finish" || txt.startsWith("finish") ||
                               txt === "move" || txt.startsWith("move") ||
                               (txt.includes("move") && !txt.includes("manage") && !txt.includes("profile"));
                    });
                }

                // 3. Tertiary Priority: If optionCard was selected above, look for "Continue" / "Done" / "Submit" button
                if (!targetBtn && optionCard) {
                    targetBtn = validCandidates.find(function(b) {
                        var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                        return txt === "continue" || txt === "next" || txt === "done" || txt === "submit" || txt === "চালিয়ে যান";
                    });
                }

                // 4. Fallback search inside dialog: Find the non-cancel button in the dialog footer
                if (!targetBtn) {
                    var dialog = document.querySelector("div[role='dialog'], div[role='main']") || document.body;
                    var dialogBtns = Array.from(dialog.querySelectorAll("button, div[role='button'], a[role='button']")).filter(isVisible);
                    var nonCancelBtns = dialogBtns.filter(function(b) {
                        if (isCancelOrBack(b)) return false;
                        var txt = (b.innerText || b.textContent || b.getAttribute('aria-label') || "").trim().toLowerCase();
                        return !txt.includes("?") && txt.length > 0 && txt.length < 50;
                    });
                    if (nonCancelBtns.length > 0) {
                        targetBtn = nonCancelBtns[0];
                    }
                }

                // FINAL SAFETY CHECK: ABSOLUTELY ENSURE targetBtn IS NOT CANCEL!
                if (targetBtn && !isCancelOrBack(targetBtn)) {
                    var clickable = targetBtn.closest("button, div[role='button'], a") || targetBtn;
                    if (isCancelOrBack(clickable)) {
                        clickable = targetBtn;
                    }

                    try { clickable.scrollIntoView({ behavior: 'instant', block: 'center' }); } catch(e){}
                    try { clickable.focus(); } catch(e){}

                    simulateTapSync(clickable);
                    try { clickable.click(); } catch(e){}
                    if (clickable !== targetBtn) {
                        try { targetBtn.click(); } catch(e){}
                    }

                    try {
                        var form = clickable.closest("form");
                        if (form && typeof form.requestSubmit === 'function') {
                            form.requestSubmit(clickable);
                        }
                    } catch(e){}

                    return 'true';
                }

                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaMoveAccountStep3(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < window.innerHeight;
                }

                // Query all potential button elements
                var allBtns = Array.from(document.querySelectorAll("button, div[role='button'], a, div.x1i10hfl, div.xjbqb8w, [tabindex='0'], span")).filter(isVisible);
                
                // 1. Find candidates strictly on the RIGHT side of the screen (> 40% screen width)
                var candidates = allBtns.filter(function(b) {
                    var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                    if (!txt || txt.includes("cancel") || txt.includes("back") || txt.includes("re-enter")) return false;
                    
                    var r = b.getBoundingClientRect();
                    // Must be compact button size (not giant wrapper)
                    if (r.width > window.innerWidth * 0.7 || r.height > 120) return false;
                    
                    var centerX = r.left + r.width / 2;
                    // MUST be on the right side of screen (X > 40% screen width)
                    var isRightSide = centerX > (window.innerWidth * 0.4);
                    
                    return isRightSide && (txt === "continue" || txt.startsWith("continue") || txt.includes("continue"));
                });

                // Sort candidates: rightmost button first (highest centerX)
                candidates.sort(function(a, b) {
                    var rA = a.getBoundingClientRect();
                    var rB = b.getBoundingClientRect();
                    var cA = rA.left + rA.width / 2;
                    var cB = rB.left + rB.width / 2;
                    return cB - cA; // Highest X first (rightmost!)
                });

                var targetBtn = candidates[0];

                if (!targetBtn) {
                    // Fallback search: any element containing "continue" on right half
                    targetBtn = allBtns.find(function(b) {
                        var txt = (b.innerText || b.textContent || "").trim().toLowerCase();
                        if (txt.includes("cancel") || txt.includes("back")) return false;
                        var r = b.getBoundingClientRect();
                        return (r.left + r.width / 2) > (window.innerWidth * 0.4) && (txt.includes("continue"));
                    });
                }

                if (targetBtn) {
                    // Only use closest if parent is small (< 50% screen width) and stays on right side
                    var clickable = targetBtn;
                    var p = targetBtn.closest("button, div[role='button'], a");
                    if (p) {
                        var pR = p.getBoundingClientRect();
                        if (pR.width < window.innerWidth * 0.6 && (pR.left + pR.width / 2) > window.innerWidth * 0.4) {
                            clickable = p;
                        }
                    }
                    simulateTapSync(clickable);
                    try { clickable.click(); } catch(e){}
                    if (clickable !== targetBtn) {
                        try { targetBtn.click(); } catch(e){}
                    }
                    return 'true';
                }

                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaClickCreatePassword(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function isVisible(el) {
                    if (!el) return false;
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < window.innerHeight;
                }

                var allBtns = Array.from(document.querySelectorAll("div.x1i10hfl.xjbqb8w, div.x1i10hfl, button, div[role='button'], a, [tabindex='0'], input[type='submit']")).filter(isVisible);

                // 1. Filter out headings/paragraphs and keep only actual bottom button
                var candidates = allBtns.filter(function(b) {
                    var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                    if (!txt) return false;
                    
                    // Reject headings & body text containing "you need", "once you", etc.
                    if (txt.includes("you need") || txt.includes("once you") || txt.length > 35) return false;
                    
                    var r = b.getBoundingClientRect();
                    // Must be in lower screen area (top > 150) and compact button size
                    var isLowerScreen = r.top > 150 || (r.top + r.height / 2) > (window.innerHeight * 0.4);
                    if (!isLowerScreen || r.height > 140) return false;
                    
                    return txt === "create password" || txt.startsWith("create password") || txt.includes("create password");
                });

                // Sort candidates: lowest screen position first (highest centerY = bottom button!)
                candidates.sort(function(a, b) {
                    var rA = a.getBoundingClientRect();
                    var rB = b.getBoundingClientRect();
                    var cYA = rA.top + rA.height / 2;
                    var cYB = rB.top + rB.height / 2;
                    return cYB - cYA; // Bottom-most button first!
                });

                var targetBtn = candidates[0];

                if (!targetBtn) {
                    // Fallback search: any element containing "create password" strictly below y > 200
                    targetBtn = allBtns.find(function(b) {
                        var txt = (b.innerText || b.textContent || "").trim().toLowerCase();
                        if (txt.includes("you need") || txt.length > 35) return false;
                        var r = b.getBoundingClientRect();
                        return r.top > 200 && r.height < 140 && txt.includes("create password");
                    });
                }

                if (targetBtn) {
                    var clickable = targetBtn;
                    var p = targetBtn.closest("button, div[role='button'], a, div.x1i10hfl");
                    if (p) {
                        var pR = p.getBoundingClientRect();
                        if (pR.height < 140 && pR.top > 150) {
                            clickable = p;
                        }
                    }
                    simulateTapSync(clickable);
                    try { clickable.click(); } catch(e){}
                    if (clickable !== targetBtn) {
                        try { targetBtn.click(); } catch(e){}
                    }
                    return 'true';
                }

                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaSetupManualPasswordAutoSubmit(callback: (String) -> Unit = {}) {
        val script = """
            (function() {
                $jsClickHelpers

                function disableRecommendations(el) {
                    if (!el) return;
                    try {
                        el.setAttribute('autocomplete', 'new-password');
                        el.setAttribute('data-lpignore', 'true');
                        el.setAttribute('data-form-type', 'other');
                        el.setAttribute('autocapitalize', 'off');
                        el.setAttribute('autocorrect', 'off');
                        el.setAttribute('spellcheck', 'false');
                        el.setAttribute('aria-autocomplete', 'none');
                        el.setAttribute('data-google-ignore', 'true');
                        if (el.form) {
                            el.form.setAttribute('autocomplete', 'off');
                        }
                    } catch(e) {}
                }

                // Strip Google credential recommendations from all forms and inputs
                try {
                    var allInputs = document.querySelectorAll("input, select, textarea, form");
                    allInputs.forEach(function(i) {
                        i.setAttribute('autocomplete', 'off');
                        i.setAttribute('data-lpignore', 'true');
                    });
                    var googleCredPopups = document.querySelectorAll("[data-google-credential], [id*='credential'], [class*='credential']");
                    googleCredPopups.forEach(function(p) { p.remove(); });
                } catch(e) {}

                var pwdInput = document.querySelector("input[type='password'], input[name*='pass' i], input[placeholder*='pass' i]");
                if (!pwdInput) {
                    var inputs = Array.from(document.querySelectorAll("input:not([type='hidden'])"));
                    pwdInput = inputs.find(function(i) {
                        var p = (i.placeholder || "").toLowerCase();
                        var a = (i.getAttribute('aria-label') || "").toLowerCase();
                        var n = (i.name || "").toLowerCase();
                        return p.includes("password") || a.includes("password") || n.includes("password");
                    });
                }

                if (pwdInput) {
                    disableRecommendations(pwdInput);
                    try {
                        pwdInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        pwdInput.focus();
                    } catch(e) {}

                    if (!window.__pwd_auto_submit_initialized) {
                        window.__pwd_auto_submit_initialized = true;
                        window.__manual_pwd_submitted = false;
                        var submitDebounceTimer = null;

                        function doSubmit() {
                            if (window.__manual_pwd_submitted) return;
                            var currentVal = (pwdInput.value || "").trim();
                            if (currentVal.length < 6) return;

                            var allElements = Array.from(document.querySelectorAll("button, [role='button'], div[role='button'], input[type='submit'], a, span"));
                            var submitBtn = allElements.find(function(b) {
                                var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                                if (txt.includes("cancel") || txt.includes("back") || txt.includes("show") || txt.includes("hide")) return false;
                                var r = b.getBoundingClientRect();
                                return (txt === "continue" || txt === "next" || txt === "submit" || txt.startsWith("continue") || txt === "চালিয়ে যান" || txt === "পরবর্তী") &&
                                       r.width > 20 && r.height > 15;
                            });

                            if (!submitBtn) {
                                var form = pwdInput.closest("form");
                                if (form) {
                                    var formBtns = Array.from(form.querySelectorAll("button[type='submit'], button, [role='button']"));
                                    submitBtn = formBtns.find(function(b) {
                                        var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                                        return !txt.includes("cancel") && !txt.includes("back") && !txt.includes("close") && !txt.includes("বাতিল") && !txt.includes("dismiss");
                                    });
                                }
                            }

                            if (!submitBtn) {
                                var container = pwdInput.closest("div[role='dialog'], div[role='main'], div");
                                if (container) {
                                    var btns = Array.from(container.querySelectorAll("button, [role='button']"));
                                    submitBtn = btns.find(function(b) {
                                        var txt = (b.innerText || b.textContent || "").trim().toLowerCase();
                                        return !txt.includes("cancel") && !txt.includes("back") && (txt.includes("continue") || txt.includes("next") || txt.includes("submit"));
                                    });
                                }
                            }

                            if (submitBtn) {
                                window.__manual_pwd_submitted = true;
                                try {
                                    var rect = submitBtn.getBoundingClientRect();
                                    showVisualClickIndicator(rect.left + rect.width / 2, rect.top + rect.height / 2);
                                } catch(e) {}
                                simulateTap(submitBtn);
                                try { submitBtn.click(); } catch(e) {}
                                try {
                                    var parentForm = submitBtn.closest("form");
                                    if (parentForm) parentForm.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                                } catch(e) {}
                            }
                        }

                        function onInputReceived() {
                            var currentVal = (pwdInput.value || "").trim();
                            // As soon as user enters password (at least 6 chars)
                            if (currentVal.length >= 6 && !window.__manual_pwd_submitted) {
                                if (submitDebounceTimer) clearTimeout(submitDebounceTimer);
                                submitDebounceTimer = setTimeout(function() {
                                    doSubmit();
                                }, 800); // 800ms debounce: once user finishes typing without clicking anything
                            }
                        }

                        pwdInput.addEventListener('input', onInputReceived);
                        pwdInput.addEventListener('keyup', function(e) {
                            if (e.key === 'Enter' || e.keyCode === 13) {
                                doSubmit();
                            } else {
                                onInputReceived();
                            }
                        });
                        pwdInput.addEventListener('keydown', function(e) {
                            if (e.key === 'Enter' || e.keyCode === 13) {
                                doSubmit();
                            }
                        });
                        pwdInput.addEventListener('change', onInputReceived);
                        pwdInput.addEventListener('blur', function() {
                            var currentVal = (pwdInput.value || "").trim();
                            if (currentVal.length >= 6) {
                                doSubmit();
                            }
                        });
                        pwdInput.addEventListener('paste', function() {
                            setTimeout(onInputReceived, 200);
                        });
                    }
                    return window.__manual_pwd_submitted ? "SUBMITTED" : "LISTENING";
                }
                return "NOT_FOUND";
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            callback(res.trim().replace("\"", ""))
        }
    }

    fun isManualPasswordSubmitted(callback: (Boolean) -> Unit) {
        val script = "(function() { return (window.__manual_pwd_submitted === true) ? 'true' : 'false'; })();"
        evaluateJsWithCallback(script) { res ->
            callback(res.contains("true"))
        }
    }

    fun injectMetaMoveAccountStep4(password: String, onComplete: (Boolean) -> Unit = {}) {
        val cleanPassword = password.trim()
        val script = """
            (function() {
                $jsClickHelpers
                var passwordValue = "$cleanPassword";

                var pwdInput = document.querySelector("input[type='password'], input[name*='pass' i], input[placeholder*='pass' i], input[autocomplete='new-password'], input[autocomplete='current-password']");
                if (!pwdInput) {
                    var allInputs = Array.from(document.querySelectorAll("input:not([type='hidden'])"));
                    pwdInput = allInputs.find(function(i) {
                        var p = (i.placeholder || "").toLowerCase();
                        var a = (i.getAttribute('aria-label') || "").toLowerCase();
                        var n = (i.name || "").toLowerCase();
                        return p.includes("password") || a.includes("password") || n.includes("password");
                    });
                }

                if (pwdInput) {
                    try {
                        var rect = pwdInput.getBoundingClientRect();
                        showVisualClickIndicator(rect.left + rect.width / 2, rect.top + rect.height / 2);
                        pwdInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        pwdInput.focus();
                        pwdInput.click();

                        if (pwdInput._valueTracker) {
                            try { pwdInput._valueTracker.setValue(''); } catch(e) {}
                        }

                        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
                        if (nativeSetter && nativeSetter.set) {
                            nativeSetter.set.call(pwdInput, passwordValue);
                        } else {
                            pwdInput.value = passwordValue;
                        }

                        try { pwdInput.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, data: passwordValue })); } catch(e) {}
                        try { pwdInput.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, data: passwordValue })); } catch(e) {}
                        pwdInput.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                        pwdInput.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                        pwdInput.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true }));
                        pwdInput.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, cancelable: true }));
                        pwdInput.dispatchEvent(new Event('blur', { bubbles: true }));

                        // Human pause (600ms) before clicking Continue button
                        setTimeout(function() {
                            var allElements = Array.from(document.querySelectorAll("button, [role='button'], div[role='button'], input[type='submit'], a, span, div"));
                            var submitBtn = allElements.find(function(b) {
                                var txt = (b.innerText || b.textContent || b.value || "").trim().toLowerCase();
                                if (txt.includes("cancel") || txt.includes("back")) return false;
                                var r = b.getBoundingClientRect();
                                return (txt === "continue" || txt === "next" || txt === "submit" || txt.startsWith("continue")) &&
                                       r.width > 0 && (r.left + r.width / 2) > window.innerWidth * 0.45;
                            });

                            if (!submitBtn) {
                                submitBtn = allElements.find(function(b) {
                                    var txt = (b.innerText || b.textContent || b.value || "").trim().toLowerCase();
                                    if (txt.includes("cancel") || txt.includes("back")) return false;
                                    return txt === "continue" || txt === "next" || txt === "submit" || txt.startsWith("continue");
                                });
                            }

                            if (submitBtn) {
                                simulateTap(submitBtn);
                            }
                        }, 600);

                        return 'true';
                    } catch(e) {
                        return 'error: ' + e.message;
                    }
                }
                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun injectMetaFindAndClickInstagramRemoveLink(onComplete: (Boolean) -> Unit = {}) {
        val script = """
            (function() {
                function showVisualClickIndicator(x, y) {
                    try {
                        var marker = document.createElement('div');
                        marker.style.position = 'fixed';
                        marker.style.left = (x - 22) + 'px';
                        marker.style.top = (y - 22) + 'px';
                        marker.style.width = '44px';
                        marker.style.height = '44px';
                        marker.style.borderRadius = '50%';
                        marker.style.border = '4px solid #00E5FF';
                        marker.style.backgroundColor = 'rgba(0, 229, 255, 0.45)';
                        marker.style.boxShadow = '0 0 20px #00E5FF';
                        marker.style.pointerEvents = 'none';
                        marker.style.zIndex = '9999999';
                        marker.style.transition = 'transform 0.8s ease-out, opacity 0.8s ease-out';
                        marker.style.transform = 'scale(0.3)';
                        marker.style.opacity = '1';
                        
                        document.body.appendChild(marker);
                        marker.offsetHeight;
                        marker.style.transform = 'scale(2.5)';
                        marker.style.opacity = '0';
                        setTimeout(function() {
                            try { document.body.removeChild(marker); } catch(e){}
                        }, 1000);
                    } catch(e){}
                }

                function simulateTap(el) {
                    if (!el) return;
                    try {
                        var rect = el.getBoundingClientRect();
                        var x = rect.left + rect.width / 2;
                        var y = rect.top + rect.height / 2;
                        showVisualClickIndicator(x, y);

                        var originalOutline = el.style.outline;
                        el.style.outline = "4px solid #00E5FF";
                        el.style.outlineOffset = "2px";
                        setTimeout(function() {
                            try { el.style.outline = originalOutline; } catch(e){}
                        }, 2000);

                        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        el.focus();
                        el.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, cancelable: true }));
                        el.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true }));
                        el.click();
                        el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                    } catch(e) {}
                }

                var allLinks = Array.from(document.querySelectorAll("a, div[role='button'], button, li, div"));
                var target = allLinks.find(function(el) {
                    var href = (el.getAttribute('href') || "").toLowerCase();
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    return href.includes("/remove/") ||
                           (href.includes("accounttype=instagram") && href.includes("manage")) ||
                           (txt.includes("instagram") && (txt.includes("move") || txt.includes("manage") || txt.includes("remove")));
                });

                if (target) {
                    var clickable = target.closest("a, div[role='button'], button") || target;
                    simulateTap(clickable);
                    return 'true';
                }
                return 'false';
            })();
        """.trimIndent()
        evaluateJsWithCallback(script) { res ->
            onComplete(res.contains("true"))
        }
    }

    fun computeTotp(base32Key: String): String {
        try {
            val cleanKey = base32Key.replace(" ", "").trim().uppercase()
            val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            var bits = ""
            for (c in cleanKey) {
                val valIndex = base32Chars.indexOf(c)
                if (valIndex != -1) {
                    bits += valIndex.toString(2).padStart(5, '0')
                }
            }
            val bytes = ByteArray(bits.length / 8)
            for (i in bytes.indices) {
                bytes[i] = bits.substring(i * 8, i * 8 + 8).toInt(2).toByte()
            }

            val time = System.currentTimeMillis() / 1000 / 30
            val timeBytes = ByteBuffer.allocate(8).putLong(time).array()

            val mac = Mac.getInstance("HmacSHA1")
            val keySpec = SecretKeySpec(bytes, "HmacSHA1")
            mac.init(keySpec)
            val hash = mac.doFinal(timeBytes)

            val offset = hash[hash.size - 1].toInt() and 0x0F
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            val otp = binary % 1000000
            return otp.toString().padStart(6, '0')
        } catch (e: Exception) {
            return ""
        }
    }

    fun injectTwoFaAutomationTick(callback: (JSONObject) -> Unit) {
        val script = """
            (async function() {
                $jsClickHelpers

                function cleanClick(el) {
                    if (!el) return;
                    var target = el.closest('button, [role="button"], a, input[type="radio"], input[type="checkbox"], [tabindex]') || el;
                    try {
                        var rect = target.getBoundingClientRect();
                        if (rect.width > 0 && rect.height > 0) {
                            showVisualClickIndicator(rect.left + rect.width / 2, rect.top + rect.height / 2);
                        }
                    } catch(e) {}
                    ['mousedown', 'mouseup', 'click'].forEach(function(type) {
                        try { target.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, view: window })); } catch(e){}
                    });
                    try { target.click(); } catch(e) {}
                }

                function base32ToUint8Array(base32) {
                    const base32chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
                    let bits = "";
                    let cleanBase32 = (base32 || '').replace(/[\s\n\r=\-_:]/g, '').toUpperCase();
                    for (let i = 0; i < cleanBase32.length; i++) {
                        let val = base32chars.indexOf(cleanBase32.charAt(i));
                        if (val === -1) continue;
                        bits += val.toString(2).padStart(5, '0');
                    }
                    let bytes = new Uint8Array(Math.floor(bits.length / 8));
                    for (let i = 0; i < bytes.length; i++) {
                        bytes[i] = parseInt(bits.substring(i * 8, i * 8 + 8), 2);
                    }
                    return bytes;
                }

                async function generateTOTP(secret) {
                    try {
                        let keyBytes = base32ToUint8Array(secret);
                        if (!keyBytes || keyBytes.length === 0) return null;
                        let epoch = Math.floor(Date.now() / 1000);
                        let time = Math.floor(epoch / 30);
                        let timeBuffer = new ArrayBuffer(8);
                        let timeView = new DataView(timeBuffer);
                        timeView.setUint32(4, time, false);

                        let cryptoKey = await window.crypto.subtle.importKey(
                            "raw", keyBytes, { name: "HMAC", hash: { name: "SHA-1" } }, false, ["sign"]
                        );
                        let signature = await window.crypto.subtle.sign("HMAC", cryptoKey, timeBuffer);
                        let bytes = new Uint8Array(signature);
                        let offset = bytes[bytes.length - 1] & 0x0f;
                        
                        let binary = ((bytes[offset] & 0x7f) << 24) |
                                     ((bytes[offset + 1] & 0xff) << 16) |
                                     ((bytes[offset + 2] & 0xff) << 8) |
                                     (bytes[offset + 3] & 0xff);
                        
                        let otp = binary % 1000000;
                        return String(otp).padStart(6, '0');
                    } catch (e) {
                        return null;
                    }
                }

                function extractUsername() {
                    let allEls = document.querySelectorAll('div, span, p, h1, h2, h3, [role="heading"]');
                    for (let el of allEls) {
                        let txt = el.innerText || el.textContent || '';
                        if (txt.includes('• Instagram') || txt.includes('•Instagram') || txt.includes('Instagram •')) {
                            let parts = txt.split('•');
                            let uname = parts[0].trim();
                            if (uname.toLowerCase() === 'instagram' && parts.length > 1) {
                                uname = parts[1].trim();
                            }
                            let cleanParts = uname.split('\n');
                            uname = cleanParts[cleanParts.length - 1].trim(); 
                            uname = uname.replace(/^title\s*/i, '').replace(/^@/, '').trim(); 
                            if (uname.length > 1 && uname.length < 35 && !uname.toLowerCase().includes('meta')) return uname;
                        }
                    }
                    var stored = localStorage.getItem('ig_master_user');
                    if (stored && stored.length > 1) return stored;
                    return "Instagram_User";
                }

                function setReactValue(input, value) {
                    if (!input) return;
                    try { input.focus(); } catch(e) {}
                    let nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value");
                    if (nativeSetter && nativeSetter.set) {
                        nativeSetter.set.call(input, value);
                    } else {
                        input.value = value;
                    }
                    ['input', 'change', 'blur'].forEach(function(evtName) {
                        try { input.dispatchEvent(new Event(evtName, { bubbles: true })); } catch(e) {}
                    });
                }

                var bodyText = document.body ? (document.body.innerText || document.body.textContent || '') : '';
                var lowerBody = bodyText.toLowerCase();

                // Stage 5: Two-factor authentication is on
                if (lowerBody.indexOf('two-factor authentication is on') > -1 || 
                    lowerBody.indexOf('two-factor authentication is turned on') > -1 ||
                    lowerBody.indexOf("you've turned on two-factor authentication") > -1 ||
                    lowerBody.indexOf('authentication app is on') > -1) {
                    var user = localStorage.getItem('ig_master_user') || extractUsername();
                    var key = localStorage.getItem('ig_master_key') || "";
                    
                    // Click Done if visible
                    var allButtons = document.querySelectorAll('button, [role="button"], div, span');
                    for (var d = 0; d < allButtons.length; d++) {
                        var dTxt = (allButtons[d].innerText || allButtons[d].textContent || '').trim().toLowerCase();
                        if (dTxt === 'done' || dTxt === 'finish' || dTxt === 'close') {
                            cleanClick(allButtons[d]);
                            break;
                        }
                    }
                    return JSON.stringify({ state: "2FA_CONFIRMED_ON", user: user, key: key });
                }

                // Stage 4: Input OTP code and click Next
                if (lowerBody.indexOf('get your code from your authentication app') > -1 || 
                    (lowerBody.indexOf('enter code') > -1 && lowerBody.indexOf('digit') > -1) || 
                    lowerBody.indexOf('enter the 6-digit code') > -1 ||
                    lowerBody.indexOf('enter 6-digit code') > -1 ||
                    lowerBody.indexOf('confirmation code') > -1) {
                    
                    var finalKey = localStorage.getItem('ig_master_key');
                    if (finalKey) {
                        var totpCode = await generateTOTP(finalKey);
                        if (totpCode && totpCode.length === 6) {
                            var allInputs = Array.from(document.querySelectorAll('input:not([type="hidden"]):not([type="password"])'))
                                .filter(el => el.offsetWidth > 0 && el.offsetHeight > 0);
                            
                            if (allInputs.length === 6) {
                                // 6 separate 1-digit boxes
                                for (var i = 0; i < 6; i++) {
                                    setReactValue(allInputs[i], totpCode.charAt(i));
                                }
                            } else if (allInputs.length >= 1) {
                                setReactValue(allInputs[0], totpCode);
                            }

                            await new Promise(r => setTimeout(r, 400));
                            var nextBtns = document.querySelectorAll('button, [role="button"], div, span');
                            for (var n = nextBtns.length - 1; n >= 0; n--) {
                                var nTxt = (nextBtns[n].innerText || nextBtns[n].textContent || '').trim().toLowerCase();
                                if (nTxt === 'next' || nTxt === 'continue' || nTxt === 'submit' || nTxt === 'confirm') {
                                    cleanClick(nextBtns[n]);
                                    break;
                                }
                            }
                            return JSON.stringify({ state: "SUBMITTING_TOTP", code: totpCode, key: finalKey });
                        }
                    }
                    return JSON.stringify({ state: "WAITING_TOTP_FIELD" });
                }

                // Stage 3: Extract 32-character key and click Enter code
                if (lowerBody.indexOf('instructions for setup') > -1 || 
                    lowerBody.indexOf('copy key') > -1 || 
                    lowerBody.indexOf('set up on another device') > -1 ||
                    lowerBody.indexOf('enter this text code') > -1) {
                    
                    if (lowerBody.indexOf('loading...') > -1) return JSON.stringify({ state: "LOADING" });

                    var secretKey = '';
                    
                    // Strategy A: Regex search on entire text for 32 base32 characters (chunks or continuous)
                    var base32Regex = /\b([A-Z2-7]{4}[\s\-]?){8}\b/i;
                    var match = bodyText.match(base32Regex);
                    if (match) {
                        var cleaned = match[0].replace(/[\s\-]/g, '').toUpperCase();
                        if (cleaned.length === 32) {
                            secretKey = cleaned;
                        }
                    }

                    // Strategy B: Continuous 32-character Base32 string
                    if (!secretKey) {
                        var contRegex = /\b[A-Z2-7]{32}\b/i;
                        var contMatch = bodyText.match(contRegex);
                        if (contMatch) {
                            secretKey = contMatch[0].toUpperCase();
                        }
                    }

                    // Strategy C: DOM elements examination
                    if (!secretKey) {
                        var elements = document.querySelectorAll('div, span, p, code, pre, input, [data-key]');
                        for (var i = 0; i < elements.length; i++) {
                            var txt = elements[i].value || elements[i].getAttribute('data-key') || elements[i].innerText || elements[i].textContent || '';
                            var chunks = txt.trim().split(/[\s\n\r\-]+/);
                            if (chunks.length === 8) {
                                var isValid = true;
                                var combined = '';
                                for (var c of chunks) {
                                    if (c.length !== 4 || !/^[A-Z2-7]{4}$/i.test(c)) {
                                        isValid = false;
                                        break;
                                    }
                                    combined += c.toUpperCase();
                                }
                                if (isValid && combined.length === 32) {
                                    secretKey = combined;
                                    break;
                                }
                            }
                        }
                    }

                    if (secretKey) {
                        var username = extractUsername();
                        localStorage.setItem('ig_master_user', username);
                        localStorage.setItem('ig_master_key', secretKey);

                        var btns = document.querySelectorAll('button, [role="button"], div, span');
                        for (var b = btns.length - 1; b >= 0; b--) {
                            var bTxt = (btns[b].innerText || btns[b].textContent || '').trim().toLowerCase();
                            if (bTxt === 'enter code' || bTxt === 'enter your code' || bTxt === 'next' || bTxt === 'continue') {
                                cleanClick(btns[b]);
                                break;
                            }
                        }
                        return JSON.stringify({ state: "EXTRACTED_KEY", user: username, key: secretKey });
                    }
                    return JSON.stringify({ state: "SEARCHING_KEY" });
                }

                // Stage 2: Choose Authentication app and click Continue
                if (lowerBody.indexOf('help protect your account') > -1 || 
                    lowerBody.indexOf('authentication app') > -1 ||
                    lowerBody.indexOf('choose your security method') > -1) {
                    
                    var radios = document.querySelectorAll('input[type="radio"], input[type="checkbox"], [role="radio"], [role="checkbox"]');
                    for (var r = 0; r < radios.length; r++) {
                        var parentTxt = (radios[r].closest('label, div') ? radios[r].closest('label, div').innerText : '').toLowerCase();
                        if (parentTxt.includes('authentication app') || parentTxt.includes('authenticator')) {
                            cleanClick(radios[r]);
                            break;
                        }
                    }

                    var allOptionContainers = document.querySelectorAll('div[role="button"], div[tabindex], label');
                    for (var opt of allOptionContainers) {
                        var optTxt = (opt.innerText || '').toLowerCase();
                        if (optTxt.includes('authentication app') || optTxt.includes('recommended')) {
                            cleanClick(opt);
                            break;
                        }
                    }

                    var allBtns = document.querySelectorAll('button, div[role="button"], a');
                    for (var k = 0; k < allBtns.length; k++) {
                        var btnTxt = (allBtns[k].innerText || '').trim();
                        if (btnTxt.toLowerCase() === 'continue' || btnTxt.toLowerCase() === 'next') {
                            cleanClick(allBtns[k]);
                            return JSON.stringify({ state: "CLICKED_AUTH_APP_CONTINUE" });
                        }
                    }
                    return JSON.stringify({ state: "WAITING_AUTH_APP" });
                }

                // Stage 1: Choose Instagram account
                if ((lowerBody.indexOf('two-factor authentication') > -1 && lowerBody.indexOf('choose an account') > -1) ||
                    (lowerBody.indexOf('two-factor authentication') > -1 && document.querySelectorAll('div[role="button"], a').length > 0)) {
                    
                    var nodes = document.querySelectorAll('div, span, a, [role="button"]');
                    for (var m = 0; m < nodes.length; m++) {
                        var nTxt = (nodes[m].innerText || '').trim();
                        if (nTxt.toLowerCase().includes('instagram') && nodes[m].children.length === 0) {
                            var accBox = nodes[m].closest('div[role="button"], a, div[tabindex], [role="listitem"]') || nodes[m];
                            cleanClick(accBox);
                            return JSON.stringify({ state: "CHOSE_INSTAGRAM_ACCOUNT" });
                        }
                    }
                    return JSON.stringify({ state: "WAITING_CHOOSE_ACCOUNT" });
                }

                return JSON.stringify({ state: "IDLE_PAGE", url: window.location.href });
            })();
        """.trimIndent()

        evaluateJsWithCallback(script) { res ->
            try {
                val clean = res.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                val json = if (clean.startsWith("{") && clean.endsWith("}")) JSONObject(clean) else JSONObject().put("state", "RAW").put("val", clean)
                callback(json)
            } catch (e: Exception) {
                callback(JSONObject().put("state", "ERROR").put("error", e.message))
            }
        }
    }

    fun setRecordingActive(active: Boolean) {
        this.isRecording = active
        injectRecorderScript()
        evaluateJs("window.__ig_recording_active = $active;")
    }

    suspend fun executeStep(
        step: AutomationStep,
        accountEmail: String,
        sharedPassword: () -> String
    ): Result<String> = withContext(Dispatchers.Main) {
        val currentView = webView ?: return@withContext Result.failure(Exception("WebView not connected"))

        // Check if current page is Instagram security checkpoint
        val currentUrl = currentView.url ?: ""
        if (isSecurityCheckpointUrl(currentUrl)) {
            return@withContext Result.failure(SecurityException("Instagram Checkpoint/Verification screen detected"))
        }

        if (step.type == StepActionType.NAVIGATE) {
            val targetUrl = step.customValue.ifBlank { DEFAULT_INSTAGRAM_URL }
            // Only navigate if the current URL is different
            if (currentView.url != targetUrl) {
                currentView.loadUrl(targetUrl)
                return@withContext Result.success("Navigated to $targetUrl")
            } else {
                return@withContext Result.success("Already on target URL: $targetUrl")
            }
        }

        if (step.type == StepActionType.WAIT) {
            val delayTime = if (step.delayMs > 0) step.delayMs else 1500L
            delay(delayTime)
            return@withContext Result.success("Waited ${delayTime}ms")
        }

        if (step.type == StepActionType.PAUSE_USER_INPUT) {
            return@withContext Result.success("Paused for user input")
        }

        val isIAgreeStep = step.selector == "i_agree" || step.description.lowercase().contains("i agree")
        val maxAttempts = if (isIAgreeStep) 25 else (if (step.type == StepActionType.CLICK || step.type == StepActionType.TYPE) 7 else 1)
        var lastError = "Element not found: ${step.selector}"

        for (attempt in 1..maxAttempts) {
            if (attempt > 1) {
                val sleepTime = if (isIAgreeStep) 1200L else 450L
                delay(sleepTime)
            }

            val nowUrl = currentView.url ?: ""
            if (isSecurityCheckpointUrl(nowUrl)) {
                return@withContext Result.failure(SecurityException("Instagram Checkpoint/Verification screen detected"))
            }

            // --- Robustness: Detect if page reloaded unexpectedly ---
            val isPageLoadedRes = evalJsAsync("document.body !== null && document.readyState === 'complete'")
            val isPageLoaded = isPageLoadedRes == "true"
            
            // If page is reloading (body null) or not ready, just wait and continue loop without erroring
            if (!isPageLoaded) {
                delay(1000L)
                continue
            }
            // ---------------------------------------------------------

            val result = executeSingleAttempt(step, accountEmail, sharedPassword)
            if (result.isSuccess) {
                return@withContext result
            }

            lastError = result.exceptionOrNull()?.message ?: lastError

            // Special fallback for Landing Login screen on attempt 4+
            val isLandingLogin = step.selector == "log_in_landing" || step.description.contains("log in", ignoreCase = true) || step.description.contains("landing", ignoreCase = true)
            if (attempt >= 4 && isLandingLogin) {
                val directNavScript = """
                    (function() {
                        var inputs = document.querySelectorAll("input");
                        if (inputs.length > 0) return JSON.stringify({ success: true, message: "Login inputs already present" });
                        var path = window.location.pathname || "";
                        if (path !== "/accounts/login/" && path !== "/accounts/emailsignup/") {
                            window.location.href = "https://www.instagram.com/accounts/login/";
                            return JSON.stringify({ success: true, message: "Redirecting to Login form..." });
                        }
                        return JSON.stringify({ success: false });
                    })();
                """.trimIndent()
                val navRes = evalJsAsync(directNavScript)
                try {
                    val json = JSONObject(navRes)
                    if (json.optBoolean("success", false)) {
                        delay(1000L)
                        return@withContext Result.success("Navigated to Login form")
                    }
                } catch (_: Exception) {}
            }
        }

        return@withContext Result.failure(Exception(lastError))
    }

    private suspend fun executeSingleAttempt(
        step: AutomationStep,
        accountEmail: String,
        sharedPassword: () -> String
    ): Result<String> = withContext(Dispatchers.Main) {
        when (step.type) {
            StepActionType.CLICK -> {
                val selector = step.selector
                val desc = step.description.lowercase()

                // Support coordinate-based auto-clicker pin (e.g. "coord:50,42")
                if (selector.startsWith("coord:")) {
                    val parts = selector.removePrefix("coord:").split(",")
                    if (parts.size == 2) {
                        val xPct = parts[0].toFloatOrNull() ?: 50f
                        val yPct = parts[1].toFloatOrNull() ?: 50f
                        val coordScript = """
                            (function() {
                                var x = window.innerWidth * ($xPct / 100.0);
                                var y = window.innerHeight * ($yPct / 100.0);
                                var target = document.elementFromPoint(x, y) || document.body;
                                
                                try {
                                    var touchObj = new Touch({
                                        identifier: Date.now(),
                                        target: target,
                                        clientX: x, clientY: y, screenX: x, screenY: y, pageX: x, pageY: y
                                    });
                                    target.dispatchEvent(new TouchEvent('touchstart', { touches: [touchObj], targetTouches: [touchObj], changedTouches: [touchObj], bubbles: true, cancelable: true }));
                                    target.dispatchEvent(new TouchEvent('touchend', { touches: [], targetTouches: [], changedTouches: [touchObj], bubbles: true, cancelable: true }));
                                } catch(e) {}
                                
                                try {
                                    target.dispatchEvent(new MouseEvent('mousedown', { clientX: x, clientY: y, bubbles: true, cancelable: true }));
                                    target.dispatchEvent(new MouseEvent('mouseup', { clientX: x, clientY: y, bubbles: true, cancelable: true }));
                                    target.click();
                                    target.dispatchEvent(new MouseEvent('click', { clientX: x, clientY: y, bubbles: true, cancelable: true }));
                                } catch(e) {}
                                
                                return JSON.stringify({ success: true, message: "Clicked coordinate coord:$xPct,$yPct" });
                            })();
                        """.trimIndent()
                        val res = evalJsAsync(coordScript)
                        return@withContext Result.success("Clicked at coordinate coord:$xPct,$yPct")
                    }
                }

                val isLandingLogin = selector == "log_in_landing" || desc.contains("log in or sign up")
                val isLoginSubmit = selector == "button[type='submit']" || desc.contains("click 'log in'") || (desc.contains("log in") && !desc.contains("or sign up"))
                val isMetaCard = selector == "account_card_meta"
                val isWhatsYourName = selector == "whats_your_name_next" || desc.contains("what's your name")
                val isUsernameNext = selector == "username_next_button" || desc.contains("next on username") || (desc.contains("username") && desc.contains("next"))
                val isAllowContinue = selector == "allow_and_continue" || selector == "allow_and_agree" || desc.contains("allow and continue") || desc.contains("allow and agree") || desc.contains("allow & agree") || desc.contains("horizon")
                val isIAgree = selector == "i_agree" || desc.contains("i agree")
                val isProfileMainCard = selector == "profiles_main_card" || desc.contains("profiles_main_card")
                val isProfileInstagramCard = selector == "profiles_instagram_card"
                val isProfileUsernameOption = selector == "profiles_username_option"
                val isUsernameChangeDone = selector == "username_change_done"

                val isMetaUseEmail = selector == "meta_use_email" || desc.contains("use mobile number or email")
                val isMetaLoginInput = selector == "meta_login_input"
                val isMetaContinue = selector == "meta_continue"
                val isMetaPasswordInstead = selector == "meta_password_instead" || desc.contains("password instead")
                val isMetaPasswordInput = selector == "meta_password_input"
                val isMetaSave = selector == "meta_save" || desc.contains("save")

                val script = """
                    (function() {
                        var el = null;

                        ${if (isMetaLoginInput) """
                        el = document.querySelector("input[name='contact_point'], input[type='text'], input[type='email']");
                        if (!el) {
                             var inputs = Array.from(document.querySelectorAll("input"));
                             el = inputs.find(function(i) {
                                 var p = (i.placeholder || "").toLowerCase();
                                 return p.includes("mobile") || p.includes("email");
                             });
                        }
                        """ else ""}

                        ${if (isMetaContinue) """
                        var allBtns = Array.from(document.querySelectorAll("button, [role='button'], div, span"));
                        el = allBtns.find(function(b) {
                            var t = (b.innerText || "").trim().toLowerCase();
                            return t === "continue" || t === "চলুন" || t.includes("continue");
                        });
                        """ else ""}

                        ${if (isMetaPasswordInstead) """
                        var allNodes = Array.from(document.querySelectorAll("button, div, span, p, [role='button']"));
                        el = allNodes.find(function(item) {
                            var t = (item.innerText || "").toLowerCase();
                            return t.includes("password instead") || t.includes("enter password");
                        });
                        """ else ""}

                        ${if (isMetaPasswordInput) """
                        el = document.querySelector("input[name='password'], input[type='password']");
                        """ else ""}

                        ${if (isMetaSave) """
                        var allBtns = Array.from(document.querySelectorAll("button, [role='button'], div, span"));
                        el = allBtns.find(function(b) {
                            var t = (b.innerText || "").trim().toLowerCase();
                            return t === "save" || t === "সেভ" || t.includes("save");
                        });
                        """ else ""}

                        ${if (isLandingLogin) """
                        // 1. If already on login form, succeed immediately
                        var allInputs = Array.from(document.querySelectorAll("input"));
                        var hasLoginInput = allInputs.some(function(i) {
                            var n = (i.name || '').toLowerCase();
                            var t = (i.type || '').toLowerCase();
                            var p = (i.placeholder || '').toLowerCase();
                            return n.includes('username') || n.includes('email') || p.includes('username') || p.includes('email') || (t === 'text' && !i.hidden);
                        });
                        if (hasLoginInput) {
                            return JSON.stringify({ success: true, message: "Already on login form" });
                        }

                        // 2. Find any element containing "Log in or sign up" / "Log in" / "Sign up"
                        var allClickables = Array.from(document.querySelectorAll("a, button, div, span, p, [role='button']"));
                        el = allClickables.find(function(item) {
                            var t = (item.innerText || "").trim().toLowerCase();
                            var href = (item.getAttribute && item.getAttribute("href")) || "";
                            if (t === "log in or sign up" || t.includes("log in or sign up")) return true;
                            if ((t === "log in" || t === "sign up") && !t.includes("open instagram")) return true;
                            if (href.indexOf("/accounts/login") !== -1 || href.indexOf("/accounts/emailsignup") !== -1) return true;
                            return false;
                        });

                        if (!el) {
                            el = allClickables.find(function(item) {
                                var t = (item.innerText || "").trim().toLowerCase();
                                return (t.includes("log in") || t.includes("sign up")) && !t.includes("open instagram") && item.children.length === 0;
                            });
                        }
                        """ else ""}

                        ${if (isLoginSubmit) """
                        var allSubmitBtns = Array.from(document.querySelectorAll("button, [type='submit'], div[role='button'], a"));
                        el = allSubmitBtns.find(function(b) {
                            var txt = (b.innerText || b.textContent || "").trim().toLowerCase();
                            var type = (b.getAttribute('type') || '').toLowerCase();
                            return type === 'submit' || txt === 'log in' || txt === 'login' || (txt.includes('log in') && !txt.includes('log in or sign up'));
                        });

                        if (el) {
                            if (el.disabled) {
                                el.disabled = false;
                                el.removeAttribute('disabled');
                                el.classList.remove('disabled');
                            }
                            var parentForm = el.closest('form') || document.querySelector('form');
                            if (parentForm) {
                                try {
                                    if (typeof parentForm.requestSubmit === 'function') {
                                        parentForm.requestSubmit(el);
                                    } else {
                                        parentForm.submit();
                                    }
                                } catch(e) {}
                            }
                        }
                        """ else ""}

                        ${if (isMetaCard) """
                        // Dedicated Meta Account Card detection (explicitly skips 'Find another account')
                        var allClickables = Array.from(document.querySelectorAll("div[role='button'], button, a, [data-bloks-name]"));
                        var filtered = allClickables.filter(function(item) {
                            var t = (item.innerText || "").toLowerCase();
                            return !t.includes("find another account");
                        });
                        el = filtered.find(function(item) {
                            var t = (item.innerText || "").toLowerCase();
                            var hasEmail = t.includes("@") || t.includes(".com") || t.includes("meta");
                            var hasIcon = item.querySelector("svg, img, [aria-label]") !== null;
                            return (hasEmail && (item.getAttribute("role") === "button" || item.tagName === "BUTTON")) || (hasEmail && hasIcon);
                        });
                        if (!el) {
                            var bodyText = document.body ? document.body.innerText : "";
                            if (bodyText.includes("OR") || bodyText.includes("No Instagram profile found")) {
                                el = filtered.find(function(item) {
                                    return item.innerText && item.innerText.length > 3 && item.getAttribute("role") === "button";
                                });
                            }
                        }
                        """ else ""}

                        ${if (isWhatsYourName) """
                        // Highly robust detector for "What's your name?" screen Next button
                        var allClickables = Array.from(document.querySelectorAll("button, [role='button'], [type='submit'], div, span, a, input[type='submit']"));
                        var nextCandidates = allClickables.filter(function(b) {
                            var txt = (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || "").trim().toLowerCase();
                            return txt === "next" || txt === "continue" || txt === "next " || txt === "continue " || 
                                   txt === "ডানে" || txt === "পরবর্তী" || txt === "চলুন" || 
                                   (txt.indexOf("next") === 0 && txt.length <= 15) || (txt.indexOf("continue") === 0 && txt.length <= 15);
                        });

                        if (nextCandidates.length > 0) {
                            nextCandidates.sort(function(a, b) {
                                var scoreA = (a.tagName === "BUTTON" || a.getAttribute("role") === "button") ? 10 : 0;
                                var scoreB = (b.tagName === "BUTTON" || b.getAttribute("role") === "button") ? 10 : 0;
                                if (a.type === 'submit') scoreA += 5;
                                if (b.type === 'submit') scoreB += 5;
                                return scoreB - scoreA;
                            });
                            el = nextCandidates[0];
                        }

                        if (!el) {
                            el = document.querySelector("form button, button[type='submit'], button");
                        }
                        """ else ""}

                        ${if (isUsernameNext) """
                        var allBtns = Array.from(document.querySelectorAll("button, [role='button'], [type='submit'], div, span"));
                        var nextCandidates = allBtns.filter(function(b) {
                            var txt = (b.innerText || b.textContent || "").trim().toLowerCase();
                            return txt === "next" || txt === "continue" || txt === "next " || txt === "continue " || txt === "ডানে" || txt === "সেভ";
                        });
                        
                        if (nextCandidates.length > 0) {
                            nextCandidates.sort(function(a, b) {
                                var scoreA = (a.tagName === "BUTTON" || a.getAttribute("role") === "button") ? 2 : 0;
                                var scoreB = (b.tagName === "BUTTON" || b.getAttribute("role") === "button") ? 2 : 0;
                                return scoreB - scoreA;
                            });
                            el = nextCandidates[0];
                        }
                        
                        if (!el) {
                            el = allBtns.find(function(b) {
                                var txt = (b.innerText || "").trim().toLowerCase();
                                return (txt.indexOf("next") !== -1 || txt.indexOf("continue") !== -1) && b.tagName === "BUTTON";
                            });
                        }
                        
                        if (!el) {
                            el = document.querySelector("button[type='submit'], button");
                        }
                        """ else ""}

                        ${if (isProfileMainCard) """
                        var allNodes = Array.from(document.querySelectorAll("a, button, div, span, li, [role='button'], [data-bloks-name]"));
                        var candidates = allNodes.filter(function(item) {
                            var text = (item.innerText || item.textContent || "").trim().toLowerCase();
                            var isMainProfiles = text.includes("profiles") || text.includes("profile");
                            var isExcluded = text.includes("meta ai") || text.includes("add more") || text.includes("checkup") || text.includes("security") || text.includes("family center") || text.includes("instagram");
                            return isMainProfiles && !isExcluded;
                        });

                        if (candidates.length > 0) {
                            candidates.sort(function(a, b) {
                                return (a.innerText || "").length - (b.innerText || "").length;
                            });
                            var target = candidates[0];
                            var curr = target;
                            while (curr && curr !== document.body) {
                                var r = (curr.getAttribute("role") || "").toLowerCase();
                                var tag = curr.tagName.toLowerCase();
                                if (r === "button" || tag === "a" || tag === "button" || curr.getAttribute("tabindex") === "0" || curr.classList.contains("x1i10hfl")) {
                                    target = curr;
                                    break;
                                }
                                curr = curr.parentElement;
                            }
                            el = target;
                        }
                        """ else ""}

                        ${if (isProfileInstagramCard) """
                        var allNodes = Array.from(document.querySelectorAll("a, button, div, span, li, [role='button'], [data-bloks-name]"));
                        var candidates = allNodes.filter(function(item) {
                            var text = (item.innerText || item.textContent || "").trim().toLowerCase();
                            var isIg = text.includes("instagram") && !text.includes("meta ai") && !text.includes("add more") && !text.includes("profiles") && (item.children.length === 0 || text.length < 50);
                            return isIg;
                        });

                        if (candidates.length > 0) {
                            candidates.sort(function(a, b) {
                                return (a.innerText || "").length - (b.innerText || "").length;
                            });
                            var target = candidates[0];
                            var curr = target;
                            while (curr && curr !== document.body) {
                                var r = (curr.getAttribute("role") || "").toLowerCase();
                                var tag = curr.tagName.toLowerCase();
                                if (r === "button" || tag === "a" || tag === "button" || curr.getAttribute("tabindex") === "0" || curr.classList.contains("x1i10hfl")) {
                                    target = curr;
                                    break;
                                }
                                curr = curr.parentElement;
                            }
                            el = target;
                        }
                        """ else ""}

                        ${if (isProfileUsernameOption) """
                        // Check if we are already on the Username edit screen (if URL contains 'username')
                        var currentUrl = window.location.href || "";
                        if (currentUrl.indexOf("username") !== -1) {
                            return JSON.stringify({ success: true, message: "Successfully navigated to Username edit screen." });
                        }

                        // Otherwise, find the clickable "Username" option row under details using robust tree-walking
                        var bestCandidate = null;
                        
                        // 1. Search for <a> tags or [role='button'] whose text or href contains "username"
                        var links = Array.from(document.querySelectorAll("a, [role='button'], div[tabindex], li"));
                        for (var i = 0; i < links.length; i++) {
                            var link = links[i];
                            var txt = (link.innerText || link.textContent || "").toLowerCase();
                            var href = (link.getAttribute("href") || "").toLowerCase();
                            if ((txt.includes("username") || href.includes("username")) && !txt.includes("change") && !txt.includes("enter") && txt.length < 60) {
                                bestCandidate = link;
                                break;
                            }
                        }

                        // 2. If not found, find any element containing "username" and walk up to find the clickable row container
                        if (!bestCandidate) {
                            var all = document.querySelectorAll("*");
                            for (var j = 0; j < all.length; j++) {
                                var el = all[j];
                                var t = (el.innerText || "").toLowerCase();
                                if (t.trim() === "username" || (t.includes("username") && t.length < 35 && !t.includes("change"))) {
                                    var curr = el;
                                    while (curr && curr !== document.body) {
                                        var tag = curr.tagName.toLowerCase();
                                        var role = curr.getAttribute("role") || "";
                                        if (tag === "a" || tag === "button" || role === "button" || curr.onclick || tag === "li") {
                                            bestCandidate = curr;
                                            break;
                                        }
                                        curr = curr.parentElement;
                                    }
                                    if (!bestCandidate) bestCandidate = el;
                                    break;
                                }
                            }
                        }
                        
                        if (bestCandidate) {
                            var clickHelper = function(element) {
                                if (!element) return;
                                try {
                                    element.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                    element.focus();
                                } catch(e) {}
                                
                                var touchObj = null;
                                // Dispatch TouchStart
                                try {
                                    touchObj = new Touch({
                                        identifier: Date.now(),
                                        target: element,
                                        clientX: 10, clientY: 10, screenX: 10, screenY: 10, pageX: 10, pageY: 10
                                    });
                                    var touchStartEvent = new TouchEvent('touchstart', {
                                        touches: [touchObj], targetTouches: [touchObj], changedTouches: [touchObj], bubbles: true, cancelable: true
                                    });
                                    element.dispatchEvent(touchStartEvent);
                                } catch(e) {}
                                
                                // Dispatch TouchEnd
                                try {
                                    var touchEndEvent = new TouchEvent('touchend', {
                                        touches: [], targetTouches: [], changedTouches: [touchObj], bubbles: true, cancelable: true
                                    });
                                    element.dispatchEvent(touchEndEvent);
                                } catch(e) {}
                                
                                // Standard Mouse & Click
                                try {
                                    element.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                                    element.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                                    element.click();
                                    element.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                                } catch(e) {}
                            };

                            // Click the best candidate and its parents
                            var curr = bestCandidate;
                            var depth = 0;
                            while (curr && curr !== document.body && depth < 5) {
                                clickHelper(curr);
                                curr = curr.parentElement;
                                depth++;
                            }
                            return JSON.stringify({ success: false, retryAfter: true, message: "Clicked Username row container, waiting for edit input screen..." });
                        }

                        return JSON.stringify({ success: false, retryAfter: true, message: "Waiting for Username option row to load..." });
                        """ else ""}

                        ${if (isUsernameChangeDone) """
                        var allDoneBtns = Array.from(document.querySelectorAll("button, div[role='button'], a, [type='submit']"));
                        el = allDoneBtns.find(function(b) {
                            var t = (b.innerText || "").toLowerCase();
                            return t === "done" || t === "save" || t.includes("done") || t.includes("save");
                        });
                        """ else ""}

                        ${if (isAllowContinue) """
                        var bodyText = (document.body ? document.body.innerText : "") || "";
                        var lowerBody = bodyText.toLowerCase();

                        var candidates = Array.from(document.querySelectorAll("button, [role='button'], [type='submit'], div, span, a, li, [tabindex='0']"));
                        var matches = candidates.filter(function(b) {
                            var txt = (b.innerText || b.textContent || b.getAttribute('aria-label') || b.value || "").trim().toLowerCase();
                            return (txt.includes("allow") && txt.includes("continue")) || 
                                   (txt.includes("allow") && txt.includes("agree")) ||
                                   txt.includes("allow and continue") || 
                                   txt.includes("allow & continue") ||
                                   txt.includes("allow and agree") ||
                                   txt.includes("allow & agree") ||
                                   (txt.includes("agree") && (txt.includes("allow") || txt.includes("horizon")));
                        });

                        if (matches.length === 0) {
                            return JSON.stringify({ success: false, message: "Waiting for Allow and continue / Allow and agree button to appear..." });
                        }

                        // Prioritize exact "Allow and agree" or "Allow and continue" buttons
                        matches.sort(function(a, b) {
                            var tA = (a.innerText || a.textContent || "").toLowerCase();
                            var tB = (b.innerText || b.textContent || "").toLowerCase();
                            var scoreA = 0, scoreB = 0;
                            if (tA.includes("allow and agree") || tA.includes("allow & agree")) scoreA += 200;
                            if (tB.includes("allow and agree") || tB.includes("allow & agree")) scoreB += 200;
                            if (tA.includes("allow and continue") || tA.includes("allow & continue")) scoreA += 150;
                            if (tB.includes("allow and continue") || tB.includes("allow & continue")) scoreB += 150;
                            if (a.tagName.toLowerCase() === 'button' || a.getAttribute('role') === 'button') scoreA += 50;
                            if (b.tagName.toLowerCase() === 'button' || b.getAttribute('role') === 'button') scoreB += 50;
                            return (scoreB - scoreA) || (tA.length - tB.length);
                        });
                        var el = matches[0];

                        var isMetaHorizonAgree = false;
                        if (el) {
                            var matchedTxt = (el.innerText || el.textContent || "").toLowerCase();
                            if (matchedTxt.includes("agree") || lowerBody.includes("horizon")) {
                                isMetaHorizonAgree = true;
                                window.__ig_allow_and_agree_clicked = true;
                            }
                        }

                        // Click the element
                        try {
                            el.scrollIntoView({ behavior: 'instant', block: 'center' });
                            var rect = el.getBoundingClientRect();
                            var clickX = rect.left + rect.width / 2;
                            var clickY = rect.top + rect.height / 2;

                            var touch = new Touch({
                                identifier: Date.now(),
                                target: el,
                                clientX: clickX,
                                clientY: clickY,
                                pageX: clickX,
                                pageY: clickY
                            });
                            
                            var touchStart = new TouchEvent('touchstart', {
                                touches: [touch],
                                targetTouches: [touch],
                                changedTouches: [touch],
                                bubbles: true,
                                cancelable: true
                            });
                            el.dispatchEvent(touchStart);
                            
                            var touchEnd = new TouchEvent('touchend', {
                                touches: [],
                                targetTouches: [],
                                changedTouches: [touch],
                                bubbles: true,
                                cancelable: true
                            });
                            el.dispatchEvent(touchEnd);

                            var mouseDown = new MouseEvent('mousedown', {
                                clientX: clickX,
                                clientY: clickY,
                                bubbles: true,
                                cancelable: true
                            });
                            el.dispatchEvent(mouseDown);

                            var mouseUp = new MouseEvent('mouseup', {
                                clientX: clickX,
                                clientY: clickY,
                                bubbles: true,
                                cancelable: true
                            });
                            el.dispatchEvent(mouseUp);

                            var clickEvt = new MouseEvent('click', {
                                clientX: clickX,
                                clientY: clickY,
                                bubbles: true,
                                cancelable: true
                            });
                            el.dispatchEvent(clickEvt);
                            
                            if (typeof el.click === 'function') {
                                el.click();
                            }
                        } catch(err) {
                            if (el) el.click();
                        }

                        var msg = isMetaHorizonAgree ? "Clicked 'Allow and agree' (Meta Horizon account)" : "Clicked Allow and continue";
                        return JSON.stringify({ success: true, isAllowAndAgree: isMetaHorizonAgree, message: msg });
                        """ else ""}

                        ${if (isIAgree) """
                        // Meta Horizon Account: Step 9 & 10 are combined into 'Allow and agree'
                        if (window.__ig_allow_and_agree_clicked === true) {
                            return JSON.stringify({ success: true, message: "Meta Horizon account: 'Allow and agree' already completed (Step 10 auto-skipped)." });
                        }

                        var bText = (document.body ? document.body.innerText : "") || "";
                        var hasSuccess = bText.includes("Save your login info") || 
                                         bText.includes("Save login info") || 
                                         bText.includes("Save info") || 
                                         bText.includes("Save your login") ||
                                         bText.includes("Not now") || 
                                         document.querySelector("svg[aria-label='Home'], svg[aria-label='Direct'], [aria-label='Feed'], svg[aria-label='Search']") !== null;

                        if (hasSuccess) {
                            return JSON.stringify({ success: true, message: "Registration successful! Save login info screen detected." });
                        }

                        // Check if page has 'I agree' or 'Allow and agree' button
                        var allAgreeBtns = Array.from(document.querySelectorAll("button, [role='button'], [type='submit'], div, span, a"));
                        var iAgreeCandidate = allAgreeBtns.find(function(b) {
                            var txt = (b.innerText || b.textContent || b.getAttribute('aria-label') || "").trim().toLowerCase();
                            return txt === "i agree" || txt === "agree" || txt.includes("i agree") || (txt.includes("agree") && txt.includes("terms")) || (txt.includes("allow") && txt.includes("agree"));
                        });

                        if (iAgreeCandidate) {
                            try {
                                iAgreeCandidate.scrollIntoView({ behavior: 'instant', block: 'center' });
                                iAgreeCandidate.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                                iAgreeCandidate.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                                iAgreeCandidate.click();
                                iAgreeCandidate.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                                window.__ig_allow_and_agree_clicked = true;
                            } catch(e) {
                                iAgreeCandidate.click();
                            }
                            return JSON.stringify({ success: true, message: "Clicked 'I agree' (Step 10 completed)." });
                        }

                        return JSON.stringify({ success: false, message: "Waiting for 'I agree' button or registration completion..." });
                        """ else ""}

                        if (!el) {
                            try {
                                el = document.querySelector("$selector");
                            } catch(e) {}
                        }

                        if (!el) {
                            // Secondary fallback attempt for buttons
                            var allBtns = Array.from(document.querySelectorAll("button, div[role='button'], a, [type='submit']"));
                            el = allBtns.find(function(b) {
                                var txt = (b.innerText || "").toLowerCase();
                                var type = (b.getAttribute('type') || '').toLowerCase();
                                return type === 'submit' || txt.includes("log in") || txt.includes("next") || txt.includes("allow and continue") || txt.includes("i agree") || txt.includes("continue") || txt.includes("done");
                            });
                        }

                        if (el) {
                            if (el.disabled) {
                                el.disabled = false;
                                el.removeAttribute('disabled');
                            }
                            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            el.focus();
                            el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                            el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                            
                            // Touch event dispatch for React Native for Web
                            try {
                                var touch = new Touch({
                                    identifier: Date.now(),
                                    target: el,
                                    clientX: 0,
                                    clientY: 0,
                                    screenX: 0,
                                    screenY: 0,
                                    pageX: 0,
                                    pageY: 0
                                });
                                var touchStart = new TouchEvent('touchstart', {
                                    touches: [touch],
                                    targetTouches: [touch],
                                    changedTouches: [touch],
                                    bubbles: true,
                                    cancelable: true
                                });
                                el.dispatchEvent(touchStart);
                                
                                var touchEnd = new TouchEvent('touchend', {
                                    touches: [],
                                    targetTouches: [],
                                    changedTouches: [touch],
                                    bubbles: true,
                                    cancelable: true
                                });
                                el.dispatchEvent(touchEnd);
                            } catch(e) {
                                try {
                                    var evt1 = document.createEvent('TouchEvent');
                                    evt1.initEvent('touchstart', true, true);
                                    el.dispatchEvent(evt1);
                                    var evt2 = document.createEvent('TouchEvent');
                                    evt2.initEvent('touchend', true, true);
                                    el.dispatchEvent(evt2);
                                } catch(err) {}
                            }

                            try { el.click(); } catch(e) {}
                            el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                            return JSON.stringify({ success: true, message: "Clicked " + (el.innerText ? el.innerText.trim().substring(0, 25) : el.tagName) });
                        }
                        return JSON.stringify({ success: false, message: "Element not found: $selector" });
                    })();
                """.trimIndent()

                val rawResult = evalJsAsync(script)
                parseJsonResult(rawResult, selector)
            }
            StepActionType.TYPE -> {
                val selector = step.selector
                val desc = step.description.lowercase()
                val textToFill = when (step.valueType) {
                    VariableType.USERNAME_EMAIL -> accountEmail
                    VariableType.SHARED_PASSWORD -> {
                        val pass = sharedPassword()
                        if (pass.isNotBlank()) pass else "AutomationSecurePass2026!"
                    }
                    VariableType.AUTO_GENERATED_USERNAME -> {
                        generateAutoUsername(accountEmail)
                    }
                    VariableType.CUSTOM_TEXT -> step.customValue
                }

                if (step.valueType == VariableType.USERNAME_EMAIL && textToFill.isBlank()) {
                    return@withContext Result.failure(Exception("No email found. Please add or select an email in the [EMAIL] manager."))
                }

                val escaped = JSONObject.quote(textToFill)
                val script = """
                    (function() {
                        var valToType = $escaped;
                        var targetSelector = "$selector";
                        var desc = "$desc";

                        function findInputField() {
                            var allInputs = Array.from(document.querySelectorAll("input, textarea"));
                            var isUser = targetSelector.indexOf("username") !== -1 || desc.indexOf("username") !== -1 || desc.indexOf("email") !== -1;
                            var isPass = targetSelector.indexOf("password") !== -1 || desc.indexOf("password") !== -1;

                            if (isUser) {
                                var match = allInputs.find(function(i) {
                                    var n = (i.name || '').toLowerCase();
                                    var t = (i.type || '').toLowerCase();
                                    var ac = (i.getAttribute('autocomplete') || '').toLowerCase();
                                    var al = (i.getAttribute('aria-label') || '').toLowerCase();
                                    var p = (i.placeholder || '').toLowerCase();
                                    return t !== 'password' && (n === 'username' || n === 'email' || ac === 'username' || al.includes('username') || al.includes('phone') || al.includes('email') || p.includes('username') || p.includes('phone') || p.includes('email'));
                                });
                                if (match) return match;
                                return allInputs.find(function(i) {
                                    var t = (i.type || '').toLowerCase();
                                    return (t === 'text' || t === 'email' || t === '') && !i.hidden;
                                });
                            } else if (isPass) {
                                var match = allInputs.find(function(i) {
                                    var t = (i.type || '').toLowerCase();
                                    var n = (i.name || '').toLowerCase();
                                    var ac = (i.getAttribute('autocomplete') || '').toLowerCase();
                                    return t === 'password' || n === 'password' || ac.includes('password');
                                });
                                if (match) return match;
                                return allInputs.find(function(i) { return i.type === 'password'; });
                            }
                            return document.querySelector(targetSelector);
                        }

                        var el = findInputField();

                        // If not found, check if on landing page with "Log in or sign up"
                        if (!el) {
                            var allClickables = Array.from(document.querySelectorAll("a, button, div[role='button'], span"));
                            var loginLink = allClickables.find(function(item) {
                                var t = (item.innerText || "").trim().toLowerCase();
                                var href = (item.getAttribute && item.getAttribute("href")) || "";
                                return t.includes("log in or sign up") || (t === "log in" && !t.includes("open instagram")) || href.indexOf("/accounts/login") !== -1;
                            });
                            if (loginLink) {
                                loginLink.click();
                                return JSON.stringify({ success: false, retryAfterLanding: true, message: "Clicked 'Log in or sign up', opening login screen..." });
                            }
                        }

                        if (el) {
                            el.focus();
                            el.scrollIntoView({ behavior: 'smooth', block: 'center' });

                            // Native React 16+ property setter bypass & _valueTracker reset
                            if (el._valueTracker) {
                                try { el._valueTracker.setValue(''); } catch(e) {}
                            }

                            var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value') ?
                                Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set : null;

                            if (nativeSetter) {
                                nativeSetter.call(el, valToType);
                            } else {
                                el.value = valToType;
                            }

                            // Also try document.execCommand insertText for synthetic keyboard entry
                            try {
                                if (el.select) el.select();
                                document.execCommand('insertText', false, valToType);
                            } catch(e) {}

                            // Dispatch React events
                            el.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, data: valToType }));
                            el.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, data: valToType }));
                            el.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                            el.dispatchEvent(new Event('blur', { bubbles: true }));

                            return JSON.stringify({ success: true, message: "Filled value into " + (el.name || el.type || el.tagName) });
                        }

                        return JSON.stringify({ success: false, message: "Input element not found: " + targetSelector });
                    })();
                """.trimIndent()

                var rawResult = evalJsAsync(script)
                if (rawResult.contains("retryAfterLanding") || rawResult.contains("Input element not found")) {
                    delay(2000L)
                    rawResult = evalJsAsync(script)
                }
                if (rawResult.contains("Input element not found")) {
                    delay(1500L)
                    rawResult = evalJsAsync(script)
                }
                parseJsonResult(rawResult, selector)
            }
            StepActionType.WAIT -> {
                // Wait for element if specified
                if (step.selector.isNotBlank()) {
                    val checkScript = """
                        (function() {
                            var el = document.querySelector("${step.selector}");
                            return JSON.stringify({ exists: el !== null });
                        })();
                    """.trimIndent()
                    val rawResult = evalJsAsync(checkScript)
                    if (rawResult.contains("\"exists\":true")) {
                        Result.success("Element ${step.selector} is present")
                    } else {
                        Result.success("Waited ${step.delayMs}ms")
                    }
                } else {
                    Result.success("Waited ${step.delayMs}ms")
                }
            }
            StepActionType.NAVIGATE -> {
                val targetUrl = step.customValue.ifBlank { DEFAULT_INSTAGRAM_URL }
                webView?.loadUrl(targetUrl)
                Result.success("Navigated to $targetUrl")
            }
            StepActionType.PAUSE_USER_INPUT -> {
                Result.success("Manual Pause")
            }
        }
    }

    fun generateAutoUsername(email: String): String {
        val cleanEmailHandle = email.substringBefore("@")
            .replace(Regex("[^a-zA-Z0-9_]"), "")
            .take(12)
        val randomSuffix = (100..999).random()
        return if (cleanEmailHandle.isNotBlank()) {
            "${cleanEmailHandle}_$randomSuffix"
        } else {
            "user_${System.currentTimeMillis() % 100000}"
        }
    }

    suspend fun testSingleStep(
        step: AutomationStep,
        accountEmail: String,
        sharedPassword: () -> String
    ): Result<String> {
        return executeStep(step, accountEmail, sharedPassword)
    }

    private fun parseJsonResult(rawResult: String, selector: String): Result<String> {
        try {
            val unquoted = if (rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
                // unescape JS string
                JSONObject("{\"v\":$rawResult}").getString("v")
            } else rawResult

            val json = JSONObject(unquoted)
            val success = json.optBoolean("success", false)
            val message = json.optString("message", "")
            return if (success) {
                Result.success(message)
            } else {
                Result.failure(Exception(if (message.isNotBlank()) message else "Element not found: $selector"))
            }
        } catch (_: Exception) {
            return if (rawResult.contains("true")) {
                Result.success("Step completed")
            } else {
                Result.failure(Exception("Element not found: $selector"))
            }
        }
    }

    private suspend fun evalJsAsync(script: String): String = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String>()
        val currentView = webView
        if (currentView == null) {
            deferred.complete("{\"success\":false,\"message\":\"No WebView\"}")
        } else {
            currentView.evaluateJavascript(script) { result ->
                deferred.complete(result ?: "")
            }
        }
        deferred.await()
    }

    private fun evaluateJs(script: String) {
        mainHandler.post {
            webView?.evaluateJavascript(script, null)
        }
    }

    fun evaluateJsWithCallback(script: String, callback: (String) -> Unit) {
        mainHandler.post {
            webView?.evaluateJavascript(script) { result ->
                callback(result ?: "")
            }
        }
    }

    fun isSecurityCheckpointUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("/two_factor") ||
                lower.contains("/checkpoint/") ||
                lower.contains("/challenge/") ||
                lower.contains("login/trouble") ||
                lower.contains("recaptcha") ||
                lower.contains("captcha") ||
                lower.contains("suspicious")
    }

    fun prepareUsernameScreen(savedUsername: String?) {
        val script = """
            (function() {
                window.__ig_user_has_started_typing = false;
                window.__ig_default_cleared = false;
                window.__ig_user_clicked_continue = false;
                window.__ig_user_entered_username = "";
                window.__ig_waiting_for_username_input = true;

                function findUsernameInput() {
                    var inputs = Array.from(document.querySelectorAll("input[type='text'], input[name='username'], input[aria-label*='username' i], input:not([type='password']):not([type='hidden'])"));
                    return inputs.find(function(i) {
                        var name = (i.name || '').toLowerCase();
                        var label = (i.getAttribute('aria-label') || '').toLowerCase();
                        var placeholder = (i.placeholder || '').toLowerCase();
                        return name.includes('username') || label.includes('username') || placeholder.includes('username') || i.type === 'text';
                    }) || inputs[0];
                }

                function clearDefaultIfPresent() {
                    if (window.__ig_user_has_started_typing) return;
                    var input = findUsernameInput();
                    if (input) {
                        // If input contains default suggested username and user hasn't typed yet, CLEAR IT!
                        if (input.value && input.value.trim().length > 0 && !window.__ig_user_has_started_typing) {
                            try {
                                input.focus();
                                var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                                if (nativeSetter) {
                                    nativeSetter.call(input, "");
                                } else {
                                    input.value = "";
                                }
                                input.dispatchEvent(new Event('input', { bubbles: true }));
                                input.dispatchEvent(new Event('change', { bubbles: true }));
                                input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                            } catch(e) {
                                input.value = "";
                            }
                            window.__ig_default_cleared = true;
                        }

                        // Attach auto-clear on focus or click if user taps before typing
                        if (!input.__ig_focus_attached) {
                            input.__ig_focus_attached = true;
                            input.addEventListener('focus', function() {
                                if (!window.__ig_user_has_started_typing && input.value && input.value.trim().length > 0) {
                                    try {
                                        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                                        if (nativeSetter) nativeSetter.call(input, "");
                                        else input.value = "";
                                        input.dispatchEvent(new Event('input', { bubbles: true }));
                                        input.dispatchEvent(new Event('change', { bubbles: true }));
                                    } catch(e) { input.value = ""; }
                                }
                            });
                        }
                    }
                }

                clearDefaultIfPresent();

                // Continuously clear default username for up to 6 seconds until user starts typing
                if (window.__ig_clear_interval) clearInterval(window.__ig_clear_interval);
                var clearAttempts = 0;
                window.__ig_clear_interval = setInterval(function() {
                    clearAttempts++;
                    if (window.__ig_user_has_started_typing || clearAttempts > 60) {
                        clearInterval(window.__ig_clear_interval);
                        return;
                    }
                    clearDefaultIfPresent();
                }, 100);
            })();
        """.trimIndent()
        evaluateJs(script)
    }

    fun installNameClickListener() {
        val script = """
            (function() {
                window.__ig_name_advanced = false;
                window.__ig_name_entered = "";
                function findNameInput() {
                    var inputs = Array.from(document.querySelectorAll("input[type='text'], input[name*='name' i], input[aria-label*='name' i], input:not([type='password']):not([type='hidden'])"));
                    return inputs.find(function(i) {
                        var p = (i.placeholder || '').toLowerCase();
                        var l = (i.getAttribute('aria-label') || '').toLowerCase();
                        var n = (i.name || '').toLowerCase();
                        return p.includes('name') || l.includes('name') || n.includes('name') || i.type === 'text';
                    }) || inputs[0];
                }
                function clearDefaultNameIfPresent() {
                    var input = findNameInput();
                    if (input) {
                        if (input.value && input.value.trim().length > 0) {
                            try {
                                input.focus();
                                var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                                if (nativeSetter) {
                                    nativeSetter.call(input, "");
                                } else {
                                    input.value = "";
                                }
                                input.dispatchEvent(new Event('input', { bubbles: true }));
                                input.dispatchEvent(new Event('change', { bubbles: true }));
                                input.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                            } catch(e) {
                                input.value = "";
                            }
                        }
                    }
                }
                clearDefaultNameIfPresent();
                var nameClearAttempts = 0;
                var nameClearInterval = setInterval(function() {
                    nameClearAttempts++;
                    if (nameClearAttempts > 30) {
                        clearInterval(nameClearInterval);
                        return;
                    }
                    clearDefaultNameIfPresent();
                }, 200);

                function findNextBtn() {
                    var buttons = Array.from(document.querySelectorAll("button, [role='button'], [type='submit'], input[type='submit'], div, span, a, [tabindex='0']"));
                    var nextCandidates = buttons.filter(function(btn) {
                        var txt = (btn.innerText || btn.textContent || btn.value || btn.getAttribute('aria-label') || "").trim().toLowerCase();
                        return txt === "next" || txt === "continue" || txt === "next " || txt === "continue " || 
                               txt === "ডানে" || txt === "পরবর্তী" || txt === "চলুন" || 
                               (txt.indexOf("next") === 0 && txt.length <= 15) || (txt.indexOf("continue") === 0 && txt.length <= 15) || btn.type === "submit";
                    });

                    if (nextCandidates.length > 0) {
                        nextCandidates.sort(function(a, b) {
                            var scoreA = (a.tagName === "BUTTON" || a.getAttribute("role") === "button") ? 10 : 0;
                            var scoreB = (b.tagName === "BUTTON" || b.getAttribute("role") === "button") ? 10 : 0;
                            return scoreB - scoreA;
                        });
                        return nextCandidates[0];
                    }
                    return document.querySelector("button[type='submit'], form button");
                }

                function executeNameClick(btn, enteredVal) {
                    if (window.__ig_name_advanced) return;
                    window.__ig_name_advanced = true;
                    var val = (enteredVal || window.__ig_name_entered || "").trim();
                    window.__ig_name_entered = val;

                    if (btn) {
                        try {
                            btn.scrollIntoView({ behavior: 'instant', block: 'center' });
                            var rect = btn.getBoundingClientRect();
                            var cx = rect.left + rect.width / 2;
                            var cy = rect.top + rect.height / 2;

                            ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(function(evt) {
                                btn.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, clientX: cx, clientY: cy }));
                            });
                            btn.click();

                            var parentForm = btn.closest('form') || document.querySelector('form');
                            if (parentForm && typeof parentForm.requestSubmit === 'function') {
                                try { parentForm.requestSubmit(btn); } catch(e) {}
                            }

                            if (window.IGAutomationBridge && window.IGAutomationBridge.triggerNativeAndroidTouch) {
                                window.IGAutomationBridge.triggerNativeAndroidTouch(cx, cy);
                            }
                        } catch(e) {
                            btn.click();
                        }
                    }

                    if (window.IGAutomationBridge && window.IGAutomationBridge.onUserAdvancedNameWithValue) {
                        window.IGAutomationBridge.onUserAdvancedNameWithValue(val);
                    } else if (window.IGAutomationBridge && window.IGAutomationBridge.onUserAdvancedName) {
                        window.IGAutomationBridge.onUserAdvancedName();
                    }
                }

                function triggerAutoNameNextWatcher(enteredVal) {
                    if (!enteredVal || enteredVal.trim().length === 0) return;
                    var cleanVal = enteredVal.trim();
                    window.__ig_name_entered = cleanVal;

                    if (window.__ig_auto_name_poller) clearInterval(window.__ig_auto_name_poller);
                    var pollCount = 0;

                    window.__ig_auto_name_poller = setInterval(function() {
                        pollCount++;
                        if (window.__ig_name_advanced || pollCount > 100) {
                            clearInterval(window.__ig_auto_name_poller);
                            return;
                        }

                        var btn = findNextBtn();
                        if (btn) {
                            var isEnabled = !btn.disabled && !btn.hasAttribute('disabled') && btn.getAttribute('aria-disabled') !== 'true' && !btn.classList.contains('disabled');
                            if (isEnabled || pollCount >= 20) {
                                clearInterval(window.__ig_auto_name_poller);
                                executeNameClick(btn, cleanVal);
                            }
                        }
                    }, 80);
                }

                // Global listener for tapping 'Next' on Name screen
                var clickListener = function(e) {
                    var target = e.target;
                    if (!target) return;
                    var btn = target.closest ? target.closest("button, div[role='button'], input[type='submit'], a, [tabindex='0']") : target;
                    if (btn) {
                        var txt = (btn.innerText || btn.textContent || btn.value || "").trim().toLowerCase();
                        if (txt === "next" || txt === "continue" || txt.includes("next") || btn.type === "submit") {
                            var inp = findNameInput();
                            var v = inp ? (inp.value || "").trim() : (window.__ig_name_entered || "");
                            executeNameClick(btn, v);
                        }
                    }
                };
                document.removeEventListener("click", clickListener, true);
                document.addEventListener("click", clickListener, true);

                // Detect input activity (typing / pasting name)
                var onNameInputActivity = function(e) {
                    var target = e.target;
                    if (!target || target.tagName !== 'INPUT') return;
                    var val = (target.value || "").trim();
                    if (val.length > 0) {
                        window.__ig_name_entered = val;
                        clearTimeout(window.__ig_auto_name_timer);
                        var delayMs = (e.type === 'paste') ? 80 : 350;
                        window.__ig_auto_name_timer = setTimeout(function() {
                            var valNow = (target.value || "").trim();
                            if (valNow.length > 0) {
                                triggerAutoNameNextWatcher(valNow);
                            }
                        }, delayMs);
                    }
                };
                document.removeEventListener("input", onNameInputActivity, true);
                document.addEventListener("input", onNameInputActivity, true);
                document.removeEventListener("paste", onNameInputActivity, true);
                document.addEventListener("paste", onNameInputActivity, true);
                document.removeEventListener("change", onNameInputActivity, true);
                document.addEventListener("change", onNameInputActivity, true);

                // Enter key in name input field
                var keyListener = function(e) {
                    if (e.key === 'Enter' || e.keyCode === 13) {
                        var btn = findNextBtn();
                        var inp = findNameInput();
                        var v = inp ? (inp.value || "").trim() : (window.__ig_name_entered || "");
                        if (btn) executeNameClick(btn, v);
                    }
                };
                document.removeEventListener("keydown", keyListener, true);
                document.addEventListener("keydown", keyListener, true);
            })();
        """.trimIndent()
        evaluateJs(script)
    }

    fun installUsernameClickListener() {
        val script = """
            (function() {
                window.__ig_user_clicked_continue = false;
                window.__ig_user_entered_username = "";

                function findUsernameInput() {
                    var inputs = Array.from(document.querySelectorAll("input[type='text'], input[name='username'], input[aria-label*='username' i], input:not([type='password']):not([type='hidden'])"));
                    return inputs.find(function(i) {
                        var name = (i.name || '').toLowerCase();
                        var label = (i.getAttribute('aria-label') || '').toLowerCase();
                        var placeholder = (i.placeholder || '').toLowerCase();
                        return name.includes('username') || label.includes('username') || placeholder.includes('username') || i.type === 'text';
                    }) || inputs[0];
                }

                function findNextBtn() {
                    var buttons = Array.from(document.querySelectorAll("button, div[role='button'], input[type='submit'], a, [tabindex='0']"));
                    var nextBtn = buttons.find(function(btn) {
                        var txt = (btn.innerText || btn.textContent || btn.value || btn.getAttribute('aria-label') || "").trim().toLowerCase();
                        var isNextText = txt === "next" || txt === "continue" || txt.includes("continue") || txt.includes("next") || 
                                         txt === "done" || txt === "save" || txt.indexOf("done") !== -1 || txt.indexOf("save") !== -1 || 
                                         txt === "ডানে" || txt === "সেভ" || txt === "পরবর্তী" || txt === "চলুন" || btn.type === "submit";
                        return isNextText;
                    });
                    if (nextBtn) return nextBtn;

                    // Fallback: look for primary button on username screen
                    var submitBtn = document.querySelector("button[type='submit'], form button");
                    if (submitBtn) return submitBtn;

                    return null;
                }

                function isButtonEnabled(btn) {
                    if (!btn) return false;
                    if (btn.disabled || btn.hasAttribute('disabled')) return false;
                    if (btn.getAttribute('aria-disabled') === 'true') return false;
                    if (btn.classList.contains('disabled')) return false;
                    var style = window.getComputedStyle(btn);
                    if (style.display === 'none' || style.visibility === 'hidden') return false;
                    if (style.opacity && parseFloat(style.opacity) < 0.5) return false;
                    return true;
                }

                function executeClick(btn, enteredVal) {
                    if (!btn || window.__ig_user_clicked_continue) return;
                    window.__ig_user_clicked_continue = true;
                    var cleanVal = (enteredVal || window.__ig_user_entered_username || "").trim();
                    window.__ig_user_entered_username = cleanVal;

                    try {
                        btn.scrollIntoView({ behavior: 'instant', block: 'center' });
                        var rect = btn.getBoundingClientRect();
                        var cx = rect.left + rect.width / 2;
                        var cy = rect.top + rect.height / 2;

                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(function(evt) {
                            btn.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, clientX: cx, clientY: cy }));
                        });
                        btn.click();

                        var parentForm = btn.closest('form') || document.querySelector('form');
                        if (parentForm && typeof parentForm.requestSubmit === 'function') {
                            try { parentForm.requestSubmit(btn); } catch(e) {}
                        }

                        if (window.IGAutomationBridge && window.IGAutomationBridge.triggerNativeAndroidTouch) {
                            window.IGAutomationBridge.triggerNativeAndroidTouch(cx, cy);
                        }
                    } catch(e) {
                        btn.click();
                    }

                    if (window.IGAutomationBridge && window.IGAutomationBridge.onUserAdvancedUsernameWithValue) {
                        window.IGAutomationBridge.onUserAdvancedUsernameWithValue(cleanVal);
                    } else if (window.IGAutomationBridge && window.IGAutomationBridge.onUserAdvancedUsername) {
                        window.IGAutomationBridge.onUserAdvancedUsername();
                    }
                }

                function triggerAutoNextWatcher(enteredVal) {
                    if (!enteredVal || enteredVal.trim().length === 0) return;
                    var cleanVal = enteredVal.trim();
                    window.__ig_user_entered_username = cleanVal;

                    if (window.__ig_auto_next_poller) clearInterval(window.__ig_auto_next_poller);
                    var pollCount = 0;

                    // Watch for the Next button to become enabled after username validation
                    window.__ig_auto_next_poller = setInterval(function() {
                        pollCount++;
                        if (window.__ig_user_clicked_continue || pollCount > 100) {
                            clearInterval(window.__ig_auto_next_poller);
                            return;
                        }

                        // Check if an error appeared (e.g. username taken)
                        var bodyText = (document.body ? document.body.innerText : "") || "";
                        var lower = bodyText.toLowerCase();
                        if (lower.includes("username isn't available") || lower.includes("username is not available") || lower.includes("already exists")) {
                            clearInterval(window.__ig_auto_next_poller);
                            return;
                        }

                        var btn = findNextBtn();
                        if (btn) {
                            // If button is enabled, click it immediately!
                            if (isButtonEnabled(btn)) {
                                clearInterval(window.__ig_auto_next_poller);
                                executeClick(btn, cleanVal);
                            } else if (pollCount >= 25) {
                                // If after 2.5 seconds button exists, try clicking anyway
                                clearInterval(window.__ig_auto_next_poller);
                                executeClick(btn, cleanVal);
                            }
                        }
                    }, 80);
                }

                // Global capture handler for manual clicks on Next/Continue
                var clickHandler = function(e) {
                    var target = e.target;
                    if (!target) return;
                    var btn = target.closest ? target.closest("button, div[role='button'], input[type='submit'], a, [tabindex='0']") : target;
                    if (btn) {
                        var txt = (btn.innerText || btn.textContent || btn.value || "").trim().toLowerCase();
                        var isNext = txt === "next" || txt === "continue" || txt.includes("continue") || txt.includes("next") || 
                                     txt === "done" || txt === "save" || txt.indexOf("done") !== -1 || txt.indexOf("save") !== -1 || 
                                     txt === "ডানে" || txt === "সেভ" || txt === "পরবর্তী" || txt === "চলুন" || btn.type === "submit";
                        if (isNext) {
                            var input = findUsernameInput();
                            var enteredUsername = input ? (input.value || "").trim() : (window.__ig_user_entered_username || "").trim();
                            if (enteredUsername.length > 0) {
                                executeClick(btn, enteredUsername);
                            }
                        }
                    }
                };
                document.removeEventListener("click", clickHandler, true);
                document.addEventListener("click", clickHandler, true);

                // Detect real user typing activity
                var onUserKeystroke = function(e) {
                    window.__ig_user_has_started_typing = true;
                    if (window.__ig_clear_interval) clearInterval(window.__ig_clear_interval);
                    if (e.key === 'Enter' || e.keyCode === 13) {
                        var inp = findUsernameInput();
                        var val = inp ? (inp.value || "").trim() : (window.__ig_user_entered_username || "").trim();
                        if (val.length > 0) {
                            var btn = findNextBtn();
                            if (btn) executeClick(btn, val);
                        }
                    }
                };
                document.removeEventListener("keydown", onUserKeystroke, true);
                document.addEventListener("keydown", onUserKeystroke, true);
                document.removeEventListener("keypress", onUserKeystroke, true);
                document.addEventListener("keypress", onUserKeystroke, true);
                document.removeEventListener("compositionstart", onUserKeystroke, true);
                document.addEventListener("compositionstart", onUserKeystroke, true);

                // Global input watcher: triggers automatically when user types or pastes username
                var onInputActivity = function(e) {
                    var target = e.target;
                    if (!target || target.tagName !== 'INPUT') return;
                    window.__ig_user_has_started_typing = true;
                    if (window.__ig_clear_interval) clearInterval(window.__ig_clear_interval);

                    var val = (target.value || "").trim();
                    if (val.length > 0) {
                        window.__ig_user_entered_username = val;
                        clearTimeout(window.__ig_auto_next_timer);
                        var delayMs = (e.type === 'paste') ? 60 : 250;
                        window.__ig_auto_next_timer = setTimeout(function() {
                            var valNow = (target.value || "").trim();
                            if (valNow.length > 0) {
                                triggerAutoNextWatcher(valNow);
                            }
                        }, delayMs);
                    }
                };
                document.removeEventListener("input", onInputActivity, true);
                document.addEventListener("input", onInputActivity, true);
                document.removeEventListener("paste", onInputActivity, true);
                document.addEventListener("paste", onInputActivity, true);
                document.removeEventListener("change", onInputActivity, true);
                document.addEventListener("change", onInputActivity, true);
            })();
        """.trimIndent()
        evaluateJs(script)
    }

    suspend fun hasMovedPastUsernameScreen(): Boolean = withContext(Dispatchers.Main) {
        val script = """
            (function() {
                var bodyText = (document.body ? document.body.innerText : "") || "";
                var lowerText = bodyText.toLowerCase();

                // 1. Check for error message (username taken, invalid, etc.)
                var hasError = lowerText.includes("username isn't available") || 
                               lowerText.includes("username is not available") || 
                               lowerText.includes("user with this username already exists") ||
                               lowerText.includes("please choose another username");
                if (hasError) {
                    window.__ig_user_clicked_continue = false;
                    return JSON.stringify({ movedPast: false, error: true });
                }

                var userClicked = window.__ig_user_clicked_continue === true;
                var enteredUsername = (window.__ig_user_entered_username || "").trim();

                // CRITICAL: If user hasn't entered a username or not clicked/auto-clicked next, NEVER move past!
                if (!userClicked || enteredUsername.length === 0) {
                    return JSON.stringify({ movedPast: false, waitingForInput: true });
                }

                // 2. Check if username input is still visible on page
                var inputs = Array.from(document.querySelectorAll("input[type='text'], input[name='username'], input[aria-label*='username' i], input:not([type='password']):not([type='hidden'])"));
                var visibleUsernameInput = inputs.find(function(inp) {
                    var rect = inp.getBoundingClientRect();
                    return rect.width > 0 && rect.height > 0 && window.getComputedStyle(inp).display !== 'none' && window.getComputedStyle(inp).visibility !== 'hidden';
                });

                // If username input is still present and visible on screen:
                if (visibleUsernameInput) {
                    // Check if next screen content has started rendering
                    var nextScreenElements = lowerText.includes("allow and continue") ||
                                             lowerText.includes("allow and agree") ||
                                             lowerText.includes("allow & agree") ||
                                             lowerText.includes("agree to instagram's terms") ||
                                             lowerText.includes("i agree") ||
                                             lowerText.includes("save your login info");
                    if (!nextScreenElements) {
                        return JSON.stringify({ movedPast: false, waitingForTransition: true });
                    }
                }

                // If user entered username and clicked, and input is gone or next screen is confirmed:
                var confirmedNextScreen = lowerText.includes("allow and continue") ||
                                          lowerText.includes("allow and agree") ||
                                          lowerText.includes("allow & agree") ||
                                          lowerText.includes("agree to instagram's terms") ||
                                          lowerText.includes("i agree") ||
                                          lowerText.includes("save your login info");
                if (confirmedNextScreen || !visibleUsernameInput) {
                    return JSON.stringify({ movedPast: true });
                }

                return JSON.stringify({ movedPast: false });
            })();
        """.trimIndent()
        val result = evalJsAsync(script)
        try {
            val json = JSONObject(result)
            return@withContext json.optBoolean("movedPast", false)
        } catch (_: Exception) {
            return@withContext false
        }
    }

    suspend fun hasMovedPastNameScreen(): Boolean = withContext(Dispatchers.Main) {
        val script = """
            (function() {
                var bodyText = (document.body ? document.body.innerText : "") || "";
                var lowerText = bodyText.toLowerCase();

                // If name advanced flag was set:
                var advanced = window.__ig_name_advanced === true;

                // Check if username input or next screens are visible:
                var inputs = Array.from(document.querySelectorAll("input[type='text'], input[name='username'], input[aria-label*='username' i]"));
                var hasUsernameInput = inputs.some(function(inp) {
                    var rect = inp.getBoundingClientRect();
                    return rect.width > 0 && rect.height > 0;
                });

                var isUsernameScreen = lowerText.includes("create a username") || 
                                       lowerText.includes("choose a username") || 
                                       lowerText.includes("add your username") || 
                                       lowerText.includes("username") && lowerText.includes("next");

                var isBeyond = lowerText.includes("allow and continue") || 
                               lowerText.includes("allow and agree") || 
                               lowerText.includes("i agree");

                if (advanced || hasUsernameInput || isUsernameScreen || isBeyond) {
                    return JSON.stringify({ movedPast: true });
                }

                return JSON.stringify({ movedPast: false });
            })();
        """.trimIndent()
        val result = evalJsAsync(script)
        try {
            val json = JSONObject(result)
            return@withContext json.optBoolean("movedPast", false)
        } catch (_: Exception) {
            return@withContext false
        }
    }

    // Cookie & Profile Management
    fun captureLoggedInUsername() {
        mainHandler.post {
            val script = """
                (function() {
                    try {
                        let links = document.querySelectorAll('a[href]');
                        for (let a of links) {
                            let href = a.getAttribute('href');
                            if (href && href.length > 1 && href.startsWith('/') && href.endsWith('/') && !href.includes('?') && !href.includes('&')) {
                                let clean = href.substring(1, href.length - 1);
                                if (clean && !['explore', 'direct', 'accounts', 'developer', 'about', 'blog', 'jobs', 'help', 'privacy', 'terms', 'directory', 'suggested-profiles', 'legal'].includes(clean)) {
                                    let img = document.querySelector('img[alt*="profile"]');
                                    if (img && img.closest('a')?.getAttribute('href') === href) {
                                        return clean;
                                    }
                                }
                            }
                        }
                        let title = document.title;
                        if (title.includes('@')) {
                            let parts = title.split('@');
                            if (parts.length > 1) {
                                return parts[1].trim().split(' ')[0];
                            }
                        }
                        let scripts = document.querySelectorAll('script');
                        for (let s of scripts) {
                            let txt = s.textContent || s.innerText || "";
                            let idx = txt.indexOf('"username"');
                            if (idx !== -1) {
                                let sub = txt.substring(idx, idx + 100);
                                let match = sub.match(/"username"\s*:\s*"([^"]+)"/);
                                if (match) return match[1];
                            }
                        }
                    } catch(e) {}
                    return "";
                })()
            """.trimIndent()
            webView?.evaluateJavascript(script) { result ->
                val clean = result?.replace("\"", "")?.trim() ?: ""
                if (clean.isNotBlank() && clean != "null" && clean != "undefined") {
                    capturedUsername = clean
                }
            }
        }
    }

    fun getSessionCookieStatus(): Pair<Boolean, String> {
        val cookies = CookieManager.getInstance().getCookie("https://www.instagram.com") ?: ""
        val hasSession = cookies.contains("sessionid") || cookies.contains("ds_user_id")
        val statusText = if (hasSession) {
            if (capturedUsername.isNotBlank()) "Session: Active (@$capturedUsername)" else "Session: Active"
        } else {
            "Session: Not available"
        }
        return Pair(hasSession, statusText)
    }

    fun getInternalSessionSnapshot(): String {
        val currentUrl = webView?.url ?: "https://www.facebook.com"
        val rawCookies = CookieManager.getInstance().getCookie(currentUrl) ?: ""
        if (rawCookies.isBlank()) return ""

        val isInstagram = currentUrl.contains("instagram.com")
        if (isInstagram) {
            // Target keys in the order requested by the user
            val targetKeys = listOf(
                "ig_did", "datr", "dpr", "mid", "csrftoken", 
                "ds_user_id", "sessionid", "wd", "rur"
            )

            val cookieMap = mutableMapOf<String, String>()
            rawCookies.split(";").forEach { part ->
                val pair = part.split("=", limit = 2)
                if (pair.size == 2) {
                    val key = pair[0].trim()
                    val value = pair[1].trim()
                    if (!cookieMap.containsKey(key) || value.isNotBlank()) {
                        cookieMap[key] = value
                    }
                }
            }

            val result = mutableListOf<String>()
            targetKeys.forEach { key ->
                cookieMap[key]?.let { value ->
                    result.add("$key=$value")
                }
            }

            if (result.isNotEmpty()) {
                return result.joinToString("; ")
            }
        }

        // For Meta, Facebook, Muse, or if target keys are not found, return the full raw cookies!
        return rawCookies
    }

    fun getAllDomainsSessionSnapshot(urls: List<String> = emptyList()): String {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()

        val urlSet = linkedSetOf<String>()
        val currentUrl = webView?.url
        if (!currentUrl.isNullOrBlank()) {
            urlSet.add(currentUrl)
        }
        for (u in urls) {
            if (u.isNotBlank()) urlSet.add(u)
        }
        if (urlSet.isEmpty()) {
            urlSet.add("https://www.facebook.com")
            urlSet.add("https://www.meta.com")
        }

        val json = org.json.JSONObject()
        for (u in urlSet) {
            val c = cookieManager.getCookie(u) ?: ""
            if (c.isNotBlank()) {
                json.put(u, c)
            }
        }

        if (json.length() == 0 && !currentUrl.isNullOrBlank()) {
            val raw = cookieManager.getCookie(currentUrl) ?: ""
            if (raw.isNotBlank()) {
                json.put(currentUrl, raw)
            }
        }

        return if (json.length() > 0) json.toString() else ""
    }

    // New methods to handle LocalStorage
    fun getLocalStorageSnapshot(callback: (String) -> Unit) {
        evaluateJsWithCallback("JSON.stringify(localStorage)") { json ->
            callback(json ?: "{}")
        }
    }

    fun restoreLocalStorage(json: String) {
        evaluateJs("Object.entries(JSON.parse('$json')).forEach(([key, val]) => localStorage.setItem(key, val));")
    }

    fun reloadBrowserToInstagram() {
        mainHandler.post {
            webView?.loadUrl(DEFAULT_INSTAGRAM_URL)
        }
    }

    fun reload() {
        mainHandler.post {
            webView?.reload()
        }
    }

    fun loadUrl(url: String) {
        mainHandler.post {
            webView?.loadUrl(url)
        }
    }

    fun switchTabSession(newCookieSnapshot: String, targetUrl: String? = null, onComplete: (() -> Unit)? = null) {
        mainHandler.post {
            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                // Completely flush and clear previous tab cookies
                cookieManager.removeAllCookies {
                    if (newCookieSnapshot.isNotBlank()) {
                        val trimmedSnapshot = newCookieSnapshot.trim()
                        if (trimmedSnapshot.startsWith("{")) {
                            try {
                                val json = org.json.JSONObject(trimmedSnapshot)
                                val keys = json.keys()
                                while (keys.hasNext()) {
                                    val domainUrl = keys.next()
                                    val cookieStr = json.optString(domainUrl, "")
                                    restoreCookiesToManager(cookieManager, domainUrl, cookieStr)
                                }
                            } catch (e: Exception) {
                                val activeUrl = targetUrl ?: webView?.url ?: "https://accountscenter.meta.com/"
                                restoreCookiesToManager(cookieManager, activeUrl, trimmedSnapshot)
                            }
                        } else {
                            val activeUrl = targetUrl ?: webView?.url ?: "https://www.instagram.com"
                            restoreCookiesToManager(cookieManager, activeUrl, trimmedSnapshot)
                        }
                    }
                    cookieManager.flush()
                    if (targetUrl != null) {
                        webView?.loadUrl(targetUrl)
                    }
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
                if (targetUrl != null) {
                    webView?.loadUrl(targetUrl)
                }
                onComplete?.invoke()
            }
        }
    }

    private fun restoreCookiesToManager(cookieManager: CookieManager, targetUrl: String, rawCookies: String) {
        if (rawCookies.isBlank()) return
        val cookies = rawCookies.split(";")
        val uri = try { java.net.URI(targetUrl) } catch (e: Exception) { null }
        val host = uri?.host ?: ""
        val domainParts = host.split(".").filter { it.isNotBlank() }
        val rootDomain = if (domainParts.size >= 2) {
            "." + domainParts.takeLast(2).joinToString(".")
        } else if (host.isNotBlank()) {
            ".$host"
        } else ""

        for (cookie in cookies) {
            val trimmed = cookie.trim()
            if (trimmed.isNotEmpty()) {
                cookieManager.setCookie(targetUrl, trimmed)
                if (rootDomain.isNotEmpty()) {
                    val rootUrl = "https://" + rootDomain.removePrefix(".")
                    cookieManager.setCookie(rootUrl, trimmed)
                    cookieManager.setCookie(targetUrl, "$trimmed; Domain=$rootDomain; Path=/")
                }
                if (host.contains("meta.com") || targetUrl.contains("meta.com")) {
                    cookieManager.setCookie("https://meta.com", trimmed)
                    cookieManager.setCookie("https://accountscenter.meta.com", trimmed)
                    cookieManager.setCookie("https://auth.meta.com", trimmed)
                    cookieManager.setCookie("https://meta.com", "$trimmed; Domain=.meta.com; Path=/")
                    cookieManager.setCookie("https://accountscenter.meta.com", "$trimmed; Domain=.meta.com; Path=/")
                }
                if (host.contains("facebook.com") || targetUrl.contains("facebook.com")) {
                    cookieManager.setCookie("https://facebook.com", trimmed)
                    cookieManager.setCookie("https://www.facebook.com", trimmed)
                    cookieManager.setCookie("https://m.facebook.com", trimmed)
                    cookieManager.setCookie("https://facebook.com", "$trimmed; Domain=.facebook.com; Path=/")
                }
                if (host.contains("instagram.com") || targetUrl.contains("instagram.com")) {
                    cookieManager.setCookie("https://instagram.com", trimmed)
                    cookieManager.setCookie("https://www.instagram.com", trimmed)
                    cookieManager.setCookie("https://instagram.com", "$trimmed; Domain=.instagram.com; Path=/")
                }
            }
        }
    }

    fun getGenericSessionSnapshot(url: String): String {
        return CookieManager.getInstance().getCookie(url) ?: ""
    }

    fun restoreGenericSessionSnapshot(url: String, cookieSnapshot: String, reloadBrowser: Boolean = true) {
        if (cookieSnapshot.isBlank()) return
        mainHandler.post {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            val cookies = cookieSnapshot.split(";")
            val domain = try {
                val uri = java.net.URI(url)
                val host = uri.host ?: ""
                if (host.startsWith("www.")) host.substring(4) else host
            } catch(e: Exception) {
                ""
            }
            for (cookie in cookies) {
                val trimmed = cookie.trim()
                if (trimmed.isNotEmpty()) {
                    cookieManager.setCookie(url, trimmed)
                    if (domain.isNotEmpty()) {
                        cookieManager.setCookie(".$domain", trimmed)
                    }
                }
            }
            cookieManager.flush()
            if (reloadBrowser) {
                webView?.reload()
            }
        }
    }

    fun restoreSessionSnapshot(cookieSnapshot: String, reloadBrowser: Boolean = true) {
        if (cookieSnapshot.isBlank()) return
        mainHandler.post {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            val cookies = cookieSnapshot.split(";")
            for (cookie in cookies) {
                val trimmed = cookie.trim()
                if (trimmed.isNotEmpty()) {
                    cookieManager.setCookie("https://www.instagram.com", trimmed)
                    cookieManager.setCookie(".instagram.com", trimmed)
                }
            }
            cookieManager.flush()
            if (reloadBrowser) {
                webView?.reload()
            }
        }
    }

    fun clearBrowserSessionData(reloadUrl: String? = DEFAULT_INSTAGRAM_URL, onComplete: () -> Unit) {
        mainHandler.post {
            try {
                // Clear site storage, local/session storage, indexedDB, serviceWorkers & caches via JS
                val wipeStorageJs = """
                    (function() {
                        try { window.localStorage && window.localStorage.clear(); } catch(e) {}
                        try { window.sessionStorage && window.sessionStorage.clear(); } catch(e) {}
                        try {
                            if (window.indexedDB && window.indexedDB.databases) {
                                window.indexedDB.databases().then(function(dbs) {
                                    dbs.forEach(function(db) {
                                        if (db.name) window.indexedDB.deleteDatabase(db.name);
                                    });
                                });
                            }
                        } catch(e) {}
                        try {
                            if (navigator.serviceWorker) {
                                navigator.serviceWorker.getRegistrations().then(function(regs) {
                                    regs.forEach(function(r) { r.unregister(); });
                                });
                            }
                        } catch(e) {}
                        try {
                            if (window.caches) {
                                caches.keys().then(function(names) {
                                    names.forEach(function(n) { caches.delete(n); });
                                });
                            }
                        } catch(e) {}
                    })();
                """.trimIndent()
                webView?.evaluateJavascript(wipeStorageJs, null)

                // Clear WebView client state & storage
                WebStorage.getInstance().deleteAllData()
                android.webkit.GeolocationPermissions.getInstance().clearAll()
                webView?.clearFormData()
                webView?.clearSslPreferences()
                webView?.clearCache(true)
                webView?.clearHistory()

                // Remove all cookies immediately and flush
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    // Automatically reload the browser if a URL is provided
                    reloadUrl?.let { webView?.loadUrl(it) }
                    onComplete()
                }
            } catch (e: Exception) {
                // Fallback guarantee
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
                webView?.clearCache(true)
                reloadUrl?.let { webView?.loadUrl(it) }
                onComplete()
            }
        }
    }

    fun runCustomClickTest(
        target: String,
        searchMode: String, // "text" or "selector"
        clickMode: String,  // "geometric", "direct", "parent", "all"
        onResult: (String) -> Unit
    ) {
        val script = """
            (function() {
                var target = "${target.replace("\"", "\\\"")}";
                var searchMode = "$searchMode";
                var clickMode = "$clickMode";
                
                var el = null;
                if (searchMode === "text") {
                    var elements = Array.from(document.querySelectorAll("button, [role='button'], div, span, a, label, p, input"));
                    el = elements.find(function(item) {
                        var txt = (item.innerText || item.textContent || "").trim().toLowerCase();
                        return txt.includes(target.toLowerCase());
                    });
                } else {
                    try {
                        el = document.querySelector(target);
                    } catch(e) {
                        return "Error: Invalid CSS selector: " + e.message;
                    }
                }
                
                if (!el) {
                    return "Not Found: No element matches '" + target + "'";
                }
                
                // Highlight element (draw red border for 2.5 seconds)
                var originalOutline = el.style.outline;
                el.style.outline = "4px solid #FF1744";
                el.style.outlineOffset = "2px";
                setTimeout(function() {
                    try { el.style.outline = originalOutline; } catch(e){}
                }, 2500);
                
                var clickedMethods = [];
                
                if (clickMode === "geometric" || clickMode === "all") {
                    try {
                        var rect = el.getBoundingClientRect();
                        var x = rect.left + rect.width / 2;
                        var y = rect.top + rect.height / 2;
                        var clickEvent = new MouseEvent('click', {
                            clientX: x,
                            clientY: y,
                            bubbles: true,
                            cancelable: true,
                            view: window
                        });
                        el.dispatchEvent(clickEvent);
                        clickedMethods.push("Geometric");
                    } catch(e) {
                        clickedMethods.push("Geometric-Failed(" + e.message + ")");
                    }
                }
                
                if (clickMode === "direct" || clickMode === "all") {
                    try { el.focus(); } catch(e){}
                    try { el.click(); } catch(e){}
                    try { el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window })); } catch(e){}
                    clickedMethods.push("Direct");
                }
                
                if (clickMode === "parent" || clickMode === "all") {
                    if (el.parentElement) {
                        var p = el.parentElement;
                        try { p.click(); } catch(e){}
                        try { p.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window })); } catch(e){}
                        clickedMethods.push("Parent");
                    } else {
                        clickedMethods.push("Parent-None");
                    }
                }
                
                var computedX = 0;
                var computedY = 0;
                try {
                    var rect = el.getBoundingClientRect();
                    computedX = Math.round(rect.left + rect.width / 2);
                    computedY = Math.round(rect.top + rect.height / 2);
                } catch(e){}
                
                return "Success: Element found! Clicked via [" + clickedMethods.join(", ") + "] at geometric position: (X: " + computedX + ", Y: " + computedY + "). Outline highlighted in RED.";
            })();
        """.trimIndent()
        
        webView?.evaluateJavascript(script) { res ->
            val cleanResult = res?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: "No response from browser"
            onResult(cleanResult)
        }
    }

    fun inspectElementAtPoint(
        pxX: Float,
        pxY: Float,
        onResult: (String) -> Unit
    ) {
        val loc = IntArray(2)
        webView?.getLocationOnScreen(loc)
        val adjustedX = pxX - loc[0]
        val adjustedY = pxY - loc[1]

        val script = """
            (function() {
                var pxX = $adjustedX;
                var pxY = $adjustedY;
                var dpr = window.devicePixelRatio || 1;
                var cssX = pxX / dpr;
                var cssY = pxY / dpr;
                
                var offsets = [
                    [0, 0], [0, 15], [0, 30], [0, 45], [0, 60],
                    [0, -15], [0, -30], [15, 0], [-15, 0], [25, 0], [-25, 0],
                    [15, 15], [-15, 15], [20, 30], [-20, 30]
                ];
                
                var el = null;
                var candidateClickable = null;
                
                for (var i = 0; i < offsets.length; i++) {
                    var testX = cssX + offsets[i][0];
                    var testY = cssY + offsets[i][1];
                    var testEl = document.elementFromPoint(testX, testY);
                    if (!testEl || testEl === document.body || testEl === document.documentElement) continue;
                    
                    var curr = testEl;
                    while (curr && curr !== document.body) {
                        var tag = curr.tagName.toLowerCase();
                        var role = curr.getAttribute("role");
                        var txt = (curr.innerText || curr.textContent || curr.value || "").trim();
                        if ((tag === "button" || tag === "a" || tag === "input" || role === "button" || curr.onclick || curr.classList.contains("button") || curr.classList.contains("x1i10hfl")) && txt.length > 0) {
                            candidateClickable = curr;
                            break;
                        }
                        curr = curr.parentElement;
                    }
                    if (candidateClickable) break;
                    if (!el && testEl) {
                        var t = (testEl.innerText || testEl.textContent || "").trim();
                        if (t.length > 0) el = testEl;
                    }
                }
                
                var clickable = candidateClickable || el || document.elementFromPoint(cssX, cssY);
                if (!clickable || clickable === document.body || clickable === document.documentElement) {
                    return JSON.stringify({ found: false });
                }
                
                var text = (clickable.innerText || clickable.textContent || clickable.value || "").trim();
                if (text.length > 80) {
                    text = text.substring(0, 77) + "...";
                }
                
                var selector = clickable.tagName.toLowerCase();
                if (clickable.id) {
                    selector += "#" + clickable.id;
                } else {
                    var classes = Array.from(clickable.classList).filter(function(c) {
                        return !c.includes(":") && !c.includes("[") && c.length < 20;
                    }).slice(0, 2).join(".");
                    if (classes) selector += "." + classes;
                }
                
                var rect = clickable.getBoundingClientRect();
                var centerX = Math.round((rect.left + rect.width / 2) * dpr) + ${loc[0]};
                var centerY = Math.round((rect.top + rect.height / 2) * dpr) + ${loc[1]};
                
                return JSON.stringify({
                    found: true,
                    tagName: clickable.tagName,
                    text: text,
                    selector: selector,
                    centerX: centerX,
                    centerY: centerY
                });
            })();
        """.trimIndent()
        
        webView?.evaluateJavascript(script) { res ->
            val cleanResult = res?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: "{}"
            onResult(cleanResult)
        }
    }

    fun triggerCustomClickAtPoint(
        pxX: Float,
        pxY: Float,
        onResult: (String) -> Unit
    ) {
        val loc = IntArray(2)
        webView?.getLocationOnScreen(loc)
        val adjustedX = pxX - loc[0]
        val adjustedY = pxY - loc[1]

        val script = """
            (function() {
                $jsClickHelpers

                var pxX = $adjustedX;
                var pxY = $adjustedY;
                var dpr = window.devicePixelRatio || 1;
                var cssX = pxX / dpr;
                var cssY = pxY / dpr;
                
                var offsets = [
                    [0, 0], [0, 15], [0, 30], [0, 45],
                    [0, -15], [0, -30], [15, 0], [-15, 0], [25, 0], [-25, 0],
                    [15, 15], [-15, 15], [20, 30], [-20, 30]
                ];
                
                var el = null;
                var candidateClickable = null;
                
                for (var i = 0; i < offsets.length; i++) {
                    var testX = cssX + offsets[i][0];
                    var testY = cssY + offsets[i][1];
                    var testEl = document.elementFromPoint(testX, testY);
                    if (!testEl || testEl === document.body || testEl === document.documentElement) continue;
                    
                    var curr = testEl;
                    while (curr && curr !== document.body) {
                        var tag = curr.tagName.toLowerCase();
                        var role = curr.getAttribute("role");
                        var txt = (curr.innerText || curr.textContent || curr.value || "").trim();
                        if ((tag === "button" || tag === "a" || tag === "input" || role === "button" || curr.onclick || curr.classList.contains("button") || curr.classList.contains("x1i10hfl")) && txt.length > 0) {
                            candidateClickable = curr;
                            break;
                        }
                        curr = curr.parentElement;
                    }
                    if (candidateClickable) break;
                    if (!el && testEl) {
                        var t = (testEl.innerText || testEl.textContent || "").trim();
                        if (t.length > 0) el = testEl;
                    }
                }
                
                var clickable = candidateClickable || el || document.elementFromPoint(cssX, cssY);
                if (!clickable) {
                    return "Error: No element at point (" + pxX + ", " + pxY + ")";
                }
                
                simulateTapSync(clickable);
                try { clickable.click(); } catch(e){}
                
                var txt = (clickable.innerText || clickable.textContent || clickable.value || "").trim();
                return "Success: Clicked on element '" + (txt || clickable.tagName) + "' at (" + pxX + ", " + pxY + ")";
            })();
        """.trimIndent()
        
        webView?.evaluateJavascript(script) { res ->
            val cleanResult = res?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: "No response"
            onResult(cleanResult)
        }
    }

    fun injectTextAtPoint(
        pxX: Float,
        pxY: Float,
        textToType: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        val loc = IntArray(2)
        webView?.getLocationOnScreen(loc)
        val adjustedX = pxX - loc[0]
        val adjustedY = pxY - loc[1]

        val script = """
            (function() {
                $jsClickHelpers

                var pxX = $adjustedX;
                var pxY = $adjustedY;
                var dpr = window.devicePixelRatio || 1;
                var cssX = pxX / dpr;
                var cssY = pxY / dpr;
                
                var el = document.elementFromPoint(cssX, cssY);
                if (!el) return 'false';
                
                var input = el.tagName.toLowerCase() === 'input' || el.tagName.toLowerCase() === 'textarea' ? el : el.querySelector('input, textarea');
                if (!input) input = el;
                
                simulateTapSync(input);
                try {
                    input.focus();
                    input.value = ${JSONObject.quote(textToType)};
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                    triggerReactHandler(input);
                    return 'true';
                } catch(e) {
                    return 'false';
                }
            })();
        """.trimIndent()
        
        webView?.evaluateJavascript(script) { res ->
            onResult(res?.contains("true") == true)
        }
    }

    suspend fun dismissPostUsernameDialogIfNeeded(): Boolean = withContext(Dispatchers.Main) {
        val script = """
            (function() {
                var buttons = Array.from(document.querySelectorAll("button, div[role='button'], a, span"));
                var leaveBtn = buttons.find(function(b) {
                    var t = (b.innerText || b.textContent || "").trim().toLowerCase();
                    return t === "leave" || t === "confirm" || t === "ok" || t === "dismiss" || t === "done" || t === "save" || t.includes("leave") || t.includes("confirm") || t.includes("ছেড়ে");
                });
                if (leaveBtn) {
                    leaveBtn.click();
                    return JSON.stringify({ success: true, message: "Clicked leave/confirmation dialog button" });
                }
                return JSON.stringify({ success: false });
            })();
        """.trimIndent()
        val result = evalJsAsync(script)
        try {
            val json = JSONObject(result)
            return@withContext json.optBoolean("success", false)
        } catch (_: Exception) {
            return@withContext false
        }
    }

    suspend fun checkInputExists(): Boolean = suspendCancellableCoroutine { continuation ->
        val script = """
            (function() {
                var inputs = Array.from(document.querySelectorAll("input"));
                return inputs.some(function(i) {
                    var type = (i.type || "").toLowerCase();
                    var placeholder = (i.placeholder || "").toLowerCase();
                    var label = (i.getAttribute('aria-label') || "").toLowerCase();
                    if (type === "hidden" || i.offsetParent === null) return false;
                    return type === "text" || type === "email" || placeholder.includes("email") || placeholder.includes("mobile") || label.includes("email");
                });
            })();
        """.trimIndent()
        webView?.evaluateJavascript(script) { res ->
            val exists = res?.trim()?.toBoolean() ?: false
            if (continuation.isActive) {
                continuation.resume(exists)
            }
        }
    }

    suspend fun waitForInputToAppear(timeoutMs: Long = 10000L): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (checkInputExists()) return true
            delay(500L)
        }
        return false
    }

    suspend fun checkElementWithTextExists(selector: String, text: String): Boolean = suspendCancellableCoroutine { continuation ->
        val script = """
            (function() {
                var elements = Array.from(document.querySelectorAll("$selector"));
                return elements.some(function(el) {
                    var txt = (el.innerText || el.textContent || "").trim().toLowerCase();
                    return txt.includes("${text.lowercase()}");
                });
            })();
        """.trimIndent()
        webView?.evaluateJavascript(script) { res ->
            val exists = res?.trim()?.toBoolean() ?: false
            if (continuation.isActive) {
                continuation.resume(exists)
            }
        }
    }

    suspend fun waitForElementWithText(selector: String, text: String, timeoutMs: Long = 10000L): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (checkElementWithTextExists(selector, text)) return true
            delay(500L)
        }
        return false
    }

    suspend fun checkPasswordInputExists(): Boolean = suspendCancellableCoroutine { continuation ->
        val script = """
            (function() {
                var inputs = Array.from(document.querySelectorAll("input"));
                return inputs.some(function(i) {
                    var type = (i.type || "").toLowerCase();
                    var placeholder = (i.placeholder || "").toLowerCase();
                    var label = (i.getAttribute('aria-label') || "").toLowerCase();
                    return type === "password" || placeholder.includes("password") || label.includes("password");
                });
            })();
        """.trimIndent()
        webView?.evaluateJavascript(script) { res ->
            val exists = res?.trim()?.toBoolean() ?: false
            if (continuation.isActive) {
                continuation.resume(exists)
            }
        }
    }

    suspend fun waitForPasswordInput(timeoutMs: Long = 10000L): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (checkPasswordInputExists()) return true
            delay(500L)
        }
        return false
    }

    fun setClickYOffset(offsetPx: Int) {
        val script = "window.__clickYOffset = $offsetPx;"
        webView?.post { webView?.evaluateJavascript(script, null) }
    }

    fun setClickXOffset(offsetPx: Int) {
        val script = "window.__clickXOffset = $offsetPx;"
        webView?.post { webView?.evaluateJavascript(script, null) }
    }
}
