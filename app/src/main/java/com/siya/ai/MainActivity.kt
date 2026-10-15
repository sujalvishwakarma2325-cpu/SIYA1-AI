package com.siya.ai

import android.Manifest
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

data class Msg(val fromUser: Boolean, val text: String)

class SiyaVM(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val agent = Agent(app, store)
    private val voice = Voice(app, store)

    val msgs = mutableStateListOf<Msg>()
    var name by mutableStateOf(store.assistantName)
    var status by mutableStateOf("taiyaar")
    var partial by mutableStateOf("")
    var listening by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var confirmText by mutableStateOf<String?>(null)
    var showSettings by mutableStateOf(store.apiKey.isBlank())
    var conversationMode by mutableStateOf(store.conversationMode)

    private var pending: CompletableDeferred<Boolean>? = null

    init {
        msgs.add(Msg(false, "Namaste! Main ${store.assistantName} hoon. Mic dabakar bolo ya likho."))
    }

    fun refresh() {
        name = store.assistantName
        conversationMode = store.conversationMode
    }

    fun setConversation(on: Boolean) {
        conversationMode = on
        store.conversationMode = on
    }

    private suspend fun confirm(question: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        pending = d
        confirmText = question
        voice.speak(question)
        val ok = d.await()
        confirmText = null
        return ok
    }

    fun answer(ok: Boolean) {
        voice.stopSpeaking()
        pending?.complete(ok)
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        msgs.add(Msg(true, t))
        busy = true
        status = "soch rahi hoon"
        viewModelScope.launch {
            val reply = try {
                agent.ask(t, { q -> confirm(q) }, { s -> status = s })
            } catch (e: Exception) {
                "Dikkat aayi: ${e.message}"
            }
            msgs.add(Msg(false, reply))
            busy = false
            status = "taiyaar"
            refresh()
            voice.speak(reply) { if (conversationMode) startListening() }
        }
    }

    fun startListening() {
        if (busy) return
        voice.stopSpeaking()
        partial = ""
        listening = true
        status = "sun rahi hoon"
        voice.listen(
            { p -> partial = p },
            { t ->
                listening = false
                partial = ""
                if (t.isNotBlank()) send(t) else status = "taiyaar"
            },
            { m ->
                listening = false
                partial = ""
                status = if (m.isBlank()) "taiyaar" else m
            }
        )
    }

    fun stopListening() {
        voice.stopListening()
    }

    override fun onCleared() {
        voice.shutdown()
    }
}

class MainActivity : ComponentActivity() {
    private val vm: SiyaVM by viewModels()
    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askPerms.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS
            )
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38E1FF),
                    onPrimary = Color(0xFF03141A),
                    background = Color(0xFF050B10),
                    surface = Color(0xFF0B151C),
                    onBackground = Color(0xFFD8EEF5),
                    onSurface = Color(0xFFD8EEF5),
                    onSurfaceVariant = Color(0xFF6F97A6)
                )
            ) { SiyaScreen(vm) }
        }
    }
}

@Composable
fun SiyaScreen(vm: SiyaVM) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.msgs.size) {
        if (vm.msgs.isNotEmpty()) listState.animateScrollToItem(vm.msgs.size - 1)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        vm.name.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(vm.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { vm.showSettings = true }) { Text("Settings") }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.msgs) { m -> Bubble(m) }
            }

            if (vm.partial.isNotBlank()) {
                Text(vm.partial, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Kuch bhi bolo ya likho") },
                    singleLine = true
                )
                Button(onClick = { vm.send(input); input = "" }, enabled = !vm.busy) { Text("Bhejo") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (vm.listening) vm.stopListening() else vm.startListening() },
                    enabled = !vm.busy
                ) { Text(if (vm.listening) "Roko" else "Mic dabao") }
                Spacer(Modifier.weight(1f))
                Text("Baatcheet mode", fontSize = 12.sp)
                Switch(checked = vm.conversationMode, onCheckedChange = { vm.setConversation(it) })
            }
        }
    }

    vm.confirmText?.let { q ->
        AlertDialog(
            onDismissRequest = { vm.answer(false) },
            title = { Text("${vm.name} puch rahi hai") },
            text = { Text(q) },
            confirmButton = { TextButton(onClick = { vm.answer(true) }) { Text("Haan") } },
            dismissButton = { TextButton(onClick = { vm.answer(false) }) { Text("Nahi") } }
        )
    }
    if (vm.showSettings) SettingsDialog(vm)
}

@Composable
fun Bubble(m: Msg) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (m.fromUser) Color(0xFF12566A) else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) { Text(m.text) }
    }
}

@Composable
fun SettingsDialog(vm: SiyaVM) {
    val s = vm.store
    var key by remember { mutableStateOf(s.apiKey) }
    var nm by remember { mutableStateOf(s.assistantName) }
    var lang by remember { mutableStateOf(s.language) }
    var voiceOn by remember { mutableStateOf(s.voiceReplies) }
    var extra by remember { mutableStateOf(s.extraInstructions) }

    AlertDialog(
        onDismissRequest = { vm.showSettings = false },
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("Claude API key") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true
                )
                OutlinedTextField(value = nm, onValueChange = { nm = it }, label = { Text("Assistant ka naam") }, singleLine = true)
                OutlinedTextField(value = lang, onValueChange = { lang = it }, label = { Text("Bhasha (hi-IN, en-IN)") }, singleLine = true)
                OutlinedTextField(value = extra, onValueChange = { extra = it }, label = { Text("Khaas nirdesh") })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bolkar jawab", Modifier.weight(1f))
                    Switch(checked = voiceOn, onCheckedChange = { voiceOn = it })
                }
                Text("Jodi hui skills: ${s.skillCount()}", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                s.apiKey = key
                s.assistantName = nm
                s.language = lang
                s.voiceReplies = voiceOn
                s.extraInstructions = extra
                vm.refresh()
                vm.showSettings = false
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { vm.showSettings = false }) { Text("Band") } }
    )
}
