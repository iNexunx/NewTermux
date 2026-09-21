package com.termux.app.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.newtermux.compose.MenuItemDivider
import com.newtermux.compose.accentMenuItemColors
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.compose.outlinedMenuCard
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * Explorador de archivos + editor de texto ligero en la app, limitado al árbol
 * de archivos de Termux. Migración a Compose (Fase 2) de la antigua pantalla basada en View.
 */
class FileManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NewTermuxComposeTheme(this) {
                FileManagerScreen(onExit = { finish() })
            }
        }
    }
}

private const val MAX_TEXT_SIZE = 512L * 1024 // 512 KB

private val TEXT_EXTENSIONS = setOf(
    "txt", "sh", "bash", "zsh", "py", "js", "ts", "java", "kt", "c", "cpp", "h",
    "md", "json", "xml", "yaml", "yml", "toml", "conf", "cfg", "ini", "properties",
    "env", "rc", "profile", "log", "csv", "html", "css", "rb", "go", "rs", "php",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FileManagerScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var currentDir by remember { mutableStateOf(File(TermuxConstants.TERMUX_HOME_DIR_PATH)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val files = remember(currentDir, refreshKey) { listDir(currentDir) }

    // Estado del diálogo
    var overflowOpen by remember { mutableStateOf(false) }
    var editorFile by remember { mutableStateOf<File?>(null) }
    var optionsFile by remember { mutableStateOf<File?>(null) }
    var deleteFile by remember { mutableStateOf<File?>(null) }
    var newFileDialog by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }

    fun refresh() { refreshKey++ }

    fun navigateUpOrExit() {
        val parent = currentDir.parentFile
        val path = currentDir.absolutePath
        if (parent != null && path.startsWith(TermuxConstants.TERMUX_FILES_DIR_PATH) &&
            path != TermuxConstants.TERMUX_FILES_DIR_PATH
        ) {
            currentDir = parent
        } else {
            onExit()
        }
    }

    BackHandler { navigateUpOrExit() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administrador de archivos") },
                navigationIcon = {
                    IconButton(onClick = { navigateUpOrExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Subir")
                    }
                },
                actions = {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Más")
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }, modifier = Modifier.outlinedMenuCard()) {
                        DropdownMenuItem(text = { Text("Nuevo archivo") }, colors = accentMenuItemColors(), onClick = { overflowOpen = false; newFileDialog = true })
                        MenuItemDivider()
                        DropdownMenuItem(text = { Text("Nueva carpeta") }, colors = accentMenuItemColors(), onClick = { overflowOpen = false; newFolderDialog = true })
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                friendlyPath(currentDir.absolutePath),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider()
            if (files.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Carpeta vacía", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files, key = { it.absolutePath }) { file ->
                        FileRow(
                            file = file,
                            onClick = {
                                when {
                                    file.isDirectory -> currentDir = file
                                    isTextFile(file) -> editorFile = file
                                    else -> optionsFile = file
                                }
                            },
                            onLongClick = { optionsFile = file },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // --- Editor de texto ---
    editorFile?.let { file ->
        if (file.length() > MAX_TEXT_SIZE) {
            Toast.makeText(context, "Archivo demasiado grande para editarlo en la app", Toast.LENGTH_SHORT).show()
            editorFile = null
        } else {
            TextEditorDialog(
                file = file,
                onDismiss = { editorFile = null },
                onSaved = { editorFile = null; Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show() },
                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() },
            )
        }
    }

    // --- Opciones de archivo ---
    optionsFile?.let { file ->
        val actions = buildList {
            if (isTextFile(file)) add("Editar")
            add("Compartir"); add("Copiar ruta"); add("Eliminar")
        }
        AlertDialog(
            onDismissRequest = { optionsFile = null },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    actions.forEachIndexed { i, action ->
                        if (i > 0) MenuItemDivider()
                        DropdownMenuItem(text = { Text(action) }, colors = accentMenuItemColors(), onClick = {
                            optionsFile = null
                            when (action) {
                                "Editar" -> editorFile = file
                                "Compartir" -> shareFile(context, file)
                                "Copiar ruta" -> copyPath(context, file)
                                "Eliminar" -> deleteFile = file
                            }
                        })
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { optionsFile = null }) { Text("Cancelar") } },
        )
    }

    // --- Confirmación de borrado ---
    deleteFile?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteFile = null },
            title = { Text("Eliminar") },
            text = { Text("¿Eliminar \"${file.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = deleteRecursive(file)
                    deleteFile = null
                    if (ok) { refresh(); Toast.makeText(context, "Eliminado", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(context, "Error al eliminar", Toast.LENGTH_SHORT).show()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteFile = null }) { Text("Cancelar") } },
        )
    }

    // --- Nuevo archivo ---
    if (newFileDialog) {
        NameEntryDialog(
            title = "Nuevo archivo",
            hint = "archivo.txt",
            onDismiss = { newFileDialog = false },
            onConfirm = { name ->
                newFileDialog = false
                val f = File(currentDir, name)
                try {
                    if (f.createNewFile()) refresh()
                    else Toast.makeText(context, "El archivo ya existe", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    // --- Nueva carpeta ---
    if (newFolderDialog) {
        NameEntryDialog(
            title = "Nueva carpeta",
            hint = "nombre-carpeta",
            onDismiss = { newFolderDialog = false },
            onConfirm = { name ->
                newFolderDialog = false
                val f = File(currentDir, name)
                if (f.mkdir()) refresh()
                else Toast.makeText(context, "No se pudo crear la carpeta", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(file: File, onClick: () -> Unit, onLongClick: () -> Unit) {
    val isDir = file.isDirectory
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (isDir) "📁" else "📄", modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isDir) "Carpeta" else formatSize(file.length()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextEditorDialog(
    file: File,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
) {
    var text by remember { mutableStateOf(readFile(file)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    file.writeText(text)
                    onSaved()
                } catch (e: Exception) {
                    onError("Error al guardar: ${e.message}")
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun NameEntryDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.trim().isNotEmpty()) onConfirm(name.trim()) }) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// --- helpers ---

private fun listDir(dir: File): List<File> {
    val entries = dir.listFiles() ?: return emptyList()
    return entries
        .filter { it.name != "." && it.name != ".." }
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

private fun isTextFile(file: File): Boolean {
    if (file.isDirectory) return false
    val name = file.name.lowercase()
    if (!name.contains(".")) {
        return name.startsWith(".") || name == "makefile" || name == "dockerfile"
    }
    val ext = name.substringAfterLast('.')
    return ext in TEXT_EXTENSIONS
}

private fun readFile(file: File): String = try {
    file.readText()
} catch (e: Exception) {
    ""
}

private fun deleteRecursive(f: File): Boolean {
    if (f.isDirectory) {
        f.listFiles()?.forEach { if (!deleteRecursive(it)) return false }
    }
    return f.delete()
}

private fun friendlyPath(path: String): String {
    val home = TermuxConstants.TERMUX_HOME_DIR_PATH
    return if (path.startsWith(home)) "~" + path.substring(home.length) else path
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024))
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir ${file.name}"))
    } catch (e: Exception) {
        copyPath(context, file)
        Toast.makeText(context, "No se puede compartir; ruta copiada", Toast.LENGTH_SHORT).show()
    }
}

private fun copyPath(context: Context, file: File) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("path", file.absolutePath))
    Toast.makeText(context, "Ruta copiada", Toast.LENGTH_SHORT).show()
}
