// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileSystem;

/**
 * General options available for all the program classes.
 */
public final class GeneralOptions implements Option {

  public static final String NOANSI = "--noansi";
  private final FileSystem fileSystem;
  private final boolean verbose;
  private final Console console;
  private final boolean skylark;

  @VisibleForTesting
  public GeneralOptions(FileSystem fileSystem, boolean verbose, Console console) {
    this(fileSystem, verbose, console, /*skylark=*/false);
  }

  @VisibleForTesting
  public GeneralOptions(FileSystem fileSystem, boolean verbose, Console console,
      boolean skylark) {
    this.console = Preconditions.checkNotNull(console);
    this.fileSystem = Preconditions.checkNotNull(fileSystem);
    this.verbose = verbose;
    this.skylark = skylark;
  }

  public boolean isVerbose() {
    return verbose;
  }

  public Console console() {
    return console;
  }

  public FileSystem getFileSystem() {
    return fileSystem;
  }

  public boolean isSkylark() {
    return skylark;
  }

  @Parameters(separators = "=")
  public static final class Args {
    @Parameter(names = "-v", description = "Verbose output.")
    boolean verbose;

    // We don't use JCommander for parsing this flag but we do it manually since
    // the parsing could fail and we need to report errors using one console
    @SuppressWarnings("unused")
    @Parameter(names = NOANSI, description = "Don't use ANSI output for messages")
    boolean noansi = false;

    @Parameter(names = "--skylark",
        description = "Use Skylark config format instead of Yaml. This is an experiment")
    boolean skylark = false;

    /**
     * This method should be called after the options have been set but before are used by any class.
     */
    public GeneralOptions init(FileSystem fileSystem, Console console) throws IOException {
      return new GeneralOptions(fileSystem, verbose, console, skylark);
    }
  }
}
