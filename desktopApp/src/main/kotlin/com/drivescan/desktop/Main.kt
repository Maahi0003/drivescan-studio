package com.drivescan.desktop

import com.drivescan.shared.DriveApi
import com.drivescan.shared.InMemoryScanStore
import com.drivescan.shared.PinHasher
import com.drivescan.shared.VaultTemplates
import com.drivescan.shared.platformName
import java.awt.BorderLayout
import java.awt.GraphicsEnvironment
import javax.swing.DefaultListModel
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * Desktop entry point. Runs the SAME shared module as the Android app.
 * On a real desktop it opens a window; headless (CI/build machine) it runs
 * a console self-test of the shared logic instead.
 */
fun main() {
    if (GraphicsEnvironment.isHeadless()) {
        runConsoleDemo()
    } else {
        openWindow()
    }
}

private fun openWindow() {
    val store = InMemoryScanStore()
    val frame = JFrame("DriveScan Studio — Desktop (Phase A)").apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        layout = BorderLayout()
        setSize(560, 480)
    }
    val info = JTextArea().apply {
        isEditable = false
        val sb = StringBuilder()
        sb.append("Platform: ${platformName()}\nWorkspace: DriveScan_Workspace\n\nFolders:\n")
        for (f in store.folders()) sb.append("  ${f.name}\n")
        sb.append("\nDocuments:\n")
        for (d in store.documents()) sb.append("  ${d.name} (${d.pageCount} page)\n")
        text = sb.toString()
    }
    val listModel = DefaultListModel<String>()
    for (t in VaultTemplates.all) listModel.addElement("${t.displayName} — ${t.category}")
    frame.add(JScrollPane(info), BorderLayout.CENTER)
    frame.add(JScrollPane(JList(listModel)), BorderLayout.EAST)
    frame.isVisible = true
}

private fun runConsoleDemo() {
    val store = InMemoryScanStore()
    println("=== DriveScan Studio — Desktop self-test (shared module) ===")
    println("Platform: ${platformName()}")
    println("Folders: ${store.folders().joinToString { it.name }}")
    println("Documents: ${store.documents().joinToString { "${it.name} (${it.pageCount}p)" }}")
    println("Vault documents: ${store.vaultDocuments().joinToString { it.name }}")
    println("Vault templates (${VaultTemplates.all.size}): ${VaultTemplates.all.joinToString { it.displayName }}")

    val record = PinHasher.newRecord("2468")
    println("PIN record created: ${record.take(24)}...")
    println("PIN verify correct (2468): ${PinHasher.verify("2468", record)}")
    println("PIN verify wrong   (0000): ${PinHasher.verify("0000", record)}")

    val drive = DriveApi { null }
    println("Drive connected (no token): ${drive.isConnected}")
    println("Drive children query: ${drive.childrenQuery(null)}")
    println("Drive folder query: ${drive.folderSearchQuery(DriveApi.WORKSPACE_FOLDER_NAME, null)}")
    val sample = """{"files":[{"id":"abc123XYZ","name":"DriveScan_Workspace"}]}"""
    println("Drive extractFirstFileId: ${drive.extractFirstFileId(sample)}")
    println("=== self-test done ===")
}
