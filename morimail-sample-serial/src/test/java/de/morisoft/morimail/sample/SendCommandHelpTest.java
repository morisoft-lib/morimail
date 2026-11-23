package de.morisoft.morimail.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** Checks that the send usage text describes the campaign directory and the inline rules. */
class SendCommandHelpTest {

    @Test
    void sendUsageNamesTheCampaignFilesAndTheInlineRules() {
        String usage = SerialMail.commandLine(SerialMail::realMailer, sleep -> { })
                .getSubcommands().get("send").getUsageMessage(CommandLine.Help.Ansi.OFF);
        // picocli wraps long text, so compare with the line breaks and indentation collapsed.
        String flat = usage.replaceAll("\\s+", " ");

        assertThat(flat).contains("recipients.csv", "attachments/", "cid:NAME");
        assertThat(flat).contains("copies each inline image once into inline/ under --out");
        assertThat(flat).contains("sent mails keep cid:");
        assertThat(usage).doesNotContain("%");
    }
}
