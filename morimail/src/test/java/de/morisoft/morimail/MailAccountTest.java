package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.morisoft.morimail.test.RecordingTransport;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MailAccountTest {

    @Test
    void unknownTransportListsAvailableIds() {
        MailAccount.Builder builder = MailAccount.builder("main").transport("nope");

        assertThatThrownBy(builder::build)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope")
                .hasMessageContaining("recording");
    }

    @Test
    void missingIdOrTransportIsRejected() {
        assertThatThrownBy(() -> MailAccount.builder(null).transport("recording").build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
        assertThatThrownBy(() -> MailAccount.builder("  ").transport("recording").build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
        assertThatThrownBy(() -> MailAccount.builder("main").build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transport");
        assertThatThrownBy(() -> MailAccount.builder("main").transport(" ").build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transport");
    }

    @Test
    void optionsReachTheTransportUnchangedAndUnmodifiable() {
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .option("a", 1)
                .option("b", "x")
                .build();
        Map<String, Object> options = ((RecordingTransport) account.transport()).options();

        assertThat(options).isEqualTo(Map.of("a", 1, "b", "x"));
        assertThatThrownBy(() -> options.put("c", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingAuthArrivesAsNull() {
        MailAccount account = MailAccount.builder("main").transport("recording").build();

        assertThat(((RecordingTransport) account.transport()).auth()).isNull();
    }

    @Test
    void authArrivesAsTheSameInstance() {
        TokenProvider auth = scope -> "tok";
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .auth(auth)
                .build();

        assertThat(((RecordingTransport) account.transport()).auth()).isSameAs(auth);
    }

    @Test
    void invalidDefaultFromIsRejectedWithoutEchoingTheValue() {
        String[][] cases = {
            {"no-at-sign", "no-at-sign"},
            {"a@b@example.org", "a@b@example.org"},
            {"sentinel-crlf\r\n@example.org", "sentinel-crlf"},
        };
        for (String[] c : cases) {
            MailAccount.Builder builder = MailAccount.builder("main")
                    .transport("recording")
                    .defaultFrom(c[0]);

            assertThatThrownBy(builder::build)
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("defaultFrom")
                    .hasMessageContaining("main")
                    .hasMessageNotContaining(c[1]);
        }
    }

    @Test
    void eachBuildCreatesADistinctTransport() {
        MailAccount.Builder builder = MailAccount.builder("main").transport("recording");

        MailAccount first = builder.build();
        MailAccount second = builder.build();

        assertThat(first.transport()).isNotSameAs(second.transport());
    }
}
