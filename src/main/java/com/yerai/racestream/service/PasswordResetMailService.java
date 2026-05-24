/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.1.0
 * @created 07-05-2026
 * @description Servicio SMTP para enviar enlaces de restablecimiento de contraseña de RaceStream
 */
package com.yerai.racestream.service;

import com.yerai.racestream.model.AppUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetMailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromEmail;
    private final boolean mailEnabled;

    public PasswordResetMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.username:}") String fromEmail,
            @Value("${racestream.password-reset.mail-enabled:${racestream.contact.mail-enabled:false}}") boolean mailEnabled) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromEmail = fromEmail;
        this.mailEnabled = mailEnabled;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.1.0
     * @created 07-05-2026
     * @modified 24-05-2026
     * @description Envía un enlace de recuperación al email del usuario si SMTP está activo
     * @param user Usuario destinatario
     * @param resetUrl Enlace seguro con token temporal
     * @return true si el correo salió por SMTP
     */
    public boolean sendResetLink(AppUser user, String resetUrl) {
        if (!mailEnabled) return false;
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) return false;

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(user.getEmail());
        if (!fromEmail.isBlank()) email.setFrom(fromEmail);
        email.setSubject("[RaceStream] Restablece tu contraseña");
        email.setText("""
                RaceStream
                Restablecimiento de contraseña

                Hola, %s:

                Hemos recibido una solicitud para restablecer tu contraseña de RaceStream.
                Usa este enlace durante los próximos 30 minutos:

                %s

                Si no has pedido este cambio, ignora este correo. Por seguridad, no compartas este enlace con nadie.

                --
                Equipo de RaceStream
                Este mensaje se ha generado automáticamente. No respondas a este correo si no necesitas soporte.
                """.formatted(user.getName(), resetUrl));
        mailSender.send(email);
        return true;
    }
}
