package com.example.AppSegurity.Services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    /**
     * Todo texto que no haya escrito este archivo (nombre de una persona, el motivo libre que redacta un docente al
     * resolver una apelación, etc.) se escapa antes de entrar al HTML del correo. Sin esto, ese texto se insertaba
     * tal cual en el cuerpo del mensaje: alguien podía romper la plantilla o colar enlaces/estilos falsos dentro de
     * un correo que de verdad sale de la cuenta institucional — un vector de phishing cómodo si una cuenta se
     * compromete, aunque los clientes de correo ya bloquean la ejecución de JavaScript.
     */
    private static String escapar(String texto) {
        return texto == null ? "" : HtmlUtils.htmlEscape(texto);
    }

    @Async
    public void enviarResolucionApelacion(String correoEstudiante, String nombreEstudiante, String codigoExamen, boolean esAprobada, String motivoProfesor) {
        try {
            MimeMessage mensaje = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");

            String nombreEstudianteSeguro = escapar(nombreEstudiante);
            String codigoExamenSeguro = escapar(codigoExamen);
            String motivoProfesorSeguro = escapar(motivoProfesor);

            helper.setTo(correoEstudiante);
            helper.setSubject("Resolucion de Segunda Revision - Examen: " + codigoExamen);

            String colorEstado = esAprobada ? "#27AE60" : "#C0392B";
            String textoEstado = esAprobada ? "APROBADA (SANCION LEVANTADA)" : "RECHAZADA (SANCION MANTENIDA)";

            String parrafoContexto = esAprobada
                    ? "Tras una evaluaci&oacute;n exhaustiva de la evidencia t&eacute;cnica (grabaciones de c&aacute;mara, audio y monitoreo de perif&eacute;ricos) recopilada durante la sesi&oacute;n, el comit&eacute; ha determinado que las anomal&iacute;as detectadas por el sistema no constituyen una falta al reglamento estudiantil."
                    : "Tras una auditor&iacute;a exhaustiva de la evidencia forense digital (incluyendo an&aacute;lisis de comportamiento visual, procesos de escritorio en segundo plano y patrones auditivos), el comit&eacute; ha ratificado que existen pruebas contundentes de actividad fraudulenta que violan el reglamento de integridad acad&eacute;mica.";

            String htmlTemplate = "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 650px; margin: 0 auto; border: 1px solid #E0E0E0; border-radius: 4px; overflow: hidden; box-shadow: 0 4px 10px rgba(0,0,0,0.05);\">"
                    + "<div style=\"background-color: #003B13; padding: 25px 30px; text-align: center; border-bottom: 4px solid #F1C40F;\">"
                    + "<h2 style=\"color: #FFFFFF; margin: 0; font-size: 20px; font-weight: 600; letter-spacing: 0.5px;\">Comit&eacute; de Integridad Institucional</h2>"
                    + "<p style=\"color: #A9DFBF; margin: 5px 0 0 0; font-size: 13px;\">Universidad Popular del Cesar - Supervisi&oacute;n Acad&eacute;mica</p>"
                    + "</div>"
                    + "<div style=\"padding: 40px 35px; background-color: #FFFFFF;\">"
                    + "<p style=\"font-size: 15px; color: #2C3E50; margin-top: 0;\">Estimado/a estudiante <strong>" + nombreEstudianteSeguro + "</strong>,</p>"
                    + "<p style=\"font-size: 14px; color: #34495E; line-height: 1.6; text-align: justify;\">Le notificamos formalmente que su solicitud de apelaci&oacute;n y segunda revisi&oacute;n de novedades correspondiente al examen <strong>" + codigoExamenSeguro + "</strong> ha concluido su proceso de auditor&iacute;a por parte del cuerpo docente.</p>"
                    + "<p style=\"font-size: 14px; color: #34495E; line-height: 1.6; text-align: justify;\">" + parrafoContexto + "</p>"
                    + "<div style=\"margin: 30px 0; padding: 20px; border: 1px solid " + colorEstado + "; background-color: #F8F9F9; border-radius: 3px;\">"
                    + "<h3 style=\"margin-top: 0; color: " + colorEstado + "; font-size: 16px; text-transform: uppercase; letter-spacing: 1px;\">Veredicto Oficial: " + textoEstado + "</h3>"
                    + "<p style=\"margin-bottom: 5px; font-size: 13px; color: #7F8C8D; font-weight: 600; text-transform: uppercase;\">Argumento de la decisi&oacute;n:</p>"
                    + "<p style=\"margin: 0; font-size: 14px; color: #2C3E50; font-style: italic; line-height: 1.5;\">\"" + motivoProfesorSeguro + "\"</p>"
                    + "</div>"
                    + "<p style=\"font-size: 13px; color: #7F8C8D; line-height: 1.5; border-top: 1px solid #EEEEEE; padding-top: 20px; margin-top: 40px;\">De acuerdo con los estatutos de la universidad, esta decisi&oacute;n tiene car&aacute;cter definitivo. Si requiere asistencia adicional, por favor dir&iacute;jase a la decanatura correspondiente.</p>"
                    + "</div>"
                    + "<div style=\"background-color: #F2F4F4; padding: 15px 30px; text-align: left; font-size: 11px; color: #95A5A6; border-top: 1px solid #E5E7E9;\">"
                    + "Notificaci&oacute;n autogenerada. Por favor, no responda a este correo.<br>"
                    + "&copy; 2026 Universidad Popular del Cesar. Todos los derechos reservados."
                    + "</div>"
                    + "</div>";

            helper.setText(htmlTemplate, true);
            javaMailSender.send(mensaje);

        } catch (Exception e) {
            System.err.println("Error enviando el correo a: " + correoEstudiante);
            e.printStackTrace();
        }
    }

    @Async
    public void enviarCodigoVerificacion(String correo, String nombre, String codigoOtp, String rol) {
        System.out.println("[REGISTRO OTP] Solicitado para " + correo + " (" + rol + ")");
        try {
            MimeMessage mensaje = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");

            helper.setTo(correo);
            helper.setSubject("Código de Verificación - Registro UPC Proctor");

            String nombreDest = escapar((nombre != null && !nombre.isBlank()) ? nombre : "Usuario Institucional");
            String rolTexto = "PROFESOR".equalsIgnoreCase(rol) ? "Docente / Evaluador" : "Estudiante";

            String htmlTemplate = "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #E0E0E0; border-radius: 6px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.06);\">"
                    + "<div style=\"background-color: #003B13; padding: 25px 30px; text-align: center; border-bottom: 4px solid #F1C40F;\">"
                    + "<h2 style=\"color: #FFFFFF; margin: 0; font-size: 20px; font-weight: 600; letter-spacing: 0.5px;\">Universidad Popular del Cesar</h2>"
                    + "<p style=\"color: #A9DFBF; margin: 5px 0 0 0; font-size: 13px;\">Sistema de Supervisi&oacute;n e Integridad Acad&eacute;mica (UPC Proctor)</p>"
                    + "</div>"
                    + "<div style=\"padding: 35px 30px; background-color: #FFFFFF;\">"
                    + "<p style=\"font-size: 15px; color: #2C3E50; margin-top: 0;\">Estimado/a <strong>" + nombreDest + "</strong>,</p>"
                    + "<p style=\"font-size: 14px; color: #34495E; line-height: 1.6; text-align: justify;\">"
                    + "Has iniciado el proceso de registro institucional como <strong>" + rolTexto + "</strong> en la plataforma. Para verificar tu identidad y validar el acceso a tu casillero institucional, utiliza el siguiente c&oacute;digo de confirmaci&oacute;n de un solo uso (OTP):"
                    + "</p>"
                    + "<div style=\"margin: 25px auto; max-width: 280px; padding: 16px 20px; background-color: #F4FBF7; border: 2px dashed #196F3D; border-radius: 8px; text-align: center;\">"
                    + "<span style=\"font-family: 'Consolas', 'Courier New', monospace; font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #003B13;\">" + codigoOtp + "</span>"
                    + "</div>"
                    + "<p style=\"font-size: 13px; color: #7F8C8D; line-height: 1.5; text-align: center;\">"
                    + "⏰ Este c&oacute;digo vencer&aacute; autom&aacute;ticamente en <strong>10 minutos</strong>."
                    + "</p>"
                    + "<div style=\"margin-top: 25px; padding: 12px 16px; background-color: #FFF9E6; border-left: 4px solid #F1C40F; border-radius: 4px;\">"
                    + "<p style=\"font-size: 12px; color: #7D6608; margin: 0; line-height: 1.4;\">"
                    + "<strong>Aviso de Seguridad:</strong> Si t&uacute; no solicitaste este registro, por favor ignora este correo. Ning&uacute;n administrador de la universidad te pedir&aacute; jam&aacute;s este c&oacute;digo."
                    + "</p>"
                    + "</div>"
                    + "</div>"
                    + "<div style=\"background-color: #F8F9FA; padding: 15px 30px; text-align: center; font-size: 11px; color: #95A5A6; border-top: 1px solid #E5E7E9;\">"
                    + "Mensaje autogenerado por el sistema institucional. Por favor no responda a este correo.<br>"
                    + "&copy; 2026 Universidad Popular del Cesar &bull; Valledupar, Cesar, Colombia."
                    + "</div>"
                    + "</div>";

            helper.setText(htmlTemplate, true);
            javaMailSender.send(mensaje);
            System.out.println("[REGISTRO OTP] Correo enviado exitosamente a " + correo);
        } catch (Exception e) {
            System.err.println("[REGISTRO OTP ERROR] No se pudo despachar el correo SMTP a " + correo + ": " + e.getMessage());
        }
    }

    @Async
    public void enviarCodigoRecuperacion(String correo, String nombre, String codigoOtp) {
        System.out.println("[RECUPERACIÓN OTP] Solicitado para " + correo);
        try {
            MimeMessage mensaje = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");

            helper.setTo(correo);
            helper.setSubject("Restablecimiento de Contraseña - UPC Proctor");

            String nombreDest = escapar((nombre != null && !nombre.isBlank()) ? nombre : "Usuario Institucional");

            String htmlTemplate = "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #E0E0E0; border-radius: 6px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.06);\">"
                    + "<div style=\"background-color: #003B13; padding: 25px 30px; text-align: center; border-bottom: 4px solid #F1C40F;\">"
                    + "<h2 style=\"color: #FFFFFF; margin: 0; font-size: 20px; font-weight: 600; letter-spacing: 0.5px;\">Universidad Popular del Cesar</h2>"
                    + "<p style=\"color: #A9DFBF; margin: 5px 0 0 0; font-size: 13px;\">Recuperaci&oacute;n de Acceso Institucional</p>"
                    + "</div>"
                    + "<div style=\"padding: 35px 30px; background-color: #FFFFFF;\">"
                    + "<p style=\"font-size: 15px; color: #2C3E50; margin-top: 0;\">Estimado/a <strong>" + nombreDest + "</strong>,</p>"
                    + "<p style=\"font-size: 14px; color: #34495E; line-height: 1.6; text-align: justify;\">"
                    + "Hemos recibido una solicitud para restablecer la contrase&ntilde;a de tu cuenta en la plataforma. Para verificar tu identidad y asignar una nueva clave de acceso, utiliza el siguiente c&oacute;digo de un solo uso (OTP):"
                    + "</p>"
                    + "<div style=\"margin: 25px auto; max-width: 280px; padding: 16px 20px; background-color: #FFF9E6; border: 2px dashed #D4AC0D; border-radius: 8px; text-align: center;\">"
                    + "<span style=\"font-family: 'Consolas', 'Courier New', monospace; font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #7D6608;\">" + codigoOtp + "</span>"
                    + "</div>"
                    + "<p style=\"font-size: 13px; color: #7F8C8D; line-height: 1.5; text-align: center;\">"
                    + "⏰ Este c&oacute;digo expira en <strong>10 minutos</strong>."
                    + "</p>"
                    + "<div style=\"margin-top: 25px; padding: 12px 16px; background-color: #FADBD8; border-left: 4px solid #E74C3C; border-radius: 4px;\">"
                    + "<p style=\"font-size: 12px; color: #78281F; margin: 0; line-height: 1.4;\">"
                    + "<strong>Aviso de Seguridad:</strong> Si t&uacute; no solicitaste este cambio, por favor desestima este mensaje. Tu contrase&ntilde;a actual seguir&aacute; siendo segura."
                    + "</p>"
                    + "</div>"
                    + "</div>"
                    + "<div style=\"background-color: #F8F9FA; padding: 15px 30px; text-align: center; font-size: 11px; color: #95A5A6; border-top: 1px solid #E5E7E9;\">"
                    + "Mensaje autogenerado por el sistema institucional. Por favor no responda a este correo.<br>"
                    + "&copy; 2026 Universidad Popular del Cesar &bull; Valledupar, Cesar, Colombia."
                    + "</div>"
                    + "</div>";

            helper.setText(htmlTemplate, true);
            javaMailSender.send(mensaje);
            System.out.println("[RECUPERACIÓN OTP] Correo enviado exitosamente a " + correo);
        } catch (Exception e) {
            System.err.println("[RECUPERACIÓN OTP ERROR] Fallo envío SMTP a " + correo + ": " + e.getMessage());
        }
    }
}