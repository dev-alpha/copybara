// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.util.console.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.util.console.AnsiColor;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.LogConsole;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * A testing console that allows programming the user input and intercepts all the messages.
 *
 * <p>It also writes the output to a {@link LogConsole} for debug.
 */
public final class TestingConsole implements Console {

  public static final class Message {
    private final MessageType type;
    private final String text;

    @Override
    public String toString() {
      return type + ": " + text;
    }

    Message(MessageType type, String text) {
      this.type = type;
      this.text = text;
    }

    public MessageType getType() {
      return type;
    }

    public String getText() {
      return text;
    }
  }

  private enum PromptResponse {
    YES, NO,
  }

  public enum MessageType {
    ERROR, WARNING, INFO, PROGRESS;
  }

  private final Console outputConsole = LogConsole.writeOnlyConsole(System.out);
  private final ArrayDeque<PromptResponse> programmedResponses = new ArrayDeque<>();
  private final ArrayList<Message> messages = new ArrayList<>();

  public TestingConsole respondYes() {
    this.programmedResponses.addLast(PromptResponse.YES);
    return this;
  }

  public TestingConsole respondNo() {
    this.programmedResponses.addLast(PromptResponse.NO);
    return this;
  }

  /**
   * Use {@code LogSubject}
   *
   * TODO(malcon): Remove in next change
   */
  @Deprecated
  public int countTimesInLog(MessageType type, String regex) {
    int count = 0;
    for (Message message : messages) {
      if (message.type.equals(type) && message.text.matches(regex)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Returns the list of messages in the original order that they were logged.
   */
  public ImmutableList<Message> getMessages() {
    return ImmutableList.copyOf(messages);
  }

  @Override
  public void startupMessage() {
    outputConsole.startupMessage();
  }

  @Override
  public void error(String message) {
    messages.add(new Message(MessageType.ERROR, message));
    outputConsole.error(message);
  }

  @Override
  public void warn(String message) {
    messages.add(new Message(MessageType.WARNING, message));
    outputConsole.warn(message);
  }

  @Override
  public void info(String message) {
    messages.add(new Message(MessageType.INFO, message));
    outputConsole.warn(message);
  }

  @Override
  public void progress(String message) {
    messages.add(new Message(MessageType.PROGRESS, message));
    outputConsole.progress(message);
  }

  @Override
  public boolean promptConfirmation(String message) {
    Preconditions.checkState(!programmedResponses.isEmpty(), "No more programmed responses.");
    warn(message);
    return programmedResponses.removeFirst() == PromptResponse.YES;
  }

  @Override
  public String colorize(AnsiColor ansiColor, String message) {
    return outputConsole.colorize(ansiColor, message);
  }
}
