// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor;

import com.google.enterprise.adaptor.AbstractDocIdPusher;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.IncrementalAdaptorPoller;
import com.google.enterprise.adaptor.PollingIncrementalAdaptor;
import com.google.enterprise.adaptor.PushErrorHandler;
import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Tests for {@link IncrementalAdaptorPoller}.
 */
public class IncrementalAdaptorPollerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testInvalidInit() throws Exception {
    thrown.expect(NullPointerException.class);
    new IncrementalAdaptorPoller(null, new FakeDocIdPusher());
  }

  @Test
  public void testInvalidInit2() throws Exception {
    thrown.expect(NullPointerException.class);
    new IncrementalAdaptorPoller(new PollingIncrMockAdaptor(), null);
  }

  @Test
  public void testDoubleStart() throws Exception {
    IncrementalAdaptorPoller poller = new IncrementalAdaptorPoller(
        new PollingIncrMockAdaptor(), new FakeDocIdPusher());
    try {
      poller.start(60000);
      thrown.expect(IllegalStateException.class);
      poller.start(60000);
    } finally {
      poller.cancel();
    }
  }

  @Test
  public void testInterruptOnCancel() throws Exception {
    class PollingBlockingIncrAdaptor extends PollingIncrMockAdaptor {
      private volatile boolean interrupted;

      @Override
      public void getModifiedDocIds(DocIdPusher pusher)
         throws InterruptedException, IOException {
        super.getModifiedDocIds(pusher);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex) {
          interrupted = true;
          throw ex;
        }
      }
    };
    PollingBlockingIncrAdaptor adaptor = new PollingBlockingIncrAdaptor();
    IncrementalAdaptorPoller poller
        = new IncrementalAdaptorPoller(adaptor, new FakeDocIdPusher());
    try {
      poller.start(60000);
      assertNotNull(adaptor.queue.poll(1, TimeUnit.SECONDS));
      assertFalse(adaptor.interrupted);
    } finally {
      poller.cancel();
    }
    assertTrue(adaptor.interrupted);
  }

  private static class FakeDocIdPusher extends AbstractDocIdPusher {
    @Override
    public Record pushRecords(Iterable<Record> records,
                                PushErrorHandler handler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DocId pushNamedResources(Map<DocId, Acl> resources,
                                    PushErrorHandler handler) {
      throw new UnsupportedOperationException();
    }
  }

  private static class PollingIncrMockAdaptor extends MockAdaptor
      implements PollingIncrementalAdaptor {
    public final ArrayBlockingQueue<Object> queue
        = new ArrayBlockingQueue<Object>(1);

    @Override
    public void getModifiedDocIds(DocIdPusher pusher) throws IOException,
        InterruptedException {
      queue.offer(new Object());
    }
  }
}
