package iq.mizan.auth.service;

import java.util.Properties;

import iq.mizan.auth.MailProperties;
import iq.mizan.auth.entity.CodePurpose;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * ADR auth-email-1: sends codes over plain SMTP so any provider works. The sender is built here
 * rather than from {@code spring.mail.*} so that Boot's mail health check never marks the API down
 * when the provider is slow; a failed send is counted and alerted on instead.
 */
@Component
@ConditionalOnExpression("!'${mizan.mail.host:}'.isBlank()")
public class EmailCodeSender implements CodeSender {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeSender.class);

    private final JavaMailSenderImpl mailSender;
    private final String from;
    private final Counter sent;
    private final Counter failed;

    public EmailCodeSender(MailProperties properties, MeterRegistry meterRegistry) {
        if (properties.from() == null || properties.from().isBlank()) {
            throw new IllegalStateException("MIZAN_MAIL_FROM must be set when MIZAN_MAIL_HOST is");
        }
        this.mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.host());
        mailSender.setPort(properties.port());
        if (properties.username() != null && !properties.username().isBlank()) {
            mailSender.setUsername(properties.username());
            mailSender.setPassword(properties.password());
        }
        Properties javaMail = mailSender.getJavaMailProperties();
        javaMail.put("mail.smtp.auth", String.valueOf(mailSender.getUsername() != null));
        javaMail.put("mail.smtp.starttls.enable", String.valueOf(properties.starttls()));
        javaMail.put("mail.smtp.starttls.required", String.valueOf(properties.starttls()));
        javaMail.put("mail.smtp.connectiontimeout", "10000");
        javaMail.put("mail.smtp.timeout", "10000");
        javaMail.put("mail.smtp.writetimeout", "10000");
        this.from = properties.from();
        this.sent = Counter.builder("mizan.mail.sent").register(meterRegistry);
        this.failed = Counter.builder("mizan.mail.failed").register(meterRegistry);
    }

    @Override
    public void send(Identifier contact, CodePurpose purpose, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(contact.email());
        message.setSubject(subject(purpose));
        message.setText(body(purpose, code));
        try {
            mailSender.send(message);
            sent.increment();
        } catch (MailException e) {
            // The account change has already happened; the person can ask for the code again.
            failed.increment();
            log.error("Could not send the {} email", purpose, e);
        }
    }

    static String subject(CodePurpose purpose) {
        return switch (purpose) {
            case VERIFY_CONTACT -> "رمز تأكيد ميزان | Mizan confirmation code";
            case PASSWORD_RESET -> "رمز إعادة تعيين كلمة المرور | Mizan password reset code";
        };
    }

    static String body(CodePurpose purpose, String code) {
        String arabic = switch (purpose) {
            case VERIFY_CONTACT -> "رمز تأكيد بريدك الإلكتروني في ميزان هو";
            case PASSWORD_RESET -> "رمز إعادة تعيين كلمة المرور في ميزان هو";
        };
        String english = switch (purpose) {
            case VERIFY_CONTACT -> "Your Mizan email confirmation code is";
            case PASSWORD_RESET -> "Your Mizan password reset code is";
        };
        return """
                %s: %s
                إذا لم تطلب هذا الرمز فتجاهل هذه الرسالة.

                %s: %s
                If you did not ask for this code, ignore this email.
                """.formatted(arabic, code, english, code);
    }
}
