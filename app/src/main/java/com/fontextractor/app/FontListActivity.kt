package com.fontextractor.app

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fontextractor.app.databinding.ActivityFontsBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class FontListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "package"
    }

    private lateinit var b: ActivityFontsBinding
    private lateinit var adapter: FontAdapter
    private val items = ArrayList<FontItem>()
    private var pkg = ""
    private var label = ""

    private var afterPermission: (() -> Unit)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = afterPermission
            afterPermission = null
            if (granted) action?.invoke() else toast(R.string.perm_denied)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityFontsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val packageArg = intent.getStringExtra(EXTRA_PACKAGE)
        if (packageArg == null) { finish(); return }
        pkg = packageArg
        val ai = try {
            packageManager.getApplicationInfo(pkg, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            toast(R.string.app_not_found); finish(); return
        }
        label = try { packageManager.getApplicationLabel(ai).toString() } catch (e: Exception) { pkg }

        b.toolbar.title = label
        b.toolbar.subtitle = pkg
        b.toolbar.setNavigationOnClickListener { finish() }

        adapter = FontAdapter(
            items,
            onToggle = { pos ->
                items[pos].checked = !items[pos].checked
                adapter.notifyItemChanged(pos)
                updateBar()
            },
            onShare = { share(it) }
        )
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        b.selectAll.setOnClickListener {
            val allChecked = items.isNotEmpty() && items.all { it.checked }
            items.forEach { it.checked = !allChecked }
            adapter.notifyDataSetChanged()
            updateBar()
        }
        b.export.setOnClickListener { exportSelected() }
        updateBar()
        load(ai)
    }

    private fun load(ai: ApplicationInfo) {
        lifecycleScope.launch {
            b.progress.isVisible = true
            val fonts = withContext(Dispatchers.IO) { FontScanner.scanPackage(ai) }
            items.clear()
            fonts.forEachIndexed { i, f -> items += FontItem(f, i) }
            adapter.notifyDataSetChanged()
            b.status.text = getString(R.string.font_count, items.size)
            updateBar()

            // 미리보기용 캐시 복사 + 이름 파싱은 하나씩 순서대로 진행
            withContext(Dispatchers.IO) {
                for ((i, item) in items.withIndex()) {
                    ensureActive()
                    try {
                        val f = cacheFile(item)
                        item.cacheFile = f
                        item.names = FontNameParser.parse(f)
                        item.typeface = try { Typeface.createFromFile(f) } catch (e: Exception) { null }
                    } catch (e: Exception) {
                        item.error = true
                    }
                    item.ready = true
                    withContext(Dispatchers.Main) { adapter.notifyItemChanged(i) }
                }
            }
            b.progress.isVisible = false
        }
    }

    /** APK 안의 글꼴을 캐시 디렉터리로 복사한 파일 (이미 있으면 재사용) */
    private fun cacheFile(item: FontItem): File {
        val dir = File(cacheDir, "fonts/$pkg").apply { mkdirs() }
        val f = File(dir, "${item.index}_${item.entry.fileName}")
        if (f.exists() && item.entry.size >= 0 && f.length() == item.entry.size) return f
        ZipFile(item.entry.apkPath).use { zip ->
            val e = zip.getEntry(item.entry.entryName) ?: error("APK 항목을 찾을 수 없음: ${item.entry.entryName}")
            zip.getInputStream(e).use { ins -> f.outputStream().use { out -> ins.copyTo(out) } }
        }
        return f
    }

    private fun openFont(item: FontItem): InputStream {
        val cached = item.cacheFile
        if (cached != null && cached.exists()) return cached.inputStream()
        return cacheFile(item).inputStream()
    }

    /** 저장/공유에 쓸 파일 이름: 글꼴 내부 이름을 우선, 없으면 원래 파일 이름 */
    private fun exportName(item: FontItem): String {
        val ext = item.entry.extension.takeIf { it in FontScanner.FONT_EXT } ?: "ttf"
        val names = item.names
        val base = names?.fullName?.takeIf { it.isNotBlank() }
            ?: names?.family?.takeIf { it.isNotBlank() }
            ?: item.entry.fileName.substringBeforeLast('.')
        return FontExporter.sanitize(base) + "." + ext
    }

    private fun updateBar() {
        val n = items.count { it.checked }
        b.export.text = getString(R.string.export_n, n)
        b.export.isEnabled = n > 0
        val allChecked = items.isNotEmpty() && items.all { it.checked }
        b.selectAll.setText(if (allChecked) R.string.deselect_all else R.string.select_all)
        b.selectAll.isEnabled = items.isNotEmpty()
    }

    private fun exportSelected() {
        val selected = items.filter { it.checked }
        if (selected.isEmpty()) { toast(R.string.select_first); return }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            afterPermission = { exportSelected() }
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        b.export.isEnabled = false
        b.progress.isVisible = true
        lifecycleScope.launch {
            var ok = 0
            var firstError: String? = null
            withContext(Dispatchers.IO) {
                for (item in selected) {
                    val result = runCatching {
                        openFont(item).use { ins ->
                            FontExporter.save(this@FontListActivity, ins, exportName(item), label).getOrThrow()
                        }
                    }
                    if (result.isSuccess) {
                        ok++
                    } else if (firstError == null) {
                        firstError = result.exceptionOrNull()?.message ?: "unknown"
                    }
                }
            }
            b.progress.isVisible = false
            updateBar()
            val folder = "Download/${FontExporter.ROOT_DIR}/${FontExporter.sanitize(label)}"
            val msg = if (ok == selected.size) {
                getString(R.string.export_done, ok, folder)
            } else {
                getString(R.string.export_partial, ok, selected.size, firstError ?: "")
            }
            Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun share(item: FontItem) {
        lifecycleScope.launch {
            val shareFile = withContext(Dispatchers.IO) {
                runCatching {
                    val src = cacheFile(item)
                    val dir = File(cacheDir, "share").apply { mkdirs() }
                    val dst = File(dir, exportName(item))
                    src.copyTo(dst, overwrite = true)
                }.getOrNull()
            }
            if (shareFile == null) { toast(R.string.share_failed); return@launch }
            val uri = FileProvider.getUriForFile(this@FontListActivity, "$packageName.fileprovider", shareFile)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = FontExporter.mimeFor(shareFile.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share)))
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
