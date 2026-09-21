package com.termux.app.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.newtermux.compose.MenuItemDivider
import com.newtermux.compose.accentMenuItemColors
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.compose.outlinedMenuCard
import com.newtermux.features.NewTermuxSettings
import com.newtermux.features.TextExpansionStore
import com.termux.app.TermuxActivity
import com.termux.shared.android.PermissionUtils
import com.termux.app.TermuxInstaller
import com.termux.app.models.UserAction
import com.termux.shared.android.AndroidUtils
import com.termux.shared.android.PackageUtils
import com.termux.shared.file.FileUtils
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.models.ReportInfo
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences
import com.termux.shared.activities.ReportActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Ajustes de NewTermux, reescritos por completo en Jetpack Compose (Fase 5), reemplazando
 * el framework androidx.preference. Usa una pila interna para navegar entre la lista raíz,
 * las secciones de NewTermux (Respaldo/Features/Expansión de texto) y las sub-pantallas
 * de preferencias de Termux + plugins, enlazando directamente con las API existentes de
 * SharedPreferences / almacén de datos.
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NewTermuxComposeTheme(this) {
                SettingsRoot(activity = this)
            }
        }
    }
}

private enum class Route {
    ROOT, BACKUP, FEATURES, TEXT_EXPANSION,
    TERMUX, TERMINAL_IO, TERMINAL_VIEW, DEBUGGING,
    PLUGIN_API, PLUGIN_FLOAT, PLUGIN_TASKER, PLUGIN_WIDGET,
}

@Composable
private fun SettingsRoot(activity: Activity) {
    val stack = remember { mutableStateListOf(Route.ROOT) }
    fun push(r: Route) = stack.add(r)
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else activity.finish() }

    BackHandler { pop() }

    when (stack.last()) {
        Route.ROOT -> RootScreen(activity, onBack = { pop() }, onNav = { push(it) })
        Route.BACKUP -> BackupScreen(onBack = { pop() })
        Route.FEATURES -> FeaturesScreen(activity, onBack = { pop() })
        Route.TEXT_EXPANSION -> TextExpansionScreen(onBack = { pop() })
        Route.TERMUX -> TermuxScreen(onBack = { pop() }, onNav = { push(it) })
        Route.TERMINAL_IO -> TerminalIOScreen(onBack = { pop() })
        Route.TERMINAL_VIEW -> TerminalViewScreen(onBack = { pop() })
        Route.DEBUGGING -> DebuggingScreen(onBack = { pop() })
        Route.PLUGIN_API -> PluginScreen("Termux:API", Plugin.API, onBack = { pop() })
        Route.PLUGIN_FLOAT -> PluginScreen("Termux:Float", Plugin.FLOAT, onBack = { pop() })
        Route.PLUGIN_TASKER -> PluginScreen("Termux:Tasker", Plugin.TASKER, onBack = { pop() })
        Route.PLUGIN_WIDGET -> PluginScreen("Termux:Widget", Plugin.WIDGET, onBack = { pop() })
    }
}

// ---------------------------------------------------------------- shared UI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        content(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()))
    }
}

@Composable
private fun CategoryHeader(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NavRow(title: String, summary: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Bool switch backed by NewTermuxSettings.get/set. */
@Composable
private fun NtSwitch(context: Context, key: String, title: String, summary: String?) {
    var checked by remember { mutableStateOf(NewTermuxSettings.get(context, key)) }
    SwitchRow(title, summary, checked) {
        checked = it
        NewTermuxSettings.set(context, key, it)
    }
}

/**
 * Conmutador "Mantener actividad en segundo plano". Respaldado por NewTermuxSettings; al activarlo también
 * pide al usuario que excluya la app de la optimización de batería (si aún no está exenta), ya que el wake lock
 * por sí solo puede perder contra Doze.
 */
@Composable
private fun KeepAliveSwitch(activity: Activity) {
    val context = LocalContext.current
    var checked by remember { mutableStateOf(NewTermuxSettings.isKeepAliveInBackground(context)) }
    SwitchRow(
        title = "Mantener actividad en segundo plano",
        summary = "Mantiene un wake lock en primer plano mientras hay sesiones activas para que la app sobreviva al abrir un juego. Desactívalo para ahorrar batería.",
        checked = checked,
    ) {
        checked = it
        NewTermuxSettings.set(context, NewTermuxSettings.KEY_KEEP_ALIVE_BACKGROUND, it)
        if (it && !PermissionUtils.checkIfBatteryOptimizationsDisabled(context)) {
            // Mark prompted so the first-run nudge won't also fire, then request the exemption.
            NewTermuxSettings.setBatteryOptPrompted(context, true)
            runCatching { PermissionUtils.requestDisableBatteryOptimizations(activity) }
        }
    }
}

@Composable
private fun LogLevelRow(context: Context, current: Int, onSelect: (Int) -> Unit) {
    val values = remember { Logger.getLogLevelsArray().map { it.toString() } }
    val labels = remember { Logger.getLogLevelLabelsArray(context, Logger.getLogLevelsArray(), true).map { it.toString() } }
    var expanded by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(current) }
    val idx = values.indexOf(value.toString()).coerceAtLeast(0)
    Box {
        NavRow(title = "Nivel de log", summary = labels.getOrNull(idx)) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.outlinedMenuCard()) {
            values.forEachIndexed { i, v ->
                if (i > 0) MenuItemDivider()
                DropdownMenuItem(text = { Text(labels.getOrElse(i) { v }) }, colors = accentMenuItemColors(), onClick = {
                    expanded = false
                    v.toIntOrNull()?.let { lvl ->
                        value = lvl
                        onSelect(lvl)
                    }
                })
            }
        }
    }
}

// ---------------------------------------------------------------- root

@Composable
private fun RootScreen(activity: Activity, onBack: () -> Unit, onNav: (Route) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val apiInstalled = remember { TermuxAPIAppSharedPreferences.build(context, false) != null }
    val floatInstalled = remember { TermuxFloatAppSharedPreferences.build(context, false) != null }
    val taskerInstalled = remember { TermuxTaskerAppSharedPreferences.build(context, false) != null }
    val widgetInstalled = remember { TermuxWidgetAppSharedPreferences.build(context, false) != null }
    val donateVisible = remember { isDonateVisible(context) }

    fun launch(cls: Class<*>) = context.startActivity(Intent(context, cls))

    SettingsScaffold("Ajustes", onBack) { mod ->
        Column(modifier = mod) {
            NavRow("Apariencia", "Color de acento y tema de la interfaz") { launch(ThemePickerActivity::class.java) }
            NavRow("Administrador de paquetes", "Explorar, buscar e instalar paquetes") { launch(PackageManagerActivity::class.java) }
            NavRow("Administrador SSH", "Guardar y conectar con varios servidores SSH") { launch(SshManagerActivity::class.java) }
            NavRow("Administrador de archivos", "Explorar, editar y gestionar archivos en tu home de Termux") { launch(FileManagerActivity::class.java) }
            HorizontalDivider()
            NavRow("Respaldo y restauración", "Respalda o restaura tu entorno Termux") { onNav(Route.BACKUP) }
            NavRow("Funciones", "Activa o desactiva las funciones de NewTermux") { onNav(Route.FEATURES) }
            NavRow("Expansión de texto", "Expande abreviaturas cortas a comandos completos") { onNav(Route.TEXT_EXPANSION) }
            HorizontalDivider()
            NavRow("Termux", "Opciones de terminal, teclado y depuración") { onNav(Route.TERMUX) }
            if (apiInstalled) NavRow("Termux:API", "Ajustes del plugin API") { onNav(Route.PLUGIN_API) }
            if (floatInstalled) NavRow("Termux:Float", "Ajustes del plugin de ventana flotante") { onNav(Route.PLUGIN_FLOAT) }
            if (taskerInstalled) NavRow("Termux:Tasker", "Ajustes del plugin de Tasker") { onNav(Route.PLUGIN_TASKER) }
            if (widgetInstalled) NavRow("Termux:Widget", "Ajustes del plugin de Widget") { onNav(Route.PLUGIN_WIDGET) }
            HorizontalDivider()
            NavRow("Acerca de", "Información de la app, dispositivo y plugins") {
                scope.launch { openAbout(context) }
            }
            if (donateVisible) NavRow("Donar", "Apoya el desarrollo") { ShareUtils.openUrl(context, TermuxConstants.TERMUX_DONATE_URL) }
        }
    }
}

// ---------------------------------------------------------------- Features

@Composable
private fun FeaturesScreen(activity: Activity, onBack: () -> Unit) {
    val context = LocalContext.current
    var showScriptEditor by remember { mutableStateOf(false) }
    var showRestartWarning by remember { mutableStateOf(false) }

    val zshInstalled = remember { File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "bin/zsh").exists() }

    SettingsScaffold("Funciones", onBack) { mod ->
        Column(modifier = mod) {
            CategoryHeader("Teclado")
            NtSwitch(context, NewTermuxSettings.KEY_KEYBOARD_SUGGESTIONS, "Sugerencias del teclado", "Mostrar la barra de autocorrección y sugerencias de palabras")
            NtSwitch(context, NewTermuxSettings.KEY_AUTOCORRECT, "Autocorrección de comandos", "Sugerir correcciones para comandos mal escritos (barra espaciadora)")
            NtSwitch(context, NewTermuxSettings.KEY_URL_DETECTION_ENABLED, "Detección de URLs", "Mantén pulsada una URL del terminal para abrirla o copiarla")
            NtSwitch(context, NewTermuxSettings.KEY_EXTRA_KEYS_VISIBLE, "Mostrar barra de teclas extra", "Mostrar la fila de ESC, TAB y flechas encima del teclado")
            NtSwitch(context, NewTermuxSettings.KEY_EXTRA_KEYS_IN_DRAWER, "Teclas extra en el panel derecho", "Mover las teclas extra a un panel lateral deslizable (requiere reinicio)")

            CategoryHeader("Botones de la barra")
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_AC_BUTTON, "Botón AC", "Mostrar el botón de autocorrección on/off en la barra")
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_ROOT_BUTTON, "Botón de root", null)
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_STT_BUTTON, "Botón de dictado por voz", null)
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_PACKAGES_BUTTON, "Botón de administrador de paquetes", null)
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_CLEAR_BUTTON, "Botón de limpiar terminal", null)

            CategoryHeader("Pestañas de sesión")
            NtSwitch(context, NewTermuxSettings.KEY_SESSION_TABS, "Mostrar pestañas de sesión", "Mostrar las fichas de sesión en la parte superior")
            NtSwitch(context, NewTermuxSettings.KEY_SESSION_RENAME_ENABLED, "Renombrar sesiones", "Mantén pulsada una pestaña de sesión para renombrarla")

            CategoryHeader("Inicio")
            NtSwitch(context, NewTermuxSettings.KEY_STARTUP_SCRIPT_ENABLED, "Script de inicio", "Ejecutar ~/.termux/startup-script.sh en cada sesión nueva")
            NavRow("Editar script de inicio", "Editar ~/.termux/startup-script.sh") { showScriptEditor = true }

            CategoryHeader("Shell")
            if (zshInstalled) {
                NavRow("Zsh", "✓ Instalado", enabled = false) {}
            } else {
                NavRow("Instalar Zsh", "Necesario para el resaltado de sintaxis y autosugerencias") {
                    NewTermuxSettings.setPendingCommand(context, "pkg install zsh\n")
                    activity.finish()
                }
            }
            ZshPluginsSwitch(context, zshInstalled, onChanged = { showRestartWarning = true })
            NtSwitch(context, NewTermuxSettings.KEY_PACKAGE_AUTOCLOSE, "Cerrar sesión al instalar paquetes", "Al escribir 'pkg install …' se abre una sesión nueva y se cierra al terminar")

            CategoryHeader("Panel lateral")
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_DRAWER_EXPORT_SCRIPT, "Exportar pantalla y hacer script", "Mostrar los botones Export Screen y Make Script en el panel")
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_DRAWER_PKG_UPDATE, "Botón de actualizar paquetes", "Mostrar un botón que ejecuta pkg update && pkg upgrade -y")
            NtSwitch(context, NewTermuxSettings.KEY_SHOW_DRAWER_CMD_BUTTONS, "Botones de comando en el panel", "Mostrar atajos de comando personalizables")

            CategoryHeader("Segundo plano")
            KeepAliveSwitch(activity)

            CategoryHeader("Permisos")
            NavRow("Conceder permiso de almacenamiento", "Permitir acceso a /sdcard y configurar los enlaces ~/storage") {
                (activity as? TermuxActivity)?.requestStoragePermission(false)
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    if (showScriptEditor) {
        StartupScriptEditorDialog(context, onDismiss = { showScriptEditor = false })
    }
    if (showRestartWarning) {
        AlertDialog(
            onDismissRequest = { showRestartWarning = false },
            title = { Text("Reinicio requerido") },
            text = { Text("Abre una nueva sesión de terminal para que este cambio tenga efecto.") },
            confirmButton = { TextButton(onClick = { showRestartWarning = false }) { Text("Aceptar") } },
        )
    }
}

@Composable
private fun ZshPluginsSwitch(context: Context, zshInstalled: Boolean, onChanged: () -> Unit) {
    var checked by remember { mutableStateOf(NewTermuxSettings.isZshPluginsEnabled(context)) }
    SwitchRow(
        title = "Mejoras del shell",
        summary = if (zshInstalled) "Autosugerencias + resaltado; añade un bloque a ~/.zshrc sin sobrescribirlo y usa Zsh como shell" else "Instala Zsh primero para activarlo",
        checked = checked,
        enabled = zshInstalled,
    ) {
        checked = it
        NewTermuxSettings.set(context, NewTermuxSettings.KEY_ZSH_PLUGINS, it)
        Thread { TermuxInstaller.setZshPlugins(context, it) }.start()
        onChanged()
    }
}

@Composable
private fun StartupScriptEditorDialog(context: Context, onDismiss: () -> Unit) {
    val scriptFile = remember { File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".termux/startup-script.sh") }
    var text by remember { mutableStateOf(if (scriptFile.exists()) runCatching { scriptFile.readText() }.getOrDefault("") else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar script de inicio") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    scriptFile.parentFile?.mkdirs()
                    scriptFile.writeText(text)
                    Toast.makeText(context, "Script de inicio guardado", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------- Text Expansion

@Composable
private fun TextExpansionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NewTermuxSettings.isTextExpansionEnabled(context)) }
    val items = remember { mutableStateListOf<TextExpansionStore.TextExpansion>().apply { addAll(TextExpansionStore.load(context)) } }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun persist() = TextExpansionStore.save(context, items.toMutableList())

    SettingsScaffold("Expansión de texto", onBack) { mod ->
        Column(modifier = mod) {
            SwitchRow("Activar expansión de texto", "Expande abreviaturas cortas a comandos completos", enabled) {
                enabled = it
                NewTermuxSettings.set(context, NewTermuxSettings.KEY_TEXT_EXPANSION_ENABLED, it)
            }
            HorizontalDivider()
            NavRow("Añadir nueva", "Crear una nueva expansión") { editIndex = null; showEditor = true }
            if (items.isEmpty()) {
                Text(
                    "Aún no hay expansiones. Pulsa 'Añadir nueva' para crear una.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                items.forEachIndexed { i, exp ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { editIndex = i; showEditor = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${exp.trigger}  →  ${exp.expansion}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { items.removeAt(i); persist() }) { Text("Eliminar") }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val editing = editIndex?.let { items.getOrNull(it) }
        ExpansionEditorDialog(
            initTrigger = editing?.trigger ?: "",
            initExpansion = editing?.expansion ?: "",
            onDismiss = { showEditor = false },
            onSave = { trigger, expansion ->
                val idx = editIndex
                if (idx != null && idx < items.size) {
                    items[idx].trigger = trigger
                    items[idx].expansion = expansion
                    items[idx] = items[idx] // trigger recomposition
                } else {
                    val e = TextExpansionStore.TextExpansion()
                    e.trigger = trigger; e.expansion = expansion
                    items.add(e)
                }
                persist()
                showEditor = false
            },
        )
    }
}

@Composable
private fun ExpansionEditorDialog(
    initTrigger: String,
    initExpansion: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var trigger by remember { mutableStateOf(initTrigger) }
    var expansion by remember { mutableStateOf(initExpansion) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initTrigger.isNotEmpty() || initExpansion.isNotEmpty()) "Editar expansión" else "Añadir expansión") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(trigger, { trigger = it }, label = { Text("Abreviatura (p. ej. ;ll)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(expansion, { expansion = it }, label = { Text("Expansión (p. ej. ls -la)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (trigger.trim().isEmpty()) {
                    Toast.makeText(context, "La abreviatura no puede estar vacía", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onSave(trigger.trim(), expansion)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------- Backup

@Composable
private fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreFull by remember { mutableStateOf<Boolean?>(null) }

    val basicSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Respaldando home…"
            val err = withContext(Dispatchers.IO) { runBackup(context, uri, false) }
            busy = null
            toast(context, if (err == null) "Respaldo completado" else "Respaldo fallido: $err")
        }
    }
    val fullSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        if (uri != null) scope.launch {
            busy = "Respaldando home + usr…"
            val err = withContext(Dispatchers.IO) { runBackup(context, uri, true) }
            busy = null
            toast(context, if (err == null) "Respaldo completado" else "Respaldo fallido: $err")
        }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; restoreFull = null }
    }

    SettingsScaffold("Respaldo y restauración", onBack) { mod ->
        Column(modifier = mod) {
            NavRow("Respaldo básico (solo home)", "Guarda un .tar.gz de tu directorio home") { basicSaver.launch("termux-home-backup.tar.gz") }
            NavRow("Respaldo completo (home + usr)", "Guarda un .tar.gz de home y usr") { fullSaver.launch("termux-full-backup.tar.gz") }
            HorizontalDivider()
            NavRow("Restaurar desde respaldo", "Elige un .tar.gz para restaurar") { restorePicker.launch(arrayOf("*/*")) }
        }
    }

    busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.size(16.dp))
                    Text(msg)
                }
            },
        )
    }

    // Restaurar: elegir tipo
    if (restoreUri != null && restoreFull == null) {
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("¿Qué tipo de respaldo es este?") },
            text = { Text("Elige el tipo correcto para usar el método de restauración adecuado.") },
            confirmButton = { TextButton(onClick = { restoreFull = true }) { Text("Completo (home + usr)") } },
            dismissButton = { TextButton(onClick = { restoreFull = false }) { Text("Básico (solo home)") } },
        )
    }
    // Restaurar: confirmar
    if (restoreUri != null && restoreFull != null) {
        val uri = restoreUri!!
        val full = restoreFull!!
        AlertDialog(
            onDismissRequest = { restoreUri = null; restoreFull = null },
            title = { Text("Restaurar Termux") },
            text = { Text(if (full) "Esto sobrescribirá tus directorios home y usr. ¿Continuar?" else "Esto sobrescribirá tu directorio home. ¿Continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null; restoreFull = null
                    scope.launch {
                        busy = "Restaurando…"
                        val err = withContext(Dispatchers.IO) { runRestore(context, uri, full) }
                        busy = null
                        toast(context, if (err == null) "Restauración completada" else "Restauración fallida: $err")
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null; restoreFull = null }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------- Termux (upstream)

@Composable
private fun TermuxScreen(onBack: () -> Unit, onNav: (Route) -> Unit) {
    SettingsScaffold("Termux", onBack) { mod ->
        Column(modifier = mod) {
            NavRow("Depuración", "Nivel de log y opciones de depuración") { onNav(Route.DEBUGGING) }
            NavRow("E/S de terminal", "Comportamiento del teclado virtual") { onNav(Route.TERMINAL_IO) }
            NavRow("Vista de terminal", "Ajuste de márgenes del terminal") { onNav(Route.TERMINAL_VIEW) }
        }
    }
}

@Composable
private fun TerminalIOScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TermuxAppSharedPreferences.build(context, true) }
    SettingsScaffold("E/S de terminal", onBack) { mod ->
        Column(modifier = mod) {
            if (prefs == null) { Text("No disponible", modifier = Modifier.padding(16.dp)); return@Column }
            CategoryHeader("Teclado")
            var soft by remember { mutableStateOf(prefs.isSoftKeyboardEnabled) }
            SwitchRow("Teclado virtual activado", "Mostrar el teclado en pantalla", soft) { soft = it; prefs.setSoftKeyboardEnabled(it) }
            var softNoHw by remember { mutableStateOf(prefs.isSoftKeyboardEnabledOnlyIfNoHardware) }
            SwitchRow("Solo si no hay teclado físico", "Ocultar el teclado virtual cuando hay un teclado físico conectado", softNoHw) { softNoHw = it; prefs.setSoftKeyboardEnabledOnlyIfNoHardware(it) }
        }
    }
}

@Composable
private fun TerminalViewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TermuxAppSharedPreferences.build(context, true) }
    SettingsScaffold("Vista de terminal", onBack) { mod ->
        Column(modifier = mod) {
            if (prefs == null) { Text("No disponible", modifier = Modifier.padding(16.dp)); return@Column }
            CategoryHeader("Vista")
            var margin by remember { mutableStateOf(prefs.isTerminalMarginAdjustmentEnabled) }
            SwitchRow("Ajuste de márgenes del terminal", "Ajustar automáticamente los márgenes para evitar esquinas redondeadas o recortes", margin) { margin = it; prefs.setTerminalMarginAdjustment(it) }
        }
    }
}

@Composable
private fun DebuggingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TermuxAppSharedPreferences.build(context, true) }
    SettingsScaffold("Depuración", onBack) { mod ->
        Column(modifier = mod) {
            if (prefs == null) { Text("No disponible", modifier = Modifier.padding(16.dp)); return@Column }
            CategoryHeader("Registro")
            LogLevelRow(context, prefs.logLevel) { prefs.setLogLevel(context, it) }
            var keyLog by remember { mutableStateOf(prefs.isTerminalViewKeyLoggingEnabled) }
            SwitchRow("Registro de teclas de la vista de terminal", "Registrar eventos de teclas (detallado)", keyLog) { keyLog = it; prefs.setTerminalViewKeyLoggingEnabled(it) }
            var pluginErr by remember { mutableStateOf(prefs.arePluginErrorNotificationsEnabled(false)) }
            SwitchRow("Notificaciones de errores de plugins", null, pluginErr) { pluginErr = it; prefs.setPluginErrorNotificationsEnabled(it) }
            var crash by remember { mutableStateOf(prefs.areCrashReportNotificationsEnabled(false)) }
            SwitchRow("Notificaciones de reportes de fallos", null, crash) { crash = it; prefs.setCrashReportNotificationsEnabled(it) }
        }
    }
}

// ---------------------------------------------------------------- Plugins

private enum class Plugin { API, FLOAT, TASKER, WIDGET }

@Composable
private fun PluginScreen(title: String, plugin: Plugin, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsScaffold(title, onBack) { mod ->
        Column(modifier = mod) {
            CategoryHeader("Registro")
            when (plugin) {
                Plugin.API -> {
                    val p = remember { TermuxAPIAppSharedPreferences.build(context, true) }
                    if (p != null) LogLevelRow(context, p.getLogLevel(true)) { p.setLogLevel(context, it, true) }
                }
                Plugin.FLOAT -> {
                    val p = remember { TermuxFloatAppSharedPreferences.build(context, true) }
                    if (p != null) {
                        LogLevelRow(context, p.getLogLevel(true)) { p.setLogLevel(context, it, true) }
                        var keyLog by remember { mutableStateOf(p.isTerminalViewKeyLoggingEnabled(true)) }
                        SwitchRow("Registro de teclas de la vista de terminal", null, keyLog) { keyLog = it; p.setTerminalViewKeyLoggingEnabled(it, true) }
                    }
                }
                Plugin.TASKER -> {
                    val p = remember { TermuxTaskerAppSharedPreferences.build(context, true) }
                    if (p != null) LogLevelRow(context, p.getLogLevel(true)) { p.setLogLevel(context, it, true) }
                }
                Plugin.WIDGET -> {
                    val p = remember { TermuxWidgetAppSharedPreferences.build(context, true) }
                    if (p != null) LogLevelRow(context, p.getLogLevel(true)) { p.setLogLevel(context, it, true) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

private fun isDonateVisible(context: Context): Boolean {
    val digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context) ?: return true
    val apkRelease = TermuxUtils.getAPKRelease(digest)
    return !(apkRelease == null || apkRelease == TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST)
}

private fun openAbout(context: Context) {
    val about = StringBuilder()
    about.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES))
    about.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true))
    about.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context))

    val userActionName = UserAction.ABOUT.getName()
    val reportInfo = ReportInfo(userActionName, TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, "About")
    reportInfo.setReportString(about.toString())
    reportInfo.setReportSaveFileLabelAndPath(
        userActionName,
        Environment.getExternalStorageDirectory().toString() + "/" +
            FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true),
    )
    ReportActivity.startReportActivity(context, reportInfo)
}

private fun runBackup(context: Context, uri: Uri, full: Boolean): String? {
    return try {
        val cmd = if (full) arrayOf(
            "/data/data/com.termux/files/usr/bin/tar", "-zcf", "-",
            "-C", "/data/data/com.termux/files", "./home", "./usr",
        ) else arrayOf(
            "/data/data/com.termux/files/usr/bin/tar", "-zcf", "-",
            "-C", "/data/data/com.termux/files/home", ".",
        )
        val p = Runtime.getRuntime().exec(cmd)
        p.inputStream.use { input ->
            context.contentResolver.openOutputStream(uri).use { out ->
                if (out == null) return "No se pudo abrir el archivo de salida"
                input.copyTo(out)
            }
        }
        val errText = readStream(p.errorStream)
        val exit = p.waitFor()
        if (exit != 0) (errText.ifEmpty { "tar terminó con el código $exit" }) else null
    } catch (e: Exception) {
        e.message ?: "error"
    }
}

private fun runRestore(context: Context, fileUri: Uri, full: Boolean): String? {
    return try {
        val filePath: String = if (fileUri.scheme == "content") {
            val tmp = File(context.cacheDir, "restore_tmp.tar.gz")
            context.contentResolver.openInputStream(fileUri).use { input ->
                if (input == null) return "No se pudo abrir el archivo de entrada"
                FileOutputStream(tmp).use { out -> input.copyTo(out) }
            }
            tmp.absolutePath
        } else {
            fileUri.path ?: return "Ruta no válida"
        }
        val p = if (full) Runtime.getRuntime().exec(arrayOf(
            "/data/data/com.termux/files/usr/bin/tar", "-zxvf", filePath,
            "-C", "/data/data/com.termux/files", "--recursive-unlink", "--preserve-permissions",
        )) else Runtime.getRuntime().exec(arrayOf(
            "/data/data/com.termux/files/usr/bin/tar", "-zxvf", filePath,
            "-C", "/data/data/com.termux/files/home",
        ))
        val errText = readStream(p.errorStream)
        val exit = p.waitFor()
        if (exit != 0) errText else null
    } catch (e: Exception) {
        e.message ?: "error"
    }
}

private fun readStream(input: InputStream): String {
    val baos = ByteArrayOutputStream()
    val buf = ByteArray(4096)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        baos.write(buf, 0, n)
    }
    return baos.toString()
}
