package com.mailmate.app

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var store: LocalStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LocalStore(this)
        setContent { MaterialTheme { MailMateApp(store, ::importResume) } }
    }

    private fun importResume(uri: Uri): ResumeFile {
        val displayName = contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        } ?: "resume.pdf"
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "resume.pdf" }
        val target = File(store.resumeDirectory(), "${UUID.randomUUID()}_$safeName")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file." }
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return ResumeFile(name = displayName.substringBeforeLast('.', displayName), fileName = target.name)
    }
}

private enum class Page(val label: String, val symbol: String) {
    SMTP("SMTP", "⚙"), TEMPLATES("Templates", "✎"), RESUMES("Resumes", "▤"), SEND("Send", "✉"), HISTORY("History", "◷")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MailMateApp(store: LocalStore, onImportResume: (Uri) -> ResumeFile) {
    var page by remember { mutableStateOf(Page.SEND) }
    var config by remember { mutableStateOf(store.loadConfig()) }
    var templates by remember { mutableStateOf(store.loadTemplates()) }
    var resumes by remember { mutableStateOf(store.loadResumes()) }
    var history by remember { mutableStateOf(store.loadHistory()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var recipientsText by remember { mutableStateOf("") }
    var selectedTemplateId by remember { mutableStateOf(templates.firstOrNull()?.id.orEmpty()) }
    var selectedResumeId by remember { mutableStateOf(resumes.firstOrNull()?.id.orEmpty()) }
    var role by remember { mutableStateOf("Software Developer") }
    var yourName by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val imported = onImportResume(uri)
                resumes = resumes + imported
                store.saveResumes(resumes)
                selectedResumeId = imported.id
                message = "Resume added: ${imported.name}"
            } catch (e: Exception) { message = "Could not import resume: ${e.message ?: "unknown error"}" }
        }
    }

    val selectedTemplate = templates.firstOrNull { it.id == selectedTemplateId } ?: templates.firstOrNull()
    val selectedResume = resumes.firstOrNull { it.id == selectedResumeId } ?: resumes.firstOrNull()
    val recipients = recipientsText.split(',', ';', '\n', '\t', ' ').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) }

    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("MailMate", fontWeight = FontWeight.Bold); Text("Private, on-device SMTP mailer", style = MaterialTheme.typography.labelSmall) } }) },
        bottomBar = {
            NavigationBar {
                Page.values().forEach { item ->
                    NavigationBarItem(selected = page == item, onClick = { page = item }, icon = { Text(item.symbol) }, label = { Text(item.label) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (message.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(message, Modifier.weight(1f)); TextButton(onClick = { message = "" }) { Text("Dismiss") } } }
            }
            when (page) {
                Page.SMTP -> SmtpScreen(
                    config = config,
                    onChange = { config = it },
                    busy = busy,
                    onSave = {
                        if (!config.email.contains('@') || config.appPassword.isBlank()) message = "Enter your Gmail address and Google App Password."
                        else if (config.host.isBlank() || config.port.toIntOrNull() == null) message = "Check the SMTP host and port."
                        else { store.saveConfig(config); message = "SMTP settings saved on this device." }
                    },
                    onTest = {
                        store.saveConfig(config)
                        busy = true; message = "Testing SMTP connection…"
                        scope.launch {
                            val result = runCatching { withContext(Dispatchers.IO) { SmtpSender.testConnection(config) } }
                            busy = false
                            message = if (result.isSuccess) "SMTP connection successful." else "Connection failed: ${result.exceptionOrNull()?.message ?: "check settings"}"
                        }
                    }
                )
                Page.TEMPLATES -> TemplatesScreen(templates, onSave = { updated -> templates = updated; store.saveTemplates(updated); if (selectedTemplateId.isBlank()) selectedTemplateId = updated.firstOrNull()?.id.orEmpty(); message = "Templates saved locally." })
                Page.RESUMES -> ResumesScreen(resumes, onAdd = { importLauncher.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }, onDelete = { resume ->
                    File(store.resumeDirectory(), resume.fileName).delete()
                    resumes = resumes.filterNot { it.id == resume.id }; store.saveResumes(resumes)
                    if (selectedResumeId == resume.id) selectedResumeId = resumes.firstOrNull()?.id.orEmpty()
                    message = "Resume removed from this device."
                })
                Page.SEND -> {
                    SectionTitle("Send job applications")
                    Text("Each address receives a separate email. Recipients cannot see one another.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(recipientsText, { recipientsText = it }, label = { Text("Recipient email(s)") }, placeholder = { Text("person@example.com, another@example.com") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    Text("Separate addresses with commas, semicolons, spaces, or new lines.", style = MaterialTheme.typography.bodySmall)
                    if (templates.isEmpty()) Text("Add an email template first.") else {
                        ChoiceField("Email template", templates.firstOrNull { it.id == selectedTemplateId }?.name ?: "Choose template", templates.map { it.id to it.name }, { selectedTemplateId = it })
                    }
                    if (resumes.isEmpty()) {
                        Text("No resume added yet.")
                        OutlinedButton(onClick = { page = Page.RESUMES }) { Text("Add a resume") }
                    } else ChoiceField("Resume attachment", resumes.firstOrNull { it.id == selectedResumeId }?.name ?: "Choose resume", resumes.map { it.id to it.name }, { selectedResumeId = it })
                    OutlinedTextField(role, { role = it }, label = { Text("Role / position ({{role}})") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(yourName, { yourName = it }, label = { Text("Your name ({{your_name}})") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    selectedTemplate?.let { template ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Preview", fontWeight = FontWeight.SemiBold)
                                Text("Subject: ${template.subject.replace("{{role}}", role).replace("{{your_name}}", yourName)}")
                                Text(template.body.replace("{{role}}", role).replace("{{your_name}}", yourName).take(360))
                            }
                        }
                    }
                    Button(onClick = {
                        when {
                            !config.email.contains('@') || config.appPassword.isBlank() -> { page = Page.SMTP; message = "Set up and save SMTP first." }
                            recipients.isEmpty() -> message = "Enter at least one recipient email address."
                            recipients.any { !android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches() } -> message = "One or more email addresses look invalid. Check the list."
                            selectedTemplate == null -> { page = Page.TEMPLATES; message = "Create a template first." }
                            selectedResume == null -> { page = Page.RESUMES; message = "Add a resume first." }
                            yourName.isBlank() -> message = "Enter your name before sending."
                            else -> showConfirm = true
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Sending…" else "Review and send (${recipients.size})") }
                    Text("Tip: send a test to your own address first. Sending limits are controlled by Google and your account type.", style = MaterialTheme.typography.bodySmall)
                }
                Page.HISTORY -> {
                    SectionTitle("Recent sending history")
                    if (history.isEmpty()) Text("Emails sent from this app will appear here.")
                    history.forEach { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.status, fontWeight = FontWeight.Bold, color = if (item.status == "SENT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    Text(SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(item.timestamp)), style = MaterialTheme.typography.labelSmall)
                                }
                                Text(item.recipient)
                                Text(item.subject, style = MaterialTheme.typography.bodyMedium)
                                if (item.details.isNotBlank()) Text(item.details, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Send ${recipients.size} separate email(s)?") },
            text = { Text("Each email will go to one recipient and include ${selectedResume?.name ?: "the selected resume"}. Check the addresses and preview before continuing.") },
            confirmButton = {
                Button(onClick = {
                    showConfirm = false
                    val template = selectedTemplate ?: return@Button
                    val resume = selectedResume ?: return@Button
                    val attachment = File(store.resumeDirectory(), resume.fileName)
                    val configSnapshot = config
                    val recipientSnapshot = recipients
                    val subject = template.subject.replace("{{role}}", role).replace("{{your_name}}", yourName)
                    val body = template.body.replace("{{role}}", role).replace("{{your_name}}", yourName)
                    busy = true; message = "Sending 0/${recipientSnapshot.size}…"
                    scope.launch {
                        var sent = 0
                        recipientSnapshot.forEachIndexed { index, address ->
                            val result = runCatching { withContext(Dispatchers.IO) { SmtpSender.send(configSnapshot, address, subject, body, attachment) } }
                            val record = if (result.isSuccess) SendRecord(recipient = address, subject = subject, status = "SENT") else SendRecord(recipient = address, subject = subject, status = "FAILED", details = (result.exceptionOrNull()?.message ?: "Send failed").take(220))
                            store.addHistory(record)
                            if (result.isSuccess) sent++
                            message = "Processed ${index + 1}/${recipientSnapshot.size} — $sent sent."
                            history = store.loadHistory()
                        }
                        busy = false
                        message = "Finished: $sent of ${recipientSnapshot.size} email(s) sent. See History for results."
                        page = Page.HISTORY
                    }
                }) { Text("Send emails") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SmtpScreen(config: SmtpConfig, onChange: (SmtpConfig) -> Unit, busy: Boolean, onSave: () -> Unit, onTest: () -> Unit) {
    SectionTitle("Gmail SMTP settings")
    Text("These settings and your app password are saved on this phone only.", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(config.host, { onChange(config.copy(host = it)) }, label = { Text("SMTP host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(config.port, { onChange(config.copy(port = it)) }, label = { Text("SMTP port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(config.email, { onChange(config.copy(email = it)) }, label = { Text("Gmail address") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(config.appPassword, { onChange(config.copy(appPassword = it)) }, label = { Text("Google App Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
    Text("Default: smtp.gmail.com · port 587 · STARTTLS", style = MaterialTheme.typography.bodySmall)
    Text("Create an App Password in your Google Account after enabling 2-Step Verification. Do not enter your normal Google password.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
    OutlinedButton(onClick = onTest, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Testing…" else "Test SMTP connection") }
}

@Composable
private fun TemplatesScreen(templates: List<MailTemplate>, onSave: (List<MailTemplate>) -> Unit) {
    var selectedId by remember(templates) { mutableStateOf(templates.firstOrNull()?.id.orEmpty()) }
    var name by remember(templates) { mutableStateOf(templates.firstOrNull()?.name ?: "") }
    var subject by remember(templates) { mutableStateOf(templates.firstOrNull()?.subject ?: "") }
    var body by remember(templates) { mutableStateOf(templates.firstOrNull()?.body ?: "") }
    SectionTitle("Email templates")
    ChoiceField("Edit template", templates.firstOrNull { it.id == selectedId }?.name ?: "New template", templates.map { it.id to it.name } + ("__new__" to "+ New template"), { id ->
        if (id == "__new__") { selectedId = ""; name = ""; subject = "Application for {{role}}"; body = "Hello,\n\nI am interested in the {{role}} position. Please find my resume attached.\n\nRegards,\n{{your_name}}" }
        else templates.firstOrNull { it.id == id }?.let { selectedId = it.id; name = it.name; subject = it.subject; body = it.body }
    })
    OutlinedTextField(name, { name = it }, label = { Text("Template name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(subject, { subject = it }, label = { Text("Email subject") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(body, { body = it }, label = { Text("Email body") }, modifier = Modifier.fillMaxWidth(), minLines = 7)
    Text("Placeholders supported: {{role}} and {{your_name}}.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = {
        if (name.isBlank() || subject.isBlank() || body.isBlank()) return@Button
        val entry = MailTemplate(id = selectedId.ifBlank { UUID.randomUUID().toString() }, name = name.trim(), subject = subject, body = body)
        val updated = if (templates.any { it.id == entry.id }) templates.map { if (it.id == entry.id) entry else it } else templates + entry
        selectedId = entry.id
        onSave(updated)
    }, modifier = Modifier.fillMaxWidth()) { Text("Save template") }
    if (selectedId.isNotBlank()) OutlinedButton(onClick = {
        val updated = templates.filterNot { it.id == selectedId }
        selectedId = updated.firstOrNull()?.id.orEmpty(); name = updated.firstOrNull()?.name.orEmpty(); subject = updated.firstOrNull()?.subject.orEmpty(); body = updated.firstOrNull()?.body.orEmpty()
        onSave(updated)
    }, modifier = Modifier.fillMaxWidth()) { Text("Delete selected template") }
}

@Composable
private fun ResumesScreen(resumes: List<ResumeFile>, onAdd: () -> Unit, onDelete: (ResumeFile) -> Unit) {
    SectionTitle("Resume versions")
    Text("Choose PDF or Word files. Copies are stored in the app's private storage on this phone.", style = MaterialTheme.typography.bodyMedium)
    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Import resume from phone") }
    if (resumes.isEmpty()) Text("No resumes added yet.")
    resumes.forEach { resume ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(resume.name, fontWeight = FontWeight.SemiBold)
                    Text(resume.fileName, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onDelete(resume) }) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun ChoiceField(label: String, selectedLabel: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedLabel, Modifier.weight(1f)); Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(id) }) }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}
