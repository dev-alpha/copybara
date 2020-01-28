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

package com.google.copybara.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.copybara.EndpointProvider;
import com.google.copybara.Option;
import com.google.copybara.Options;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.StarlarkValue;

/** A Skylark module used by tests */
@SkylarkModule(
    name = "testing",
    doc = "Module to use mock endpoints in tests.",
    category = SkylarkModuleCategory.BUILTIN)
public class TestingModule implements StarlarkValue {

  private final TestingOptions testingOptions;

  public TestingModule(Options options) {
    Options opts = checkNotNull(options, "Options cannot be null");
    this.testingOptions = checkNotNull(opts.get(TestingOptions.class), "TestOptions not set");
  }

  @SkylarkCallable(name = "origin", doc = "A dummy origin")
  public DummyOrigin origin() {
    return testingOptions.origin;
  }

  @SkylarkCallable(name = "destination", doc = "A dummy destination")
  public RecordsProcessCallDestination destination() {
    return testingOptions.destination;
  }

  @SkylarkCallable(name = "dummy_endpoint", doc = "A dummy feedback endpoint")
  public EndpointProvider<DummyEndpoint> dummyEndpoint() {
    return EndpointProvider.wrap(testingOptions.feedbackTrigger);
  }

  @SkylarkCallable(name = "dummy_trigger", doc = "A dummy feedback trigger")
  public DummyTrigger dummyTrigger() {
    return testingOptions.feedbackTrigger;
  }

  @SkylarkCallable(name = "dummy_checker", doc = "A dummy checker")
  public DummyChecker dummyChecker() {
    return testingOptions.checker;
  }

  /**
   * Holder for options to adjust this module's behavior to the needs of a test.
   */
  public final static class TestingOptions implements Option {

    public DummyOrigin origin;
    public RecordsProcessCallDestination destination;

    public DummyTrigger feedbackTrigger;
    public DummyChecker checker;
  }
}
