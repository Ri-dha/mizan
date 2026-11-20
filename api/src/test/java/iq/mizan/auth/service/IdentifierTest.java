package iq.mizan.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import iq.mizan.auth.entity.CodePurpose;
import iq.mizan.common.exception.ApiException;

import org.junit.jupiter.api.Test;

class IdentifierTest {

    @Test
    void emailsAreTrimmedAndLowerCased() {
        assertThat(Identifier.parse("  Ridha@Example.COM ").email()).isEqualTo("ridha@example.com");
    }

    @Test
    void phoneNumbersAndJunkAreRefused() {
        for (String raw : new String[] {"07700000001", "+9647700000001", "", null, "no-at-sign", "a@b"}) {
            assertThatThrownBy(() -> Identifier.parse(raw)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void emailsNameThePurposeInBothLanguages() {
        String body = EmailCodeSender.body(CodePurpose.PASSWORD_RESET, "123456");
        assertThat(body).contains("123456").contains("password reset").contains("كلمة المرور");
    }
}
