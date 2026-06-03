package com.socket.edge.tester;

import com.socket.edge.tester.cli.CollectCommand;
import com.socket.edge.tester.cli.RunCommand;
import com.socket.edge.tester.cli.SuiteCommand;
import com.socket.edge.tester.cli.ValidateCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name        = "se-tester",
    version     = "1.0.0",
    description = "ISO 8583 Regression Test Engine for Socket Edge",
    mixinStandardHelpOptions = true,
    subcommands = {
        RunCommand.class,
        SuiteCommand.class,
        CollectCommand.class,
        ValidateCommand.class
    }
)
public class SETesterCli implements Runnable {

    @Override
    public void run() {
        // no subcommand given — print usage
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SETesterCli())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }
}