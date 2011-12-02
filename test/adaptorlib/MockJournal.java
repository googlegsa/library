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

package adaptorlib;

import java.util.List;

/**
 * Mock {@link Journal} that always throws {@link
 * UnsupportedOperationException}.
 */
public class MockJournal extends Journal {
  public MockJournal(TimeProvider timeProvider) {
    super(timeProvider);
  }

  @Override
  void recordDocIdPush(List<DocIdPusher.Record> pushed) {
    throw new UnsupportedOperationException();
  }

  @Override
  void recordGsaContentRequest(DocId docId) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  void recordNonGsaContentRequest(DocId requested) {
    throw new UnsupportedOperationException();
  }

  @Override
  void recordRequestProcessingStart() {
    throw new UnsupportedOperationException();
  }

  @Override
  void recordRequestProcessingEnd(long responseSize) {
    throw new UnsupportedOperationException();
  }

  @Override
  JournalSnapshot getSnapshot() {
    throw new UnsupportedOperationException();
  }
}
