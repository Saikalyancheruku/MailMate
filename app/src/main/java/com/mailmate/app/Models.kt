package com.mailmate.app

data class SmtpConfig(
    val host: String = "smtp.gmail.com",
    val port: String = "587",
    val email: String = "",
    val appPassword: String = ""
)

data class MailTemplate(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val subject: String,
    val body: String
)

data class ResumeFile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val fileName: String
)

data class SendRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val recipient: String,
    val subject: String,
    val status: String,
    val details: String = ""
)
