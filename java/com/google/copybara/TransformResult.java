// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.copybara.Origin.Reference;
import com.google.copybara.util.PathMatcherBuilder;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Represents the final result of a transformation, including metadata and actual code to be
 * migrated.
 */
public final class TransformResult {
  private final Path path;
  private final Reference originRef;
  private final Author author;
  private final long timestamp;
  private final String summary;
  private final PathMatcherBuilder destinationFiles;
  @Nullable
  private final String baseline;
  private final boolean askForConfirmation;

  private static long readTimestampOrCurrentTime(Reference originRef) throws RepoException {
    Long refTimestamp = originRef.readTimestamp();
    return (refTimestamp != null)
        ? refTimestamp : TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
  }

  public TransformResult(Path path, Reference originRef, Author author, String summary,
      PathMatcherBuilder destinationFiles) throws RepoException {
    this(path, originRef, author,
        readTimestampOrCurrentTime(originRef), summary, destinationFiles,
        /*baseline=*/ null, /*askForConfirmation=*/ false);
  }

  private TransformResult(Path path, Reference originRef, Author author, long timestamp,
      String summary, PathMatcherBuilder destinationFiles, @Nullable String baseline,
      boolean askForConfirmation) {
    this.path = Preconditions.checkNotNull(path);
    this.originRef = Preconditions.checkNotNull(originRef);
    this.author = Preconditions.checkNotNull(author);
    this.timestamp = timestamp;
    this.summary = Preconditions.checkNotNull(summary);
    this.destinationFiles = Preconditions.checkNotNull(destinationFiles);
    this.baseline = baseline;
    this.askForConfirmation = askForConfirmation;
  }

  public TransformResult withBaseline(String newBaseline) {
    Preconditions.checkNotNull(newBaseline);
    return new TransformResult(
        this.path, this.originRef, this.author, this.timestamp, this.summary,
        this.destinationFiles, newBaseline, this.askForConfirmation);
  }

  public TransformResult withAskForConfirmation(boolean askForConfirmation) {
    return new TransformResult(
        this.path, this.originRef, this.author, this.timestamp, this.summary,
        this.destinationFiles, this.baseline, askForConfirmation);
  }

  /**
   * Directory containing the tree of files to put in destination.
   */
  public Path getPath() {
    return path;
  }

  /**
   * Reference to the origin revision being moved.
   */
  public Origin.Reference getOriginRef() {
    return originRef;
  }

  /**
   * Destination author to be used.
   */
  public Author getAuthor() {
    return author;
  }

  /**
   * When the code was submitted to the origin repository, expressed as seconds since the UNIX
   * epoch.
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * A description of the migrated changes to include in the destination's change description. The
   * destination may add more boilerplate text or metadata.
   */
  public String getSummary() {
    return summary;
  }

  /**
   * A path matcher which matches files in the destination that should be kept even if they don't
   * exist in the source.
   */
  public PathMatcherBuilder getDestinationFiles() {
    return destinationFiles;
  }

  /**
   * Destination baseline to be used for updating the code in the destination. If null, the
   * destination can assume head baseline.
   *
   * <p>Destinations supporting non-null baselines are expected to do the equivalent of:
   * <ul>
   *    <li>Sync to that baseline</li>
   *    <li>Apply/patch the changes on that revision</li>
   *    <li>Sync to head and auto-merge conflicts if possible</li>
   * </ul>
   */
  @Nullable
  public String getBaseline() {
    return baseline;
  }

  /**
   * If the destination should ask for confirmation. Some destinations might chose to ignore this
   * flag either because it doesn't apply to them or because the always ask for confirmation in
   * certain circumstances.
   *
   * <p>But in general, any destination that could do accidental damage to a repository should
   * not ignore when the value is true.
   */
  public boolean isAskForConfirmation() {
    return askForConfirmation;
  }
}
