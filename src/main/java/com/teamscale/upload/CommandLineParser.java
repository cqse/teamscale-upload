package com.teamscale.upload;

import java.io.PrintWriter;
import java.util.function.Function;

import com.teamscale.upload.utils.LogUtils;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.helper.HelpScreenException;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

/**
 * Parses the command line of teamscale-upload, which consists of the command to
 * perform and that command's options.
 */
public class CommandLineParser {

	/**
	 * The key under which the chosen command is stored in the {@link Namespace}.
	 */
	private static final String COMMAND = "command";

	/**
	 * Parses the given command line arguments and returns the options of the
	 * command the user invoked.
	 * <p>
	 * Terminates the program if the arguments are invalid or if the user asked for
	 * the help screen or the version.
	 */
	public static CommonCommandLineOptions parse(String[] args) {
		failOnInvocationWithoutCommand(args);

		ArgumentParser parser = ArgumentParsers.newFor("teamscale-upload").build()
				.description("Upload external analysis results to Teamscale.")
				.version("Teamscale Upload " + ToolVersion.VERSION);
		parser.addArgument("--version").action(Arguments.version())
				.help("Prints the version number of this teamscale-upload tool and exits.");
		parser.epilog("For general usage help and alternative upload methods, please check our online"
				+ " documentation at:" + "\nhttp://cqse.eu/tsu-docs"
				+ "\n\nRun 'teamscale-upload COMMAND --help' to see the options of a single command.");

		Subparsers subparsers = parser.addSubparsers().dest(COMMAND).title("commands").metavar("COMMAND");
		Subparser reportParser = ReportCommandLineOptions.addCommand(subparsers);
		Subparser vulnerabilityReportParser = VulnerabilityReportCommandLineOptions.addCommand(subparsers);

		try {
			Namespace namespace = parser.parseArgs(args);
			if (VulnerabilityReportCommandLineOptions.COMMAND_NAME.equals(namespace.getString(COMMAND))) {
				return validate(namespace, vulnerabilityReportParser, VulnerabilityReportCommandLineOptions::new);
			}
			return validate(namespace, reportParser, ReportCommandLineOptions::new);
		} catch (HelpScreenException e) {
			System.exit(0); // requesting the help screen should return exit code 0
			return null;
		} catch (ArgumentParserException e) {
			handleError(e);
			System.exit(1);
			return null;
		}
	}

	/**
	 * Builds the options object for the parsed arguments via the given factory and
	 * validates it against the parser of the command it belongs to.
	 */
	private static <T extends CommonCommandLineOptions> T validate(Namespace namespace, Subparser commandParser,
			Function<Namespace, T> factory) throws ArgumentParserException {
		T options = factory.apply(namespace);
		options.validate(commandParser);
		return options;
	}

	/**
	 * Terminates the program with a hint if the arguments look like an invocation
	 * of a teamscale-upload before 3.0.0, which uploaded external analysis reports
	 * without naming a command. Without this, argparse4j merely reports the first
	 * option as unrecognized, which does not tell the user what changed.
	 */
	private static void failOnInvocationWithoutCommand(String[] args) {
		if (args.length == 0 || !args[0].startsWith("-") || isGlobalOption(args[0])) {
			return;
		}
		LogUtils.fail("You did not specify a command, so teamscale-upload does not know what to upload."
				+ " Uploading external analysis reports is now the '" + ReportCommandLineOptions.COMMAND_NAME
				+ "' command, so instead of" + "\n\nteamscale-upload " + String.join(" ", args) + "\n\nplease run"
				+ "\n\nteamscale-upload " + ReportCommandLineOptions.COMMAND_NAME + " " + String.join(" ", args)
				+ "\n\nRun 'teamscale-upload --help' to see all available commands.");
	}

	/**
	 * Returns whether the given argument is an option of the tool itself rather
	 * than of one of its commands.
	 */
	private static boolean isGlobalOption(String argument) {
		return "-h".equals(argument) || "--help".equals(argument) || "--version".equals(argument);
	}

	/**
	 * Prints the given error, preceded by the usage of the command it belongs to.
	 * <p>
	 * We cannot leave this to {@link ArgumentParser#handleError}: in argparse4j
	 * 0.9.0, that method and {@link Subparser#handleError} delegate to each other
	 * without end, so an error carrying a {@link Subparser} - as every error from
	 * {@link CommonCommandLineOptions#validate} does - would end the program with a
	 * {@link StackOverflowError}. Printing it ourselves produces the same output
	 * that argparse4j produces for a parse error of the same command.
	 */
	private static void handleError(ArgumentParserException e) {
		if (!(e.getParser() instanceof Subparser)) {
			e.getParser().handleError(e);
			return;
		}
		PrintWriter writer = new PrintWriter(System.err, true);
		e.getParser().printUsage(writer);
		writer.println("teamscale-upload: error: " + e.getMessage());
	}
}
