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

package com.google.copybara.git.gerritapi;

import static com.google.copybara.git.gerritapi.GerritApiUtil.parseTimestamp;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#change-info
 */
/** A Gerrit change that can be read from a feedback migration. */
@SkylarkModule(
    name = "change",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE,
    doc = "Gerrit change that can be read from a feedback migration.",
    documented = false
)
public class ChangeInfo implements SkylarkValue {

  @Key private String id;
  @Key private String project;
  @Key private String branch;
  @Key private String topic;
  @Key("change_id") private String changeId;
  @Key private String subject;
  @Key private String status;
  @Key private String created;
  @Key private String updated;
  @Key private String submitted;
  @Key("_number") private long number;
  @Key private AccountInfo owner;
  @Key private Map<String, LabelInfo> labels;
  @Key private List<ChangeMessageInfo> messages;
  @Key("current_revision") private String currentRevision;
  @Key("revisions") private Map<String, RevisionInfo> allRevisions;
  @Key("_more_changes") private boolean moreChanges;

  @SkylarkCallable(name = "id", doc = "The id of this change", structField = true, allowReturnNones = true)
  public String getId() {
    return id;
  }

  @SkylarkCallable(name = "project", doc = "The change project", structField = true, allowReturnNones = true)
  public String getProject() {
    return project;
  }

  @SkylarkCallable(
      name = "branch",
      doc = "The change branch",
      structField = true,
      allowReturnNones = true)
  public String getBranch() {
    return branch;
  }

  @SkylarkCallable(
      name = "topic",
      doc = "The change topic",
      structField = true,
      allowReturnNones = true)
  public String getTopic() {
    return topic;
  }

  @SkylarkCallable(
      name = "change_id",
      doc = "The change 'ChangeId'",
      structField = true,
      allowReturnNones = true)
  public String getChangeId() {
    return changeId;
  }

  @SkylarkCallable(
      name = "subject",
      doc = "The change subject",
      structField = true,
      allowReturnNones = true)
  public String getSubject() {
    return subject;
  }

  public ChangeStatus getStatus() {
    return ChangeStatus.valueOf(status);
  }

  @SkylarkCallable(
      name = "status",
      doc = "The change status",
      structField = true,
      allowReturnNones = true)
  public String getStatusAsString() {
    return status;
  }

  public ZonedDateTime getCreated() {
    return parseTimestamp(created);
  }

  @SkylarkCallable(name = "created",
      doc = "The created date time. Example: 2011-12-03T10:15:30+01:00",
      structField = true, allowReturnNones = true)
  public String getCreatedFmt() {
    return created;
  }

  public ZonedDateTime getUpdated() {
    return parseTimestamp(updated);
  }

  @SkylarkCallable(name = "updated",
      doc = "The updated date time. Example: 2011-12-03T10:15:30+01:00",
      structField = true, allowReturnNones = true)
  public String getUpdatedFmt() {
    return updated;
  }

  public ZonedDateTime getSubmitted() {
    return parseTimestamp(submitted);
  }

  @SkylarkCallable(name = "submitted",
      doc = "The submitted date time. Example: 2011-12-03T10:15:30+01:00",
      structField = true, allowReturnNones = true)
  public String getSubmittedFmt() {
    return submitted;
  }

  public long getNumber() {
    return number;
  }

  @SkylarkCallable(
      name = "number",
      doc = "The change number",
      structField = true,
      allowReturnNones = true)
  public String getNumberAsString() {
    return Long.toString(number);
  }

  public AccountInfo getOwner() {
    return owner;
  }

  public ImmutableMap<String, LabelInfo> getLabels() {
    return ImmutableMap.copyOf(labels);
  }

  public List<ChangeMessageInfo> getMessages() {
    return ImmutableList.copyOf(messages);
  }

  @SkylarkCallable(
      name = "current_revision",
      doc = "The change current revision",
      structField = true,
      allowReturnNones = true)
  public String getCurrentRevision() {
    return currentRevision;
  }

  public ImmutableMap<String, RevisionInfo> getAllRevisions() {
    return ImmutableMap.copyOf(allRevisions);
  }

  public boolean isMoreChanges() {
    return moreChanges;
  }

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append(toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("project", project)
        .add("branch", branch)
        .add("topic", topic)
        .add("changeId", changeId)
        .add("subject", subject)
        .add("status", status)
        .add("created", created)
        .add("updated", updated)
        .add("submitted", submitted)
        .add("number", number)
        .add("owner", owner)
        .add("labels", labels)
        .add("messages", messages)
        .add("currentRevision", currentRevision)
        .add("allRevisions", allRevisions)
        .add("moreChanges", moreChanges)
        .toString();
  }
}
