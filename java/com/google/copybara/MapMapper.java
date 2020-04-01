/*
 * Copyright (C) 2019 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.transform.ReversibleFunction;
import com.google.devtools.build.lib.syntax.Location;

public class MapMapper implements ReversibleFunction<String, String> {

  private final ImmutableMap<String, String> map;
  private final Location location;

  MapMapper(ImmutableMap<String, String> map, Location location) {
    this.map = Preconditions.checkNotNull(map);
    this.location = location;
  }

  @Override
  public ReversibleFunction<String, String> reverseMapping() throws NonReversibleValidationException {
    try {
      return new MapMapper(ImmutableBiMap.copyOf(map).inverse(), location);
    } catch (IllegalArgumentException e) {
      throw new NonReversibleValidationException(location,
          "Non-reversible map: " + map + ": " + e.getMessage());
    }
  }

  @Override
  public String apply(String s) {
    String v = map.get(s);
    return v == null ? s : v;
  }
}
