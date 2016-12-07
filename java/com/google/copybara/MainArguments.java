/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara;

import static com.google.copybara.Subcommand.INFO;
import static com.google.copybara.Subcommand.VALIDATE;

import com.google.common.base.Preconditions;
import com.google.common.base.StandardSystemProperty;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Arguments which are unnamed (i.e. positional) or must be evaluated inside {@link Main}.
 */
@NotThreadSafe
@Parameters(separators = "=")
public final class MainArguments {
  static final String COPYBARA_SKYLARK_CONFIG_FILENAME = "copy.bara.sky";

  @Parameter(description =
      ""
          + "[subcommand] config_path [workflow_name [source_ref]]\n"
          + "\n"
          + (""
          + "subcommand: Optional, defaults to 'migrate'. The type of task to be performed by "
          + "Copybara. Available subcommands:\n"
          + "  - migrate: Executes the migration for the given config.\n"
          + "  - validate: Validates that the configuration is correct.\n"
          + "  - info: Reads the last migrated reference in the origin and destination.\n")
          + "\n"
          + "config_path: Required. Relative or absolute path to the main Copybara config file.\n"
          + "\n"
          + "workflow_name: Optional, defaults to 'default'. The name of the workflow in the "
          + "configuration to be used by Copybara.\n"
          + "\n"
          + "source_ref: Optional. The reference to be resolved in the origin. Most of the times "
          + "this argument is not needed, as Copybara keeps track of the last migrated reference "
          + "in the destination.\n"
  )
  List<String> unnamed = new ArrayList<>();

  @Parameter(names = "--help", help = true, description = "Shows this help text")
  boolean help;

  @Parameter(names = "--version", description = "Shows the version of the binary")
  boolean version;

  @Parameter(names = "--work-dir", description = "Directory where all the transformations"
      + " will be performed. By default a temporary directory.")
  String baseWorkdir;

  @Nullable
  private ArgumentHolder argumentHolder;

  public Subcommand getSubcommand() {
    return getArgs().subcommand;
  }

  public String getConfigPath() {
    return getArgs().configPath;
  }

  @Nullable
  public String getWorkflowName() {
    return getArgs().workflowName;
  }

  @Nullable
  public String getSourceRef() {
    return getArgs().sourceRef;
  }

  private ArgumentHolder getArgs() {
    Preconditions.checkNotNull(argumentHolder, "parseUnnamedArgs() should be invoked first. "
        + "This is probably a bug.");
    return argumentHolder;
  }

  /**
   * Returns the base working directory. This method should not be accessed directly by any other
   * class but Main.
   */
  public Path getBaseWorkdir(FileSystem fs) throws IOException {
    Path workdirPath;

    if (baseWorkdir == null) {
      // This is equivalent to Files.createTempDirectory(String.. but
      // works for any filesystem
      Path tmpDir = fs.getPath(StandardSystemProperty.JAVA_IO_TMPDIR.value());
      // This is only needed if using a fs for testing.
      Files.createDirectories(tmpDir);
      workdirPath = Files.createTempDirectory(tmpDir, "workdir");
    } else {
      workdirPath = fs.getPath(baseWorkdir).normalize();
    }
    if (Files.exists(workdirPath) && !Files.isDirectory(workdirPath)) {
      // Better being safe
      throw new IOException(
          "'" + workdirPath + "' exists and is not a directory");
    }
    if (!isDirEmpty(workdirPath)) {
      System.err.println("WARNING: " + workdirPath + " is not empty");
    }
    return workdirPath;
  }

  private static boolean isDirEmpty(final Path directory) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
      return !dirStream.iterator().hasNext();
    }
  }

  void parseUnnamedArgs() throws CommandLineException {
    if (unnamed.size() < 1) {
      throw new CommandLineException("Expected at least a configuration file.");
    } else if (unnamed.size() > 4) {
      throw new CommandLineException("Expected at most four arguments.");
    }

    Subcommand subcommand = Subcommand.MIGRATE;
    int argumentId = 0;
    String firstArg = unnamed.get(argumentId);
    // This should be enough for now
    if (!firstArg.endsWith(COPYBARA_SKYLARK_CONFIG_FILENAME)) {
      try {
        subcommand = Subcommand.valueOf(firstArg.toUpperCase());
        argumentId++;
      } catch (IllegalArgumentException e) {
        throw new CommandLineException(String.format("Invalid subcommand '%s'", firstArg));
      }
    }

    if (argumentId >= unnamed.size()) {
      throw new CommandLineException(
          String.format("Configuration file missing for '%s' subcommand.",
              subcommand.toString().toLowerCase()));
    }
    String configPath = unnamed.get(argumentId);
    argumentId++;

    String workflowName = "default";
    if (argumentId < unnamed.size()) {
      workflowName = unnamed.get(argumentId);
      argumentId++;
    }

    String sourceRef = null;
    if (argumentId < unnamed.size()) {
      if (subcommand == INFO || subcommand == VALIDATE) {
        throw new CommandLineException(
            String.format(
                "Too many arguments for subcommand '%s'", subcommand.toString().toLowerCase()));
      }
      sourceRef = unnamed.get(argumentId);
      argumentId++; // Just in case we add more arguments
    }
    argumentHolder = new ArgumentHolder(subcommand, configPath, workflowName, sourceRef);
  }

  private static class ArgumentHolder {

    private final Subcommand subcommand;
    private final String configPath;
    @Nullable private final String workflowName;
    @Nullable private final String sourceRef;

    private ArgumentHolder(Subcommand subcommand, String configPath,
        @Nullable  String workflowName, @Nullable String sourceRef) {
      this.subcommand = subcommand;
      this.configPath = configPath;
      this.workflowName = workflowName;
      this.sourceRef = sourceRef;
    }
  }
}
