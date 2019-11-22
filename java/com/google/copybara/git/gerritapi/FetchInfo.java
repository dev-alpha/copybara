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

package com.google.copybara.git.gerritapi;

import com.google.api.client.util.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.devtools.build.lib.syntax.Printer;
import com.google.devtools.build.lib.syntax.StarlarkValue;

/** See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#fetch-info */
public class FetchInfo implements StarlarkValue {

  @Key private String url;
  @Key private String ref;

  // Required by GSON
  public FetchInfo() {}

  @VisibleForTesting
  public FetchInfo(String url, String ref) {
    this.url = url;
    this.ref = ref;
  }

  public String getUrl() {
    return url;
  }

  public String getRef() {
    return ref;
  }

  @Override
  public void repr(Printer printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("url", url)
        .add("ref", ref)
        .toString();
  }
}
