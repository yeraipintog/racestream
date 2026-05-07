/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 06-05-2026
 * @description Servicio de envio SMTP para mensajes de contacto guardados en RaceStream
 */
package com.yerai.racestream.service;

import com.yerai.racestream.model.ContactMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class ContactMailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String supportEmail;
    private final String fromEmail;
    private final boolean mailEnabled;

    public ContactMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${racestream.contact.support-email:yeraigonzalez100@gmail.com}") String supportEmail,
            @Value("${spring.mail.username:}") String fromEmail,
            @Value("${racestream.contact.mail-enabled:false}") boolean mailEnabled) {
        this.mailSenderProvider = mailSenderProvider;
        this.supportEmail = supportEmail;
        this.fromEmail = fromEmail;
        this.mailEnabled = mailEnabled;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 06-05-2026
     * @description Envia por email un mensaje de contacto si SMTP esta configurado
     * @param message Mensaje persistido
     * @return true si el correo se envio realmente
     */
    public boolean send(ContactMessage message) {
        if (!mailEnabled) return false;
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) throw new IllegalStateException("SMTP no configurado");

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(supportEmail);
        if (!fromEmail.isBlank()) email.setFrom(fromEmail);
        email.setReplyTo(message.getUser().getEmail());
        email.setSubject("[RaceStream] " + message.getSubject());
        email.setText("""
                Nuevo mensaje de contacto RaceStream

                Usuario: %s
                Email: %s
                Asunto: %s

                %s
                """.formatted(
                message.getUser().getName(),
                message.getUser().getEmail(),
                message.getSubject(),
                message.getMessage()));
        mailSender.send(email);
        return true;
    }

    public String getSupportEmail() {
        return supportEmail;
    }
}
