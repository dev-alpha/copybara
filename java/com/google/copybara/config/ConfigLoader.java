/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.config;

import com.google.common.base.Preconditions;
import com.google.copybara.Config;
import com.google.copybara.ModuleSupplier;
import com.google.copybara.Options;
import com.google.copybara.ValidationException;
import java.io.IOException;

/**
 * Loads the configuration from a given config file.
 */
public class ConfigLoader<T> {

  private final SkylarkParser skylarkParser;
  private final ConfigFile<T> configFile;

  public ConfigLoader(ModuleSupplier moduleSupplier, ConfigFile<T> configFile) {
    this.skylarkParser = new SkylarkParser(moduleSupplier.getModules());
    this.configFile = Preconditions.checkNotNull(configFile);
  }

  /**
   * Returns a string representation of the location of this configuration.
   */
  public final String location() {
    return configFile.path();
  }

  /**
   * Loads the configuration using this loader.
   * @param options Parsed command line options
   */
  public final Config loadConfig(Options options) throws ValidationException, IOException {
    return skylarkParser.loadConfig(configFile, options);
  }
}
