// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.MockReference.MOCK_LABEL_REV_ID;

import com.google.copybara.EmptyChangeException;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.git.GitDestination.Yaml;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.MockReference;
import com.google.copybara.testing.OptionsBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class GitDestinationTest {

  private static final String CONFIG_NAME = "copybara_project";
  private static final String COMMIT_MSG = "A commit!\n";
  private Yaml yaml;
  private Path repoGitDir;
  private OptionsBuilder options;

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private Path workdir;

  @Before
  public void setup() throws Exception {
    yaml = new Yaml();
    repoGitDir = Files.createTempDirectory("GitDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");
    yaml.setUrl("file://" + repoGitDir);
    git("init", "--bare", repoGitDir.toString());
    options = new OptionsBuilder();
    options.git.gitCommitterEmail = "commiter@email";
    options.git.gitCommitterName = "Bara Kopi";
  }

  private GitRepository repo() {
    return new GitRepository(repoGitDir, /*workTree=*/null, /*verbose=*/true);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  @Test
  public void errorIfPushToRefMissing() throws ConfigValidationException {
    yaml.setFetch("master");
    yaml.setUrl("file:///foo");
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("push");
    destinationFirstCommit();
  }

  private GitDestination destinationFirstCommit() throws ConfigValidationException {
    options.git.gitFirstCommit = true;
    return yaml.withOptions(options.build(), CONFIG_NAME);
  }

  private GitDestination destination() throws ConfigValidationException {
    options.git.gitFirstCommit = false;
    return yaml.withOptions(options.build(), CONFIG_NAME);
  }

  private void assertFilesInDir(int expected, String ref, String path) throws Exception {
    String lsResult = git("--git-dir", repoGitDir.toString(), "ls-tree", ref, path);
    assertThat(lsResult.split("\n")).hasLength(expected);
  }

  private void assertCommitCount(int expected, String ref) throws Exception {
    String logResult = git("--git-dir", repoGitDir.toString(), "log", "--oneline", ref);
    assertThat(logResult.split("\n")).hasLength(expected);
  }

  private void assertCommitHasOrigin(String branch, String originRef) throws RepoException {
    assertThat(git("--git-dir", repoGitDir.toString(), "log", "-n1", branch))
        .contains("\n    " + MOCK_LABEL_REV_ID + ": " + originRef + "\n");
  }

  @Test
  public void processFirstCommit() throws Exception {
    yaml.setFetch("testPullFromRef");
    yaml.setPush("testPushToRef");
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    destinationFirstCommit().process(workdir, new MockReference("origin_ref"), /*timestamp=*/424242420, COMMIT_MSG);

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "testPushToRef");
    assertThat(showResult).contains("some content");

    assertFilesInDir(1, "testPushToRef", ".");
    assertCommitCount(1, "testPushToRef");

    assertCommitHasOrigin("testPushToRef", "origin_ref");
  }

  @Test
  public void processEmptyCommit() throws Exception {
    yaml.setFetch("master");
    yaml.setPush("master");
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    MockReference ref = new MockReference("origin_ref");
    destinationFirstCommit().process(workdir, ref, /*timestamp=*/424242420, COMMIT_MSG);
    thrown.expect(EmptyChangeException.class);
    thrown.expectMessage("empty change");
    destination().process(workdir, ref, /*timestamp=*/424242421, COMMIT_MSG);
  }

  @Test
  public void processFetchRefDoesntExist() throws Exception {
    yaml.setFetch("testPullFromRef");
    yaml.setPush("testPushToRef");
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    thrown.expect(RepoException.class);
    thrown.expectMessage("'testPullFromRef' doesn't exist");
    destination().process(workdir, new MockReference("origin_ref"), /*timestamp=*/424242420, COMMIT_MSG);
  }

  @Test
  public void processCommitDeletesAndAddsFiles() throws Exception {
    yaml.setFetch("pullFromBar");
    yaml.setPush("pushToFoo");

    Files.write(workdir.resolve("deleted_file"), "deleted content".getBytes());
    destinationFirstCommit().process(workdir, new MockReference("origin_ref"), /*timestamp=*/424242420, COMMIT_MSG);
    git("--git-dir", repoGitDir.toString(), "branch", "pullFromBar", "pushToFoo");

    workdir = Files.createTempDirectory("workdir2");
    Files.write(workdir.resolve("1.txt"), "content 1".getBytes());
    Files.createDirectories(workdir.resolve("subdir"));
    Files.write(workdir.resolve("subdir/2.txt"), "content 2".getBytes());
    destination().process(workdir, new MockReference("origin_ref"), /*timestamp=*/424242420, COMMIT_MSG);

    // Make sure original file was deleted.
    assertFilesInDir(2, "pushToFoo", ".");
    assertFilesInDir(1, "pushToFoo", "subdir");
    // Make sure both commits are present.
    assertCommitCount(2, "pushToFoo");

    assertCommitHasOrigin("pushToFoo", "origin_ref");
  }

  @Test
  public void previousImportReference() throws Exception {
    yaml.setFetch("master");
    yaml.setPush("master");

    Path file = workdir.resolve("test.txt");

    Files.write(file, "some content".getBytes());
    GitDestination destination1 = destinationFirstCommit();
    assertThat(destination1.getPreviousRef(MOCK_LABEL_REV_ID)).isNull();
    destination1.process(workdir, new MockReference("first_commit"), /*timestamp=*/424242420, COMMIT_MSG);
    assertCommitHasOrigin("master", "first_commit");

    Files.write(file, "some other content".getBytes());
    GitDestination destination2 = destination();
    assertThat(destination2.getPreviousRef(MOCK_LABEL_REV_ID)).isEqualTo("first_commit");
    destination2.process(workdir, new MockReference("second_commit"), /*timestamp=*/424242420, COMMIT_MSG);
    assertCommitHasOrigin("master", "second_commit");

    Files.write(file, "just more text".getBytes());
    GitDestination destination3 = destination();
    assertThat(destination3.getPreviousRef(MOCK_LABEL_REV_ID)).isEqualTo("second_commit");
    destination3.process(workdir, new MockReference("third_commit"), /*timestamp=*/424242420, COMMIT_MSG);
    assertCommitHasOrigin("master", "third_commit");
  }

  private void verifySpecifyAuthorField(String expected) throws Exception {
    yaml.setFetch("master");
    yaml.setPush("master");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());

    GitDestination destination = destinationFirstCommit();
    destination.process(workdir, new MockReference("first_commit"), /*timestamp=*/424242420, COMMIT_MSG);

    String[] commitLines = git("--git-dir", repoGitDir.toString(), "log", "-n1").split("\n");
    assertThat(commitLines[1]).isEqualTo("Author: " + expected);
  }

  @Test
  public void specifyAuthorField() throws Exception {
    String author = "Copybara Unit Tester <noreply@foo.bar>";
    yaml.setAuthor(author);
    verifySpecifyAuthorField(author);
  }

  @Test
  public void defaultAuthorFieldIsCopybara() throws Exception {
    verifySpecifyAuthorField("Copybara <noreply@google.com>");
  }

  private void checkAuthorFormatIsBad(String author) throws ConfigValidationException {
    thrown.expect(ConfigValidationException.class);
    thrown.expectMessage("author field must be in the form of 'Name <email@domain>'");
    yaml.setAuthor(author);
  }

  @Test
  public void validatesAuthorFieldFormat1() throws ConfigValidationException {
    checkAuthorFormatIsBad("foo");
  }

  @Test
  public void validatesAuthorFieldFormat2() throws ConfigValidationException {
    checkAuthorFormatIsBad("foo <a@>");
  }

  @Test
  public void validatesAuthorFieldFormat3() throws ConfigValidationException {
    checkAuthorFormatIsBad("foo <@b>");
  }

  @Test
  public void validatesAuthorFieldFormat4() throws ConfigValidationException {
    checkAuthorFormatIsBad("foo <a@b> foo");
  }

  @Test
  public void validatesAuthorFieldFormat5() throws ConfigValidationException {
    checkAuthorFormatIsBad(" <a@b>");
  }

  @Test
  public void writesOriginTimestampToAuthorField() throws Exception {
    yaml.setFetch("master");
    yaml.setPush("master");

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    destinationFirstCommit().process(workdir, new MockReference("first_commit"), /*timestamp=*/1414141414, COMMIT_MSG);
    GitTesting.assertAuthorTimestamp(repo(), "master", 1414141414);

    Files.write(workdir.resolve("test2.txt"), "some more content".getBytes());
    destination().process(workdir, new MockReference("second_commit"), /*timestamp=*/1515151515,
        COMMIT_MSG);
    GitTesting.assertAuthorTimestamp(repo(), "master", 1515151515);
  }

  @Test
  public void canOverrideCommitterName() throws Exception {
    yaml.setFetch("master");
    yaml.setPush("master");

    options.git.gitCommitterName = "Bara Kopi";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    destinationFirstCommit()
        .process(workdir, new MockReference("first_commit"), /*timestamp=*/1414141414, COMMIT_MSG);
    GitTesting.assertCommitterLineMatches(repo(), "master", "Bara Kopi <.*> [-+ 0-9]+");

    options.git.gitCommitterName = "Piko Raba";
    Files.write(workdir.resolve("test.txt"), "some more content".getBytes());
    destination()
        .process(workdir, new MockReference("second_commit"), /*timestamp=*/1414141490, COMMIT_MSG);
    GitTesting.assertCommitterLineMatches(repo(), "master", "Piko Raba <.*> [-+ 0-9+]+");
  }

  @Test
  public void canOverrideCommitterEmail() throws Exception {
    yaml.setFetch("master");
    yaml.setPush("master");

    options.git.gitCommitterEmail = "bara.bara@gocha.gocha";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    destinationFirstCommit()
        .process(workdir, new MockReference("first_commit"), /*timestamp=*/1414141414, COMMIT_MSG);
    GitTesting.assertCommitterLineMatches(
        repo(), "master", ".* <bara[.]bara@gocha[.]gocha> [-+ 0-9]+");

    options.git.gitCommitterEmail = "kupo.kupo@tan.kou";
    Files.write(workdir.resolve("test.txt"), "some more content".getBytes());
    destination()
        .process(workdir, new MockReference("second_commit"), /*timestamp=*/1414141490, COMMIT_MSG);
    GitTesting.assertCommitterLineMatches(
        repo(), "master", ".* <kupo[.]kupo@tan[.]kou> [-+ 0-9]+");
  }

  @Test
  public void gitUserNameMustBeConfigured() throws Exception {
    options.git.gitCommitterName = "";
    options.git.gitCommitterEmail = "foo@bara";
    yaml.setFetch("master");
    yaml.setPush("master");

    thrown.expect(RepoException.class);
    thrown.expectMessage("'user.name' and/or 'user.email' are not configured.");
    destinationFirstCommit()
        .process(workdir, new MockReference("first_commit"), /*timestamp=*/1414141414, COMMIT_MSG);
  }

  @Test
  public void gitUserEmailMustBeConfigured() throws Exception {
    options.git.gitCommitterName = "Foo Bara";
    options.git.gitCommitterEmail = "";
    yaml.setFetch("master");
    yaml.setPush("master");

    thrown.expect(RepoException.class);
    thrown.expectMessage("'user.name' and/or 'user.email' are not configured.");
    destinationFirstCommit()
        .process(workdir, new MockReference("first_commit"), /*timestamp=*/1414141414, COMMIT_MSG);
  }
}
