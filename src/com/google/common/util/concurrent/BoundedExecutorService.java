// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.common.util.concurrent;

import java.util.List;
import java.util.concurrent.*;

/**
 * Trash class to allow code to compile unmodified.
 */
public class BoundedExecutorService extends AbstractExecutorService {
  public BoundedExecutorService(Object ... params) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isShutdown() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isTerminated() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void shutdown() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Runnable> shutdownNow() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void execute(Runnable command) {
    throw new UnsupportedOperationException();
  }
}
