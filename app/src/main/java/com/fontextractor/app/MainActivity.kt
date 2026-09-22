package com.fontextractor.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fontextractor.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val adapter = AppAdapter { app ->
        startActivity(
            Intent(this, FontListActivity::class.java)
                .putExtra(FontListActivity.EXTRA_PACKAGE, app.packageName)
        )
    }
    private var all: List<AppEntry> = emptyList()
    private var scanJob: Job? = null
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.search.doAfterTextChanged { applyFilter() }
        b.chipFontOnly.setOnCheckedChangeListener { _, _ -> applyFilter() }
        b.chipHideSystem.setOnCheckedChangeListener { _, _ -> applyFilter() }

        scan()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_rescan -> { scan(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun installedApps(pm: PackageManager): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

    private fun scan() {
        scanJob?.cancel()
        scanning = true
        scanJob = lifecycleScope.launch {
            b.progress.isVisible = true
            b.status.isVisible = true
            b.empty.isVisible = false
            b.status.text = getString(R.string.scan_preparing)

            val pm = packageManager
            val self = packageName
            val result = ArrayList<AppEntry>()

            withContext(Dispatchers.IO) {
                val apps = installedApps(pm)
                for ((i, ai) in apps.withIndex()) {
                    ensureActive()
                    if (ai.packageName == self) continue
                    val fonts = FontScanner.scanPackage(ai)
                    if (fonts.isNotEmpty()) {
                        val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { ai.packageName }
                        result += AppEntry(
                            packageName = ai.packageName,
                            label = label,
                            fontCount = fonts.size,
                            totalSize = fonts.sumOf { it.size.coerceAtLeast(0) },
                            isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        )
                    }
                    if (i % 5 == 0 || i == apps.size - 1) {
                        withContext(Dispatchers.Main) {
                            b.status.text = getString(R.string.scanning, i + 1, apps.size, result.size)
                        }
                    }
                }
            }

            all = result.sortedWith(
                compareByDescending<AppEntry> { it.isFontLike }
                    .thenBy { it.isSystem }
                    .thenByDescending { it.fontCount }
            )
            scanning = false
            b.progress.isVisible = false
            b.status.text = getString(R.string.scan_done, all.size, all.sumOf { it.fontCount })
            applyFilter()
        }
    }

    private fun applyFilter() {
        val q = b.search.text?.toString()?.trim()?.lowercase().orEmpty()
        val fontOnly = b.chipFontOnly.isChecked
        val hideSystem = b.chipHideSystem.isChecked
        val filtered = all.filter { app ->
            (!fontOnly || app.isFontLike) &&
                (!hideSystem || !app.isSystem) &&
                (q.isEmpty() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q))
        }
        adapter.submit(filtered)
        b.empty.isVisible = filtered.isEmpty() && !scanning
    }
}
