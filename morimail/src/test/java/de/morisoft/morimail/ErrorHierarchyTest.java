package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ErrorHierarchyTest {

    @Test
    void mailExceptionIsCheckedNotRuntime() {
        assertThat(MailException.class.getSuperclass()).isEqualTo(Exception.class);
        assertThat(RuntimeException.class.isAssignableFrom(MailException.class)).isFalse();
    }

    @Test
    void transientPermanentAndAuthRequiredExtendMailException() {
        assertThat(TransientMailException.class.getSuperclass()).isEqualTo(MailException.class);
        assertThat(PermanentMailException.class.getSuperclass()).isEqualTo(MailException.class);
        assertThat(AuthRequiredException.class.getSuperclass()).isEqualTo(MailException.class);
    }

    @Test
    void invalidAndSenderRejectedExtendPermanentMailException() {
        assertThat(InvalidMailException.class.getSuperclass()).isEqualTo(PermanentMailException.class);
        assertThat(SenderRejectedException.class.getSuperclass())
                .isEqualTo(PermanentMailException.class);
    }

    @Test
    void retryAfterIsEmptyUnlessSet() {
        Throwable cause = new RuntimeException("cause");

        assertThat(new TransientMailException("m").retryAfter()).isEmpty();
        assertThat(new TransientMailException("m", cause).retryAfter()).isEmpty();
        assertThat(new TransientMailException("m", Duration.ofSeconds(3)).retryAfter())
                .contains(Duration.ofSeconds(3));
        assertThat(new TransientMailException("m", (Duration) null).retryAfter()).isEmpty();
    }

    @Test
    void causeIsKeptForAllSixClasses() {
        Throwable cause = new RuntimeException("cause");

        assertThat(new MailException("m", cause).getCause()).isSameAs(cause);
        assertThat(new TransientMailException("m", cause).getCause()).isSameAs(cause);
        assertThat(new PermanentMailException("m", cause).getCause()).isSameAs(cause);
        assertThat(new InvalidMailException("m", cause).getCause()).isSameAs(cause);
        assertThat(new SenderRejectedException("m", cause).getCause()).isSameAs(cause);
        assertThat(new AuthRequiredException("m", cause).getCause()).isSameAs(cause);
    }

    @Test
    void negativeRetryAfterIsRejected() {
        assertThatThrownBy(() -> new TransientMailException("m", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TransientMailException("m", Duration.ZERO).retryAfter())
                .contains(Duration.ZERO);
    }
}
