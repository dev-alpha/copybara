package com.google.copybara;

import static com.google.copybara.WorkflowOptions.CHANGE_REQUEST_PARENT_FLAG;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Origin.ChangesVisitor;
import com.google.copybara.Origin.VisitResult;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.transform.ValidationException;
import com.google.copybara.util.console.ProgressPrefixConsole;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Workflow type to run between origin an destination
 */
public enum WorkflowMode {
  /**
   * Create a single commit in the destination with new tree state.
   */
  @DocField(description = "Create a single commit in the destination with new tree state.")
  SQUASH {
    @Override
    <O extends Origin<O>> void run(Workflow<O>.RunHelper runHelper)
        throws RepoException, IOException, EnvironmentException, ValidationException {
      runHelper.migrate(
          runHelper.getResolvedRef(),
          // SQUASH workflows always use the default author
          runHelper.getAuthoring().getDefaultAuthor(),
          runHelper.getConsole(),
          runHelper.changesSummaryMessage());
    }
  },

  /**
   * Import each origin change individually.
   */
  @DocField(description = "Import each origin change individually.")
  ITERATIVE {
    @Override
    <O extends Origin<O>> void run(Workflow<O>.RunHelper runHelper)
        throws RepoException, IOException, EnvironmentException, ValidationException {
      ImmutableList<Change<O>> changes = runHelper.changesSinceLastImport();
      for (int i = 0; i < changes.size(); i++) {
        Change<O> change = changes.get(i);
        String prefix = String.format(
            "[%2d/%d] Migrating change %s: ", i + 1, changes.size(),
            change.getReference().asString());

        runHelper.migrate(
            change.getReference(),
            change.getAuthor(),
            new ProgressPrefixConsole(prefix, runHelper.getConsole()),
            change.getMessage());
      }
    }
  },
  @DocField(description = "Import an origin tree state diffed by a common parent"
      + " in destination. This could be a GH Pull Request, a Gerrit Change, etc.")
  CHANGE_REQUEST {
    @Override
    <O extends Origin<O>> void run(Workflow<O>.RunHelper helper)
        throws RepoException, IOException, EnvironmentException, ValidationException {
      final AtomicReference<String> requestParent = new AtomicReference<>(
          helper.workflowOptions().changeBaseline);
      final String originLabelName = helper.getDestination().getLabelNameWhenOrigin();
      if (Strings.isNullOrEmpty(requestParent.get())) {
        helper.getOrigin().visitChanges(helper.getResolvedRef(), new ChangesVisitor() {
          @Override
          public VisitResult visit(Change<?> change) {
            if (change.getLabels().containsKey(originLabelName)) {
              requestParent.set(change.getLabels().get(originLabelName));
              return VisitResult.TERMINATE;
            }
            return VisitResult.CONTINUE;
          }
        });
      }

      if (Strings.isNullOrEmpty(requestParent.get())) {
        throw new ValidationException(
            "Cannot find matching parent commit in in the destination. Use '"
                + CHANGE_REQUEST_PARENT_FLAG
                + "' flag to force a parent commit to use as baseline in the destination.");
      }
      Change<O> change = helper.getOrigin().change(helper.getResolvedRef());
      helper.migrate(helper.getResolvedRef(), change.getAuthor(), helper.getConsole(),
          change.getMessage(), requestParent.get());
    }
  };

  abstract <O extends Origin<O>> void run(Workflow<O>.RunHelper runHelper)
      throws RepoException, IOException, EnvironmentException, ValidationException;

  // TODO(copybara): Mirror individual changes from origin to destination. Requires a
  // that origin and destination are of the same time and that they support mirroring
  //MIRROR
}
