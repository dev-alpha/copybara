// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.copybara.Change;
import com.google.copybara.Origin.Reference;
import com.google.copybara.Origin.ReferenceFiles;
import com.google.copybara.RepoException;
import com.google.copybara.testing.DummyReference;
import com.google.copybara.testing.OptionsBuilder;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RunWith(JUnit4.class)
public class GitOriginTest {

  private GitOrigin origin;
  private Path remote;
  private Path workdir;
  private String firstCommitRef;

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private Path reposDir;

  @Before
  public void setup() throws Exception {
    remote = Files.createTempDirectory("remote");
    workdir = Files.createTempDirectory("workdir");
    GitOrigin.Yaml yaml = new GitOrigin.Yaml();
    yaml.setUrl("file://" + remote.toFile().getAbsolutePath());
    yaml.setRef("other");

    OptionsBuilder options = new OptionsBuilder();
    reposDir = Files.createTempDirectory("repos_repo");
    options.git.gitRepoStorage = reposDir.toString();

    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    Path userHomeForTest = Files.createTempDirectory("home");
    Map<String, String> env = Maps.newHashMap(System.getenv());
    env.put("HOME", userHomeForTest.toString());
    origin = yaml.withOptions(options.build(), env);

    git("init");
    Files.write(remote.resolve("test.txt"), "some content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "first file");
    String head = git("rev-parse", "HEAD");
    // Remove new line
    firstCommitRef = head.substring(0, head.length() -1);
  }

  private String git(String... params) throws RepoException {
    return origin.getRepository().git(remote, params).getStdout();
  }

  @Test
  public void testCheckout() throws IOException, RepoException {
    // Check that we get can checkout a branch
    origin.resolve("master").checkout(workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    // Check that we track new commits that modify files
    Files.write(remote.resolve("test.txt"), "new content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "second commit");

    origin.resolve("master").checkout(workdir);

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("new content");

    // Check that we track commits that delete files
    Files.delete(remote.resolve("test.txt"));
    git("rm", "test.txt");
    git("commit", "-m", "third commit");
    origin.resolve("master").checkout(workdir);

    assertThat(Files.exists(testFile)).isFalse();
  }

  @Test
  public void testCheckoutWithLocalModifications() throws IOException, RepoException {
    ReferenceFiles<GitOrigin> master = origin.resolve("master");
    master.checkout(workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");

    Files.delete(testFile);

    master.checkout(workdir);

    // The deletion in the workdir should not matter, since we should override in the next
    // checkout
    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testCheckoutOfARef() throws IOException, RepoException {
    ReferenceFiles<GitOrigin> reference = origin.resolve(firstCommitRef);
    reference.checkout(workdir);
    Path testFile = workdir.resolve("test.txt");

    assertThat(new String(Files.readAllBytes(testFile))).isEqualTo("some content");
  }

  @Test
  public void testChanges() throws IOException, RepoException {
    // Need to "round" it since git doesn't store the milliseconds
    DateTime beforeTime = DateTime.now().minusSeconds(1);
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "change2", "test.txt", "some content2");
    singleFileCommit(author, "change3", "test.txt", "some content3");
    singleFileCommit(author, "change4", "test.txt", "some content4");

    ImmutableList<Change<GitOrigin>> changes = origin
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes).hasSize(3);
    assertThat(changes.get(0).getMessage()).isEqualTo("change2\n");
    assertThat(changes.get(1).getMessage()).isEqualTo("change3\n");
    assertThat(changes.get(2).getMessage()).isEqualTo("change4\n");
    for (Change<GitOrigin> change : changes) {
      assertThat(change.getAuthor()).isEqualTo(author);
      assertThat(change.getDate()).isAtLeast(beforeTime);
      assertThat(change.getDate()).isAtMost(DateTime.now().plusSeconds(1));
    }
  }

  @Test
  public void testNoChanges() throws IOException, RepoException {
    ImmutableList<Change<GitOrigin>> changes = origin
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes).isEmpty();
  }

  @Test
  public void testChange() throws IOException, RepoException {
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "change2", "test.txt", "some content2");

    String head = git("rev-parse", "HEAD");
    String lastCommit = head.substring(0, head.length() -1);
    ReferenceFiles<GitOrigin> lastCommitRef = origin.resolve(lastCommit);
    Change<GitOrigin> change = origin.change(lastCommitRef);

    assertThat(change.getAuthor()).isEqualTo(author);
    assertThat(change.firstLineMessage()).isEqualTo("change2");
    assertThat(change.getReference().asString()).isEqualTo(lastCommitRef.asString());
  }

  @Test
  public void testNoChange() throws IOException, RepoException {
    // This is needed to initialize the local repo
    origin.resolve(firstCommitRef);

    thrown.expect(RepoException.class);
    thrown.expectMessage("Cannot find reference 'foo'");

    origin.resolve("foo");
  }

  private void singleFileCommit(String author, String commitMessage, String fileName,
      String fileContent) throws IOException, RepoException {
    Files.write(remote.resolve(fileName), fileContent.getBytes());
    git("add", fileName);
    git("commit", "-m", commitMessage, "--author=" + author);
  }

  @Test
  public void testChangesMerge() throws IOException, RepoException {
    // Need to "round" it since git doesn't store the milliseconds
    DateTime beforeTime = DateTime.now().minusSeconds(1);
    git("branch", "feature");
    git("checkout", "feature");
    String author = "John Name <john@name.com>";
    singleFileCommit(author, "change2", "test2.txt", "some content2");
    singleFileCommit(author, "change3", "test2.txt", "some content3");
    git("checkout", "master");
    singleFileCommit(author, "master1", "test.txt", "some content2");
    singleFileCommit(author, "master2", "test.txt", "some content3");
    git("merge", "master", "feature");
    // Change merge author
    git("commit", "--amend", "--author=" + author, "--no-edit");

    ImmutableList<Change<GitOrigin>> changes = origin
        .changes(origin.resolve(firstCommitRef), origin.resolve("HEAD"));

    assertThat(changes).hasSize(3);
    assertThat(changes.get(0).getMessage()).isEqualTo("master1\n");
    assertThat(changes.get(1).getMessage()).isEqualTo("master2\n");
    assertThat(changes.get(2).getMessage()).isEqualTo("Merge branch 'feature'\n");
    for (Change<GitOrigin> change : changes) {
      assertThat(change.getAuthor()).isEqualTo(author);
      assertThat(change.getDate()).isAtLeast(beforeTime);
      assertThat(change.getDate()).isAtMost(DateTime.now().plusSeconds(1));
    }
  }

  @Test
  public void canReadTimestamp() throws IOException, RepoException {
    Files.write(remote.resolve("test2.txt"), "some more content".getBytes());
    git("add", "test2.txt");
    git("commit", "-m", "second file", "--date=1400110011");
    ReferenceFiles<GitOrigin> master = origin.resolve("master");
    assertThat(master.readTimestamp()).isEqualTo(1400110011L);
  }

  @Test
  public void testColor() throws RepoException, IOException {
    git("config", "--global", "color.ui", "always");

    ReferenceFiles<GitOrigin> firstRef = origin.resolve(firstCommitRef);

    Files.write(remote.resolve("test.txt"), "new content".getBytes());
    git("add", "test.txt");
    git("commit", "-m", "second commit");
    ReferenceFiles<GitOrigin> secondRef = origin.resolve("HEAD");

    assertThat(origin.change(firstRef).getMessage()).contains("first file");
    assertThat(origin.changes(null, secondRef)).hasSize(2);
    assertThat(origin.changes(firstRef, secondRef)).hasSize(1);
  }
}
