// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class GitOriginTest {

  private GitOrigin origin;
  private Path remote;
  private Path workdir;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() throws IOException, RepoException {
    Path reposDir = Files.createTempDirectory("repos_repo");
    remote = Files.createTempDirectory("remote");
    GitOrigin.Yaml yaml = new GitOrigin.Yaml();
    yaml.setUrl("file://" + remote.toFile().getAbsolutePath());
    yaml.setDefaultTrackingRef("other");

    GitOptions gitOptions = new GitOptions();
    gitOptions.gitRepoStorage = reposDir.toString();

    workdir = Files.createTempDirectory("workdir");
    origin = yaml.withOptions(
        new Options(ImmutableList.of(gitOptions, new GeneralOptions(workdir, /*verbose=*/true))));

    git("init");
    Files.write(remote.resolve("test.txt"), "some content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "first file");
  }

  private void git(String... params) throws RepoException {
    origin.getRepository().git(remote, params);
  }

  @Test
  public void testCheckout() throws IOException, RepoException {
    // Check that we get can checkout a branch
    origin.checkoutReference(origin.resolveReference("master"), workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    // Check that we track new commits that modify files
    Files.write(remote.resolve("test.txt"), "new content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "second commit");

    origin.checkoutReference(origin.resolveReference("master"), workdir);

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("new content");

    // Check that we track commits that delete files
    Files.delete(remote.resolve("test.txt"));
    git("rm", "test.txt");
    git("commit", "-m", "third commit");
    origin.checkoutReference(origin.resolveReference("master"), workdir);

    assertThat(Files.exists(testFile)).isFalse();
  }

  @Test
  public void testCheckoutWithLocalModifications() throws IOException, RepoException {
    String master = origin.resolveReference("master");
    origin.checkoutReference(master, workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    Files.delete(testFile);

    origin.checkoutReference(master, workdir);

    // The deletion in the workdir should not matter, since we should override in the next
    // checkout
    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testUnresolvedReference() throws IOException, RepoException {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("'master' should be already resolved");
    origin.checkoutReference("master", workdir);
  }

  @Test
  public void testUnresolvedShortSha1() throws IOException, RepoException {
    String ref = origin.resolveReference("master");
    String shortRef = ref.substring(0, 8);
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
        "'" + shortRef + "' should resolve to the same ref. But was resolved to '" + ref + "'");
    origin.checkoutReference(shortRef, workdir);
  }
}
