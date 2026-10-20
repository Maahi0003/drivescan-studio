package com.drivescan.shared

/** The 10 pre-named Personal Vault slots, same set as the web app. */
object VaultTemplates {
    val all: List<VaultTemplate> = listOf(
        VaultTemplate("aadhaar", "Aadhaar Card", "Identity"),
        VaultTemplate("pan", "PAN Card", "Identity"),
        VaultTemplate("passport", "Passport", "Identity"),
        VaultTemplate("driving-licence", "Driving Licence", "Identity"),
        VaultTemplate("voter-id", "Voter ID Card", "Identity"),
        VaultTemplate("ration-card", "Ration Card", "Household"),
        VaultTemplate("marksheet-10", "Class 10 Marksheet", "Education"),
        VaultTemplate("marksheet-12", "Class 12 Marksheet", "Education"),
        VaultTemplate("degree", "Degree Certificate", "Education"),
        VaultTemplate("bank-passbook", "Bank Passbook", "Finance")
    )

    fun byId(id: String): VaultTemplate? = all.firstOrNull { it.id == id }
}
