package com.mailmate.app

import java.io.File
import java.util.Properties
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.activation.DataHandler
import javax.activation.FileDataSource

object SmtpSender {
    private fun properties(config: SmtpConfig): Properties = Properties().apply {
        put("mail.smtp.auth", "true")
        put("mail.smtp.host", config.host)
        put("mail.smtp.port", config.port)
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.starttls.required", "true")
        put("mail.smtp.ssl.enable", "false")
        put("mail.smtp.connectiontimeout", "20000")
        put("mail.smtp.timeout", "30000")
        put("mail.smtp.writetimeout", "30000")
        put("mail.smtp.ssl.checkserveridentity", "true")
    }

    fun testConnection(config: SmtpConfig) {
        require(config.email.contains("@")) { "Enter a valid Gmail address." }
        require(config.appPassword.isNotBlank()) { "Enter your Google App Password." }
        val session = Session.getInstance(properties(config))
        val transport = session.getTransport("smtp")
        try { transport.connect(config.host, config.port.toInt(), config.email, config.appPassword) }
        finally { try { transport.close() } catch (_: Exception) {} }
    }

    fun send(config: SmtpConfig, recipient: String, subject: String, body: String, attachment: File?) {
        val session = Session.getInstance(properties(config))
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(config.email))
        message.setRecipient(Message.RecipientType.TO, InternetAddress(recipient, true))
        message.subject = subject
        if (attachment == null) {
            message.setText(body, "UTF-8")
        } else {
            val multipart: Multipart = MimeMultipart()
            val textPart = MimeBodyPart().apply { setText(body, "UTF-8") }
            multipart.addBodyPart(textPart)
            val attachmentPart = MimeBodyPart().apply {
                dataHandler = DataHandler(FileDataSource(attachment))
                fileName = attachment.name
            }
            multipart.addBodyPart(attachmentPart)
            message.setContent(multipart)
        }
        val transport = session.getTransport("smtp")
        try {
            transport.connect(config.host, config.port.toInt(), config.email, config.appPassword)
            transport.sendMessage(message, message.allRecipients)
        } finally { try { transport.close() } catch (_: Exception) {} }
    }
}
