package com.autoping.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autoping.app.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var pingJob: Job? = null
    private var pingProcess: Process? = null
    private var isRunning: Boolean = false

    // Statistics
    private var countSent: Long = 0
    private var countReceived: Long = 0
    private var totalRttMs: Double = 0.0

    // Text buffer management (prevent memory leaks during long ping sessions)
    private val logLines = ArrayList<String>()
    private val maxLogLines = 500

    private val rttPattern: Pattern = Pattern.compile("time[=<]([0-9.]+)\\s*ms", Pattern.CASE_INSENSITIVE)
    private val seqPattern: Pattern = Pattern.compile("icmp_seq=([0-9]+)", Pattern.CASE_INSENSITIVE)

    companion object {
        private const val PREFS_NAME = "auto_ping_prefs"
        private const val KEY_IP = "pref_ip"
        private const val KEY_INTERVAL = "pref_interval"
        private const val KEY_KEEP_SCREEN = "pref_keep_screen"
        private const val DEFAULT_IP = "8.8.8.8"
        private const val DEFAULT_INTERVAL = "1.0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        loadPreferences()
        setupListeners()

        // 點開程式後，自動開始對指定 IP 持續 ping
        startPing()
    }

    private fun loadPreferences() {
        val savedIp = prefs.getString(KEY_IP, DEFAULT_IP) ?: DEFAULT_IP
        val savedInterval = prefs.getString(KEY_INTERVAL, DEFAULT_INTERVAL) ?: DEFAULT_INTERVAL
        val keepScreen = prefs.getBoolean(KEY_KEEP_SCREEN, true)

        binding.etTargetIp.setText(savedIp)
        binding.etInterval.setText(savedInterval)
        binding.cbKeepScreenOn.isChecked = keepScreen

        updateScreenWakeLock(keepScreen)
    }

    private fun savePreferences() {
        val currentIp = binding.etTargetIp.text.toString().trim()
        val currentInterval = binding.etInterval.text.toString().trim()
        val keepScreen = binding.cbKeepScreenOn.isChecked

        prefs.edit()
            .putString(KEY_IP, if (currentIp.isNotEmpty()) currentIp else DEFAULT_IP)
            .putString(KEY_INTERVAL, if (currentInterval.isNotEmpty()) currentInterval else DEFAULT_INTERVAL)
            .putBoolean(KEY_KEEP_SCREEN, keepScreen)
            .apply()
    }

    private fun setupListeners() {
        // Toggle Start / Stop
        binding.btnTogglePing.setOnClickListener {
            if (isRunning) {
                stopPing()
            } else {
                startPing()
            }
        }

        // Restart
        binding.btnRestart.setOnClickListener {
            restartPing()
        }

        // Keep Screen On checkbox
        binding.cbKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            updateScreenWakeLock(isChecked)
            savePreferences()
        }

        // Clear Log
        binding.btnClearLog.setOnClickListener {
            logLines.clear()
            binding.tvTerminalOutput.text = ""
            resetStats()
            Toast.makeText(this, "日誌與統計已清除", Toast.LENGTH_SHORT).show()
        }

        // Copy Log
        binding.btnCopyLog.setOnClickListener {
            val content = binding.tvTerminalOutput.text.toString()
            if (content.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Ping Logs", content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "已複製全部日誌到剪貼簿", Toast.LENGTH_SHORT).show()
            }
        }

        // Keyboard "Done" on Target IP
        binding.etTargetIp.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                restartPing()
                true
            } else {
                false
            }
        }
    }

    private fun updateScreenWakeLock(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun restartPing() {
        stopPing()
        resetStats()
        startPing()
    }

    private fun resetStats() {
        countSent = 0
        countReceived = 0
        totalRttMs = 0.0
        updateStatsUi()
    }

    private fun startPing() {
        if (isRunning) return

        val ip = binding.etTargetIp.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "請輸入目標 IP 或主機網域名稱", Toast.LENGTH_SHORT).show()
            return
        }

        val intervalStr = binding.etInterval.text.toString().trim()
        val interval = intervalStr.toDoubleOrNull() ?: 1.0
        // Linux ping on non-root Android requires interval >= 0.2s
        val safeInterval = maxOf(0.2, interval)

        savePreferences()

        isRunning = true
        updateUiState(running = true)

        appendLogLine("=========================================")
        appendLogLine("⚡ 自動開始 持續 Ping: $ip")
        appendLogLine("⏱ 間隔設定: ${safeInterval}s")
        appendLogLine("=========================================\n")

        pingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Construct standard Linux ping command
                val intervalFormatted = String.format(Locale.US, "%.1f", safeInterval)
                val cmd = mutableListOf("ping", "-i", intervalFormatted, ip)

                val processBuilder = ProcessBuilder(cmd)
                processBuilder.redirectErrorStream(true)
                val process = processBuilder.start()
                pingProcess = process

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (isActive && reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    parsePingOutput(currentLine)

                    withContext(Dispatchers.Main) {
                        appendLogLine(currentLine)
                    }
                }

                process.waitFor()
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        appendLogLine("[錯誤] Ping 執行失敗: ${e.localizedMessage}")
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    if (isRunning) {
                        stopPing()
                    }
                }
            }
        }
    }

    private fun stopPing() {
        isRunning = false
        pingJob?.cancel()
        pingJob = null

        try {
            pingProcess?.destroy()
            pingProcess = null
        } catch (_: Exception) {}

        updateUiState(running = false)
        appendLogLine("\n--- Ping 已停止 ---")
    }

    private fun parsePingOutput(line: String) {
        val seqMatcher = seqPattern.matcher(line)
        if (seqMatcher.find()) {
            countSent++
        }

        val rttMatcher = rttPattern.matcher(line)
        if (rttMatcher.find()) {
            countReceived++
            val rttVal = rttMatcher.group(1)?.toDoubleOrNull()
            if (rttVal != null) {
                totalRttMs += rttVal
            }
        }

        runOnUiThread {
            updateStatsUi()
        }
    }

    private fun updateStatsUi() {
        binding.tvStatSent.text = countSent.toString()
        binding.tvStatRecv.text = countReceived.toString()

        val lossRate = if (countSent > 0) {
            val lost = maxOf(0L, countSent - countReceived)
            (lost * 100.0 / countSent)
        } else {
            0.0
        }
        binding.tvStatLoss.text = String.format(Locale.US, "%.1f%%", lossRate)

        val avgRtt = if (countReceived > 0) {
            totalRttMs / countReceived
        } else {
            null
        }
        binding.tvStatAvg.text = if (avgRtt != null) String.format(Locale.US, "%.1f ms", avgRtt) else "- ms"
    }

    private fun appendLogLine(line: String) {
        if (logLines.size >= maxLogLines) {
            logLines.removeAt(0)
        }
        logLines.add(line)

        val fullText = logLines.joinToString("\n")
        binding.tvTerminalOutput.text = fullText

        binding.svLogScroll.post {
            binding.svLogScroll.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun updateUiState(running: Boolean) {
        if (running) {
            binding.tvStatusBadge.text = "● RUNNING"
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.terminal_text_green))
            binding.btnTogglePing.text = getString(R.string.btn_stop)
            binding.btnTogglePing.backgroundTintList = ContextCompat.getColorStateList(this, R.color.btn_stop)
        } else {
            binding.tvStatusBadge.text = "○ STOPPED"
            binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.terminal_text_muted))
            binding.btnTogglePing.text = getString(R.string.btn_start)
            binding.btnTogglePing.backgroundTintList = ContextCompat.getColorStateList(this, R.color.btn_start)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPing()
    }
}
