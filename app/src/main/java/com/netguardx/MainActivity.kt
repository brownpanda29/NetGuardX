package com.netguardx

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: Button
    private lateinit var addAppButton: Button
    private lateinit var appsListView: ListView
    private lateinit var addBlocklistButton: Button
    private lateinit var blocklistsListView: ListView
    private lateinit var updateListsButton: Button

    private val selectedApps = mutableListOf<String>()
    private val blocklists = mutableListOf<BlocklistItem>()

    private lateinit var blocklistManager: BlocklistManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.toggleButton)
        addAppButton = findViewById(R.id.addAppButton)
        appsListView = findViewById(R.id.appsListView)
        addBlocklistButton = findViewById(R.id.addBlocklistButton)
        blocklistsListView = findViewById(R.id.blocklistsListView)
        updateListsButton = findViewById(R.id.updateListsButton)

        blocklistManager = BlocklistManager(this)

        loadSelectedApps()
        loadBlocklists()
        updateUI()

        toggleButton.setOnClickListener {
            if (MyVpnService.isRunning) {
                stopVpn()
            } else {
                startVpn()
            }
        }

        addAppButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
                putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                })
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_APP)
        }

        addBlocklistButton.setOnClickListener {
            showAddBlocklistDialog()
        }

        updateListsButton.setOnClickListener {
            blocklistManager.updateRemoteLists()
            Toast.makeText(this, "Lists updated", Toast.LENGTH_SHORT).show()
        }

        appsListView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            selectedApps.removeAt(position)
            saveSelectedApps()
            updateUI()
            true
        }

        blocklistsListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            blocklists[position].enabled = !blocklists[position].enabled
            saveBlocklists()
            updateUI()
        }

        blocklistsListView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            blocklists.removeAt(position)
            saveBlocklists()
            updateUI()
            true
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) != PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, REQUEST_CODE_VPN)
        } else {
            onActivityResult(REQUEST_CODE_VPN, Activity.RESULT_OK, null)
        }
    }

    private fun stopVpn() {
        val intent = Intent(this, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_STOP
        }
        startService(intent)
        updateUI()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_VPN -> {
                if (resultCode == Activity.RESULT_OK) {
                    val intent = Intent(this, MyVpnService::class.java).apply {
                        action = MyVpnService.ACTION_START
                        putStringArrayListExtra(MyVpnService.EXTRA_APPS, ArrayList(selectedApps))
                    }
                    startService(intent)
                    updateUI()
                }
            }
            REQUEST_CODE_PICK_APP -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val component = data.component
                    if (component != null) {
                        val packageName = component.packageName
                        if (!selectedApps.contains(packageName)) {
                            selectedApps.add(packageName)
                            saveSelectedApps()
                            updateUI()
                        }
                    }
                }
            }
        }
    }

    private fun showAddBlocklistDialog() {
        val options = arrayOf("Domain", "URL")
        AlertDialog.Builder(this)
            .setTitle("Add Blocklist")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showInputDialog("Enter domain", "") { input ->
                        blocklists.add(BlocklistItem(input, true, false))
                        saveBlocklists()
                        updateUI()
                    }
                    1 -> showInputDialog("Enter URL", "") { input ->
                        blocklists.add(BlocklistItem(input, true, true))
                        saveBlocklists()
                        updateUI()
                    }
                }
            }
            .show()
    }

    private fun showInputDialog(title: String, hint: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply { setHint(hint) }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUI() {
        toggleButton.text = if (MyVpnService.isRunning) getString(R.string.stop) else getString(R.string.start_protection)
        appsListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, selectedApps.map { getAppName(it) })
        blocklistsListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, blocklists.map { "${it.name} (${if (it.enabled) "Enabled" else "Disabled"})" })
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun loadSelectedApps() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        selectedApps.clear()
        selectedApps.addAll(prefs.getStringSet("selectedApps", emptySet()) ?: emptySet())
    }

    private fun saveSelectedApps() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        prefs.edit().putStringSet("selectedApps", selectedApps.toSet()).apply()
    }

    private fun loadBlocklists() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        blocklists.clear()
        val json = prefs.getString("blocklists", "[]")
        // Simple JSON parsing, in real app use Gson
        // For simplicity, assume format: name;enabled;isUrl\n...
        val lines = json?.split("\n") ?: emptyList()
        for (line in lines) {
            if (line.isNotEmpty()) {
                val parts = line.split(";")
                if (parts.size == 3) {
                    blocklists.add(BlocklistItem(parts[0], parts[1].toBoolean(), parts[2].toBoolean()))
                }
            }
        }
    }

    private fun saveBlocklists() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val json = blocklists.joinToString("\n") { "${it.name};${it.enabled};${it.isUrl}" }
        prefs.edit().putString("blocklists", json).apply()
    }

    companion object {
        const val REQUEST_CODE_VPN = 1
        const val REQUEST_CODE_PICK_APP = 2
    }
}

data class BlocklistItem(val name: String, var enabled: Boolean, val isUrl: Boolean)