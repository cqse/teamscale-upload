package com.teamscale.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Subparser;

class CommandLineParserTest {

	private Locale originalLocale;

	@BeforeEach
	void setGermanLocale() {
		originalLocale = Locale.getDefault();
		Locale.setDefault(Locale.GERMAN);
	}

	@AfterEach
	void restoreLocale() {
		Locale.setDefault(originalLocale);
	}

	@Test
	void helpIsEnglishRegardlessOfDefaultLocale() {
		ArgumentParser parser = CommandLineParser.createParser();
		Subparser reportParser = ReportCommandLineOptions.addCommand(parser.addSubparsers());

		assertThat(parser.formatHelp()).contains("usage:", "named arguments:").doesNotContain("Benutzung:",
				"Benannte Argumente:");
		assertThat(reportParser.formatHelp()).contains("usage:", "positional arguments:", "named arguments:")
				.doesNotContain("Benutzung:", "Positions-Argumente:", "Benannte Argumente:");
	}
}
