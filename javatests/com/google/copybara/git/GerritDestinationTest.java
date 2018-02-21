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

package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Changes;
import com.google.copybara.DestinationEffect;
import com.google.copybara.DestinationEffect.Type;
import com.google.copybara.LabelFinder;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.git.GerritDestination.GerritProcessPushOutput;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.gerritapi.GerritApiTransportImpl;
import com.google.copybara.git.testing.GitTesting;
import com.google.copybara.testing.DummyOrigin;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.OptionsBuilder.GitApiMockHttpTransport;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.testing.git.GitTestUtil.TestGitOptions;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.LogConsole;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritDestinationTest {

  private static final String GERRIT_RESPONSE = "Counting objects: 9, done.\n"
      + "Delta compression using up to 4 threads.\n"
      + "Compressing objects: 100% (6/6), done.\n"
      + "Writing objects: 100% (9/9), 3.20 KiB | 0 bytes/s, done.\n"
      + "Total 9 (delta 4), reused 0 (delta 0)\n"
      + "remote: Resolving deltas: 100% (4/4)\n"
      + "remote: Processing changes: updated: 1, done\n"
      + "remote:\n"
      + "remote: Updated Changes:\n"
      + "remote:   https://some.url.google.com/1234 This is a message\n"
      + "remote:\n"
      + "To sso://team/copybara-team/copybara\n"
      + " * [new branch]      HEAD -> refs/for/master%notify=NONE\n"
      + "<o> [master] ~/dev/copybara$\n";
  private String url;
  private String fetch;
  private String pushToRefsFor;
  private Path repoGitDir;
  private Path workdir;
  private OptionsBuilder options;
  private TestingConsole console;
  private ImmutableList<String> excludedDestinationPaths;
  private SkylarkTestExecutor skylark;

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private Path urlMapper;
  private GitApiMockHttpTransport gitApiMockHttpTransport;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");

    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();
    urlMapper = Files.createTempDirectory("url_mapper");
    options.git = new TestGitOptions(urlMapper, () -> options.general);
    options.gerrit = new GerritOptions(() -> options.general, options.git) {
      @Override
      protected GerritApiTransportImpl getGerritApiTransport(URI uri) throws RepoException {
        return new GerritApiTransportImpl(repo(), uri, gitApiMockHttpTransport);
      }
    };
    options.gitDestination = new GitDestinationOptions(() -> options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    excludedDestinationPaths = ImmutableList.of();

    repoGitDir = localGerritRepo("localhost:33333/foo/bar").getGitDir();
    url = "https://localhost:33333/foo/bar";
    repo().init();

    skylark = new SkylarkTestExecutor(options, GitModule.class);

    gitApiMockHttpTransport =
        new GitApiMockHttpTransport() {
          @Override
          protected byte[] getContent(String method, String url, MockLowLevelHttpRequest request)
              throws IOException {
            if (method.equals("GET") && url.startsWith("https://localhost:33333/changes/")) {
              String result =
                  "["
                      + "{"
                      + "  change_id : \""
                      + changeIdFromRequest(url)
                      + "\","
                      + "  status : \"NEW\""
                      + "}]";
              return result.getBytes(UTF_8);
            }
            throw new IllegalArgumentException(method + " " + url);
          }
        };
  }

  private String changeIdFromRequest(String url) {
    return url.replaceAll(".*(I[a-z0-9]{40}).*", "$1");
  }

  private GitRepository repo() {
    return GitRepository.newBareRepo(repoGitDir, getGitEnv(),  /*verbose=*/true);
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  private GerritDestination destination() throws ValidationException {
    return skylark.eval("result", "result = "
        + "git.gerrit_destination(\n"
        + "    url = '" + url + "',\n"
        + "    fetch = '" + fetch + "',\n"
        + "    " + (pushToRefsFor == null ? "" : "push_to_refs_for = '" + pushToRefsFor + "',")
        + ")");
  }

  private static String lastCommitChangeIdLine(String ref, GitRepository repo) throws Exception {
    GitLogEntry log = Iterables.getOnlyElement(repo.log("refs/for/master").withLimit(1).run());
    assertThat(log.getBody()).contains("\n" + DummyOrigin.LABEL_NAME + ": " + ref + "\n");
    for (LabelFinder label : ChangeMessage.parseMessage(log.getBody()).getLabels()) {
      if (label.isLabel(GerritDestination.CHANGE_ID_LABEL)) {
        assertThat(label.getValue()).matches("I[0-9a-f]{40}$");
        return label.getLine();
      }
    }
    fail("Cannot find " + GerritDestination.CHANGE_ID_LABEL + " in:\n" + log.getBody());
    throw new IllegalStateException();
  }

  private void process(DummyRevision originRef)
      throws ValidationException, RepoException, IOException {
    ImmutableList<DestinationEffect> result = destination()
        .newWriter(Glob.createGlob(ImmutableList.of("**"), excludedDestinationPaths),
            /*dryRun=*/false, /*groupId=*/null, /*oldWriter=*/null)
        .write(
            TransformResults.of(workdir, originRef).withIdentity(originRef.asString()),
            console);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertThat(result.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(result.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(result.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");
  }

  @Test
  public void gerritChangeIdChangesBetweenCommits() throws Exception {
    fetch = "master";

    Files.write(workdir.resolve("file"), "some content".getBytes());

    options.setForce(true);
    process(new DummyRevision("origin_ref"));

    String firstChangeIdLine = lastCommitChangeIdLine("origin_ref", repo());

    Files.write(workdir.resolve("file2"), "some more content".getBytes());
    git("branch", "master", "refs/for/master");
    options.setForce(false);
    process(new DummyRevision("origin_ref2"));

    assertThat(firstChangeIdLine)
        .isNotEqualTo(lastCommitChangeIdLine("origin_ref2", repo()));
  }

  @Test
  public void specifyChangeId() throws Exception {
    fetch = "master";

    Files.write(workdir.resolve("file"), "some content".getBytes());

    String changeId = "Iaaaaaaaaaabbbbbbbbbbccccccccccdddddddddd";
    options.setForce(true);
    options.gerrit.gerritChangeId = changeId;
    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo()))
        .isEqualTo(GerritDestination.CHANGE_ID_LABEL + ": " + changeId);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("file"), "some different content".getBytes());

    changeId = "Ibbbbbbbbbbccccccccccddddddddddeeeeeeeeee";
    options.setForce(false);
    options.gerrit.gerritChangeId = changeId;
    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo()))
        .isEqualTo(GerritDestination.CHANGE_ID_LABEL + ": " + changeId);
  }

  @Test
  public void reuseChangeId() throws Exception {
    fetch = "master";

    Files.write(workdir.resolve("file"), "some content".getBytes());

    options.setForce(true);
    options.gerrit.gerritChangeId = null;

    url = "https://localhost:33333/foo/bar";
    GitRepository repo = localGerritRepo("localhost:33333/foo/bar");
    gitApiMockHttpTransport = new GitApiMockHttpTransport() {
      @Override
      protected byte[] getContent(String method, String url, MockLowLevelHttpRequest request)
          throws IOException {
        // No changes found
        return "[]".getBytes();
      }
    };

    process(new DummyRevision("origin_ref"));
    String changeId = lastCommitChangeIdLine("origin_ref", repo);
    assertThat(changeId).matches(GerritDestination.CHANGE_ID_LABEL + ": I[a-z0-9]+");
    LabelFinder labelFinder = new LabelFinder(changeId);

    Files.write(workdir.resolve("file"), "some different content".getBytes());

    gitApiMockHttpTransport = new GitApiMockHttpTransport() {
      @Override
      protected byte[] getContent(String method, String url, MockLowLevelHttpRequest request)
          throws IOException {
        String expected = "https://localhost:33333/changes/?q=change:%20" + labelFinder.getValue()
            + "%20AND%20project:foo/bar";
        if (method.equals("GET") && url.equals(expected)) {
          String result = "["
              + "{"
              + "  change_id : \"" + labelFinder.getValue() + "\","
              + "  status : \"NEW\""
              + "}]";
          return result.getBytes(UTF_8);
        }
        throw new IllegalArgumentException(method + " " + url);
      }
    };
    // Allow to push again in a non-fastforward way.
    repo.simpleCommand("update-ref", "-d", "refs/for/master");
    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo)).isEqualTo(changeId);
    GitTesting.assertThatCheckout(repo, "refs/for/master")
        .containsFile("file", "some different content")
        .containsNoMoreFiles();
  }

  public GitRepository localGerritRepo(String url) throws RepoException {
    return GitRepository
        .newBareRepo(urlMapper.resolve(url), getGitEnv(), options.general.isVerbose())
        .init();
  }

  @Test
  public void changeExists() throws Exception {
    fetch = "master";

    Files.write(workdir.resolve("file"), "some content".getBytes());

    options.setForce(true);
    String expectedChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", StandardCharsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(0)
        .hash();
    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo()))
        .isEqualTo(GerritDestination.CHANGE_ID_LABEL + ": " + expectedChangeId);
  }

  @Test
  public void changeAlreadyMergedOnce() throws Exception {
    fetch = "master";
    Files.write(workdir.resolve("file"), "some content".getBytes());
    options.setForce(true);
    String firstChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", StandardCharsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(0)
        .hash();
    String secondChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", StandardCharsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(1)
        .hash();

    gitApiMockHttpTransport =
        new GitApiMockHttpTransport() {
          @Override
          protected byte[] getContent(String method, String url, MockLowLevelHttpRequest request)
              throws IOException {
            if (method.equals("GET") && url.startsWith("https://localhost:33333/changes/")) {
              String change = changeIdFromRequest(url);
              String result =
                  "["
                      + "{"
                      + "  change_id : \""
                      + change
                      + "\","
                      + "  status : \""
                      + (change.equals(firstChangeId) ? "MERGED" : "NEW")
                      + "\""
                      + "}]";
              return result.getBytes(UTF_8);
            }
            throw new IllegalArgumentException(method + " " + url);
          }
        };

    process(new DummyRevision("origin_ref"));
    assertThat(lastCommitChangeIdLine("origin_ref", repo()))
        .isEqualTo(GerritDestination.CHANGE_ID_LABEL + ": " + secondChangeId);
  }

  @Test
  public void changeAlreadyMergedTooOften() throws Exception {
    fetch = "master";
    Files.write(workdir.resolve("file"), "some content".getBytes());
    options.setForce(true);
    String firstChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", StandardCharsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(0)
        .hash();
    String secondChangeId = "I" + Hashing.sha1()
        .newHasher()
        .putString("origin_ref", Charsets.UTF_8)
        .putString(options.gitDestination.committerEmail, StandardCharsets.UTF_8)
        .putInt(1)
        .hash();
    gitApiMockHttpTransport =
        new GitApiMockHttpTransport() {
          @Override
          protected byte[] getContent(String method, String url, MockLowLevelHttpRequest request)
              throws IOException {
            if (method.equals("GET") && url.startsWith("https://localhost:33333/changes/")) {
              String change = changeIdFromRequest(url);
              String result =
                  "["
                      + "{"
                      + "  change_id : \""
                      + change
                      + "\","
                      + "  status : \"MERGED\""
                      + "}]";
              return result.getBytes(UTF_8);
            }
            throw new IllegalArgumentException(method + " " + url);
          }
        };

    try {
      process(new DummyRevision("origin_ref"));
      fail("Should have thrown RepoException");
    } catch (RepoException expected) {
      assertThat(expected.getMessage()).contains("Unable to find unmerged change for ");
    }
  }

  @Test
  public void specifyTopic() throws Exception {
    fetch = "master";
    options.setForce(true);
    Files.write(workdir.resolve("file"), "some content".getBytes());
    options.gerrit.gerritTopic = "testTopic";
    process(new DummyRevision("origin_ref"));
    boolean correctMessage =
        console
            .getMessages()
            .stream()
            .anyMatch(message -> message.getText().contains("refs/for/master%topic=testTopic"));
    assertThat(correctMessage).isTrue();
  }

  @Test
  public void writesOriginTimestampToAuthorField() throws Exception {
    fetch = "master";

    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    options.setForce(true);

    ZonedDateTime time1 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(355558888),
                                                  ZoneId.of("-08:00"));
    process(new DummyRevision("first_commit").withTimestamp(time1));
    GitTesting.assertAuthorTimestamp(repo(), "refs/for/master", time1);

    git("branch", "master", "refs/for/master");

    Files.write(workdir.resolve("test2.txt"), "some more content".getBytes());
    options.setForce(false);
    ZonedDateTime time2 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(424242420),
                                                  ZoneId.of("-08:00"));
    process(new DummyRevision("first_commit").withTimestamp(
        time2));
    GitTesting.assertAuthorTimestamp(repo(), "refs/for/master",
                                     time2);
  }

  @Test
  public void validationErrorForMissingPullFromRef() throws Exception {
    skylark.evalFails(
        "git.gerrit_destination(\n"
            + "    url = 'file:///foo',\n"
            + ")",
        "missing mandatory positional argument 'fetch'");
  }

  @Test
  public void validationErrorForMissingUrl() throws Exception {
    skylark.evalFails(
        "git.gerrit_destination(\n"
            + "    fetch = 'master',\n"
            + ")",
        "missing mandatory positional argument 'url'");
  }

  @Test
  public void testProcessPushOutput_new() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    GerritProcessPushOutput process =
        new GerritProcessPushOutput(
            LogConsole.readWriteConsole(System.in, new PrintStream(out), /*verbose=*/ false));
    fakeOneCommitInDestination();
    
    ImmutableList<DestinationEffect> result = process.process(
        GERRIT_RESPONSE, /*newReview*/ true, repo(), Changes.EMPTY.getCurrent());

    assertThat(out.toString())
        .contains("INFO: New Gerrit review created at https://some.url.google.com/1234");
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertThat(result.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(result.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(result.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");

    assertThat(result.get(1).getSummary())
        .contains("New Gerrit review created at https://some.url.google.com/1234");
    assertThat(result.get(1).getType()).isEqualTo(Type.CREATED);
    assertThat(result.get(1).getDestinationRef().getId()).isEqualTo("1234");
    assertThat(result.get(1).getDestinationRef().getType()).isEqualTo("gerrit_review");
    assertThat(result.get(1).getDestinationRef().getUrl())
        .isEqualTo("https://some.url.google.com/1234");
  }

  private void fakeOneCommitInDestination() throws IOException, RepoException, ValidationException {
    Path scratchTree = Files.createTempDirectory("workdir");
    GitRepository repo = repo().withWorkTree(scratchTree);
    Files.write(scratchTree.resolve("foo"), new byte[0]);
    repo.add().all().run();
    repo.commit("foo <foo@foo.com>", ZonedDateTime.now(ZoneId.systemDefault()), "not important");
  }

  @Test
  public void testProcessPushOutput_existing() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    GerritProcessPushOutput process = new GerritProcessPushOutput(
        LogConsole.readWriteConsole(System.in, new PrintStream(out), /*verbose=*/ false));

    fakeOneCommitInDestination();

    ImmutableList<DestinationEffect> result =
        process.process(GERRIT_RESPONSE, /*newReview*/false, repo(), Changes.EMPTY.getCurrent());

    assertThat(out.toString())
        .contains("INFO: Updated existing Gerrit review at https://some.url.google.com/1234");

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getErrors()).isEmpty();
    assertThat(result.get(0).getType()).isEqualTo(Type.CREATED);
    assertThat(result.get(0).getDestinationRef().getType()).isEqualTo("commit");
    assertThat(result.get(0).getDestinationRef().getId()).matches("[0-9a-f]{40}");

    assertThat(result.get(1).getSummary())
        .contains("Updated existing Gerrit review at https://some.url.google.com/1234");
    assertThat(result.get(1).getType()).isEqualTo(Type.UPDATED);
    assertThat(result.get(1).getDestinationRef().getId()).isEqualTo("1234");
    assertThat(result.get(1).getDestinationRef().getType()).isEqualTo("gerrit_review");
    assertThat(result.get(1).getDestinationRef().getUrl())
        .isEqualTo("https://some.url.google.com/1234");
  }

  @Test
  public void testPushToNonDefaultRef() throws Exception {
    fetch = "master";
    pushToRefsFor = "testPushToRef";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    options.setForce(true);
    process(new DummyRevision("origin_ref"));

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "refs/for/testPushToRef");
    assertThat(showResult).contains("some content");
  }

  @Test
  public void testPushToNonMasterDefaultRef() throws Exception {
    fetch = "fooze";
    Files.write(workdir.resolve("test.txt"), "some content".getBytes());
    options.setForce(true);
    process(new DummyRevision("origin_ref"));

    // Make sure commit adds new text
    String showResult = git("--git-dir", repoGitDir.toString(), "show", "refs/for/fooze");
    assertThat(showResult).contains("some content");
  }

  @Test
  public void canExcludeDestinationPathFromWorkflow() throws Exception {
    fetch = "master";

    Path scratchWorkTree = Files.createTempDirectory("GitDestinationTest-scratchWorkTree");
    Files.write(scratchWorkTree.resolve("excluded.txt"), "some content".getBytes(UTF_8));
    repo().withWorkTree(scratchWorkTree)
        .add().files("excluded.txt").run();
    repo().withWorkTree(scratchWorkTree)
        .simpleCommand("commit", "-m", "message");

    Files.write(workdir.resolve("normal_file.txt"), "some more content".getBytes(UTF_8));
    excludedDestinationPaths = ImmutableList.of("excluded.txt");
    process(new DummyRevision("ref"));
    GitTesting.assertThatCheckout(repo(), "refs/for/master")
        .containsFile("excluded.txt", "some content")
        .containsFile("normal_file.txt", "some more content")
        .containsNoMoreFiles();
  }
}
