package com.pixelricardo.game

import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

class MainActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameView = GameView(this) { showBackupMenu() }
        setContentView(gameView)
        hideSystemUi()
    }

    private fun showBackupMenu() {
        AlertDialog.Builder(this)
            .setTitle("Backup do progresso")
            .setMessage("Exporte um arquivo antes de trocar de aparelho ou remover o jogo. A importação substitui o progresso atual.")
            .setPositiveButton("Exportar") { _, _ ->
                openDocumentPicker(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "PixelRicardo-${todayStr()}.json")
                }, 100)
            }
            .setNeutralButton("Importar") { _, _ ->
                openDocumentPicker(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }, 101)
            }
            .setNegativeButton("Voltar", null).show()
    }

    private fun openDocumentPicker(intent: Intent, request: Int) {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, request)
        } catch (_: Exception) {
            notifyBackup("Não foi possível abrir o seletor de arquivos.")
        }
    }

    @Deprecated("Uses platform document picker without additional dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in 100..101 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 100) {
                val bytes = gameView.backupData().toByteArray(Charsets.UTF_8)
                require(bytes.size <= Backup.MAX_BYTES)
                val output = contentResolver.openOutputStream(uri, "wt")
                    ?: error("Arquivo indisponível")
                output.use { it.write(bytes) }
                notifyBackup("Backup exportado. Guarde o arquivo fora do jogo.")
            } else {
                val input = contentResolver.openInputStream(uri) ?: error("Arquivo indisponível")
                val bytes = input.use { stream ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (buffer.size() <= Backup.MAX_BYTES) {
                        val count = stream.read(chunk, 0, minOf(chunk.size, Backup.MAX_BYTES + 1 - buffer.size()))
                        if (count < 0) break
                        buffer.write(chunk, 0, count)
                    }
                    buffer.toByteArray()
                }
                require(bytes.size <= Backup.MAX_BYTES)
                val restored = Backup.decode(bytes.toString(Charsets.UTF_8))
                AlertDialog.Builder(this)
                    .setTitle("Substituir o progresso?")
                    .setMessage("Backup: ${restored.caught.size} espécies e ${restored.coins} pedrinhas. Exporte o progresso atual antes de continuar se quiser guardá-lo.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Substituir") { _, _ ->
                        try {
                            gameView.restoreBackup(restored)
                            notifyBackup("Progresso importado.")
                        } catch (_: Exception) {
                            notifyBackup("Não foi possível salvar o progresso importado.")
                        }
                    }.show()
            }
        } catch (_: Exception) {
            notifyBackup(if (requestCode == 100) "Falha ao exportar. Tente outro arquivo." else "Backup inválido ou incompatível. Seu progresso foi mantido.")
        }
    }

    private fun notifyBackup(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { c ->
                c.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onPause() {
        super.onPause()
        if (::gameView.isInitialized) gameView.persistNow()
    }

    override fun onDestroy() {
        if (::gameView.isInitialized) gameView.persistNow()
        super.onDestroy()
    }
}