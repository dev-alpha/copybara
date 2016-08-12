// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.TransformWork;
import com.google.copybara.config.NonReversibleValidationException;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.ProgressPrefixConsole;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A transformation that runs a sequence of delegate transformations
 */
public class Sequence implements Transformation {

  private final ImmutableList<Transformation> sequence;

  protected final Logger logger = Logger.getLogger(Sequence.class.getName());

  private Sequence(ImmutableList<Transformation> sequence) {
    this.sequence = Preconditions.checkNotNull(sequence);
  }

  @Override
  public void transform(TransformWork work, Console console)
      throws IOException, ValidationException {
    for (int i = 0; i < sequence.size(); i++) {
      Transformation transformation = sequence.get(i);
      String transformMsg = String.format(
          "[%2d/%d] Transform %s", i + 1, sequence.size(),
          transformation.describe());
      logger.log(Level.INFO, transformMsg);

      console.progress(transformMsg);
      transformation.transform(work, new ProgressPrefixConsole(transformMsg + ": ", console));
    }
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    ImmutableList.Builder<Transformation> list = ImmutableList.builder();
    for (Transformation element : sequence) {
      list.add(element.reverse());
    }
    return new Sequence(list.build().reverse());
  }

  @VisibleForTesting
  public ImmutableList<Transformation> getSequence() {
    return sequence;
  }

  /**
   * returns a string like "Sequence[a, b, c]"
   */
  @Override
  public String toString() {
    return "Sequence" + sequence;
  }

  @Override
  public String describe() {
    return "sequence";
  }

  /**
   * Create a sequence avoiding nesting a single sequence twice.
   */
  public static Sequence createSequence(ImmutableList<Transformation> elements) {
    //Avoid nesting one sequence inside another sequence
    if (elements.size() == 1 && elements.get(0) instanceof Sequence) {
      return (Sequence) elements.get(0);
    }
    return new Sequence(elements);
  }
}
