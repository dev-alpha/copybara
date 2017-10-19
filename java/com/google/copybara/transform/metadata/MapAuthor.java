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

package com.google.copybara.transform.metadata;

import static com.google.copybara.ValidationException.checkCondition;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.NonReversibleValidationException;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.AuthorParser;
import com.google.copybara.authoring.InvalidAuthorException;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Map authors between revision systems.
 */
public class MapAuthor implements Transformation {

  // Author only uses mail for comparation
  private final ImmutableMap<String, String> authorToAuthor;
  private final ImmutableMap<String, Author> mailToAuthor;
  private final ImmutableMap<String, Author> nameToAuthor;
  private final boolean reversible;
  private final boolean failIfNotFound;
  private final boolean failIfNotFoundInReverse;
  private final Location location;

  private MapAuthor(Location location, ImmutableMap<String, String> authorToAuthor,
      ImmutableMap<String, Author> mailToAuthor, ImmutableMap<String, Author> nameToAuthor,
      boolean reversible, boolean failIfNotFound, boolean failIfNotFoundInReverse) {
    this.location = Preconditions.checkNotNull(location);
    this.authorToAuthor = Preconditions.checkNotNull(authorToAuthor);
    this.mailToAuthor = Preconditions.checkNotNull(mailToAuthor);
    this.nameToAuthor = Preconditions.checkNotNull(nameToAuthor);
    this.reversible = reversible;
    this.failIfNotFound = failIfNotFound;
    this.failIfNotFoundInReverse = failIfNotFoundInReverse;
  }

  public static MapAuthor create(Location location, Map<String, String> authorMap,
      boolean reversible, boolean failIfNotFound, boolean failIfNotFoundInReverse)
      throws EvalException {
    ImmutableMap.Builder<String, String> authorToAuthor = ImmutableMap.builder();
    ImmutableMap.Builder<String, Author> mailToAuthor = ImmutableMap.builder();
    ImmutableMap.Builder<String, Author> nameToAuthor = ImmutableMap.builder();

    for (Entry<String, String> e : authorMap.entrySet()) {
      Author to = Author.parse(location, e.getValue());
      try {
        authorToAuthor.put(AuthorParser.parse(e.getKey()).toString(),
            to.toString());
      } catch (InvalidAuthorException ex) {
        if (e.getKey().contains("@")) {
          mailToAuthor.put(e.getKey(), to);
        } else {
          nameToAuthor.put(e.getKey(), to);
        }
      }
    }
    return new MapAuthor(location, authorToAuthor.build(), mailToAuthor.build(),
        nameToAuthor.build(), reversible, failIfNotFound, failIfNotFoundInReverse);
  }

  @Override
  public void transform(TransformWork work) throws IOException, ValidationException {
    String newAuthor = authorToAuthor.get(work.getAuthor().toString());
    if (newAuthor != null) {
      try {
        work.setAuthor(AuthorParser.parse(newAuthor));
      } catch (InvalidAuthorException e) {
        throw new IllegalStateException("Shouldn't happen. We validate before", e);
      }
      return;
    }
    Author byMail = mailToAuthor.get(work.getAuthor().getEmail());
    if (byMail != null) {
      work.setAuthor(byMail);
      return;
    }
    Author byName = nameToAuthor.get(work.getAuthor().getName());
    if (byName != null) {
      work.setAuthor(byName);
      return;
    }
    checkCondition(!failIfNotFound, "Cannot find a mapping for author '%s'", work.getAuthor());
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    if (!reversible) {
      throw new NonReversibleValidationException(location,
          "Author mapping doesn't have reversible enabled");
    } else if (!mailToAuthor.isEmpty()) {
      throw new NonReversibleValidationException(location, String.format(
          "author mapping is not reversible because it contains mail -> author mappings."
              + " Only author -> author is reversible: %s", nameToAuthor));
    } else if (!nameToAuthor.isEmpty()) {
      throw new NonReversibleValidationException(location, String.format(
          "author mapping is not reversible because it contains name -> author mappings."
              + " Only author -> author is reversible: %s", nameToAuthor));
    }

    try {
      ImmutableMap<String, String> reverse = ImmutableBiMap.<String, String>builder()
          .putAll(authorToAuthor).build().inverse();
      return new MapAuthor(location, reverse, ImmutableMap.of(),
          ImmutableMap.of(), reversible, failIfNotFoundInReverse, failIfNotFound);
    } catch (IllegalArgumentException e) {
      throw new NonReversibleValidationException(location, "non-reversible author map:"
          + e.getMessage());
    }
  }

  @Override
  public String describe() {
    return "Mapping authors";
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("authorToAuthor", authorToAuthor)
        .add("mailToAuthor", mailToAuthor)
        .add("nameToAuthor", nameToAuthor)
        .add("reversible", reversible)
        .add("failIfNotFound", failIfNotFound)
        .add("failIfNotFoundInReverse", failIfNotFoundInReverse)
        .add("location", location)
        .toString();
  }
}
