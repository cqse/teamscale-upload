package com.teamscale.upload;

import java.io.PrintWriter;
import java.util.Set;
import java.util.function.Function;

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
	 * The commands this tool offers.
	 */
	private static final Set<String> COMMAND_NAMES = Set.of(ReportCommandLineOptions.COMMAND_NAME,
			VulnerabilityReportCommandLineOptions.COMMAND_NAME);

	/**
	 * Parses the given command line arguments and returns the options of the
	 * command the user invoked.
	 * <p>
	 * Terminates the program if the arguments are invalid or if the user asked for
	 * the help screen or the version.
	 */
	public static CommonCommandLineOptions parse(String[] args) {
		ArgumentParser parser = ArgumentParsers.newFor("teamscale-upload").build()
				.description("Upload external analysis results to Teamscale.")
				.version("Teamscale Upload " + ToolVersion.VERSION);
		parser.addArgument("--version").action(Arguments.version())
				.help("Prints the version number of this teamscale-upload tool and exits.");
		parser.epilog("For general usage help and alternative upload methods, please check our online"
				+ " documentation at:" + "\nhttp://cqse.eu/tsu-docs"
				+ "\n\nRun 'teamscale-upload COMMAND --help' to see the options of a single command."
				+ "\n\nIf you do not name a command, teamscale-upload uploads external analysis reports.");

		Subparsers subparsers = parser.addSubparsers().dest(COMMAND).title("commands").metavar("COMMAND");
		Subparser reportParser = ReportCommandLineOptions.addCommand(subparsers);
		Subparser vulnerabilityReportParser = VulnerabilityReportCommandLineOptions.addCommand(subparsers);

		try {
			Namespace namespace = parser.parseArgs(withDefaultCommand(args));
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
	 * Inserts the {@link ReportCommandLineOptions#COMMAND_NAME} command if the user
	 * did not name one, which is how teamscale-upload was invoked before the
	 * commands existed. Every such command line keeps working.
	 * <p>
	 * The only ambiguous input is a first report file named like a command. Those
	 * users must name the command themselves, which the help screen says.
	 */
	private static String[] withDefaultCommand(String[] args) {
		if (args.length == 0 || COMMAND_NAMES.contains(args[0]) || isGlobalOption(args[0])) {
			return args;
		}
		String[] withCommand = new String[args.length + 1];
		withCommand[0] = ReportCommandLineOptions.COMMAND_NAME;
		System.arraycopy(args, 0, withCommand, 1, args.length);
		return withCommand;
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
