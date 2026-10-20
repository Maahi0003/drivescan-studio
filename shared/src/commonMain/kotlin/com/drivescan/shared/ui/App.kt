package com.drivescan.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.drivescan.shared.InMemoryScanStore
import com.drivescan.shared.PinHasher
import com.drivescan.shared.ScanStore
import com.drivescan.shared.VaultTemplates
import com.drivescan.shared.platformName

/**
 * The shared Compose UI — the whole point of the KMP rewrite: this one
 * composable tree is what both the Android app and the desktop app render
 * (each platform entry point just hosts it). Phase A keeps it a skeleton:
 * dashboard + vault lock; scanner arrives in Phase B.
 */
@Composable
fun DriveScanApp(store: ScanStore = remember { InMemoryScanStore() }) {
    MaterialTheme {
        var pinRecord by remember { mutableStateOf<String?>(null) }
        var pinInput by remember { mutableStateOf("") }
        var unlocked by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("DriveScan Studio", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Kotlin Multiplatform · Phase A · ${platformName()}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))

            Text("Workspace", style = MaterialTheme.typography.titleMedium)
            Text("Root folder: DriveScan_Workspace")
            for (folder in store.folders()) Text("📁 ${folder.name}")
            for (doc in store.documents()) {
                Text("📄 ${doc.name} — ${doc.pageCount} page(s)${if (doc.inVault) " · vault" else ""}")
            }
            Spacer(Modifier.height(20.dp))

            Text("Personal Vault", style = MaterialTheme.typography.titleMedium)
            if (!unlocked) {
                Text(if (pinRecord == null) "🔓 Pehle ek PIN set karo" else "🔒 Vault locked hai")
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it },
                    label = { Text("4+ digit PIN") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Button(onClick = {
                    val pin = pinInput.trim()
                    when {
                        pin.length < 4 -> message = "PIN kam se kam 4 digit ka ho"
                        pinRecord == null -> {
                            pinRecord = PinHasher.newRecord(pin)
                            pinInput = ""
                            message = "PIN set ho gaya"
                        }
                        PinHasher.verify(pin, pinRecord!!) -> {
                            unlocked = true
                            pinInput = ""
                            message = ""
                        }
                        else -> message = "Galat PIN"
                    }
                }) {
                    Text(if (pinRecord == null) "PIN Set karo" else "Unlock karo")
                }
                if (message.isNotEmpty()) Text(message)
            } else {
                Text("🔓 Vault unlocked")
                for (t in VaultTemplates.all) Text("• ${t.displayName} — ${t.category}")
                Button(onClick = { unlocked = false }) { Text("Lock karo") }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Scanner Phase B mein, Drive live sync Phase C mein — isi shared code par.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
