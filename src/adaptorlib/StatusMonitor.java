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

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

class StatusMonitor {
  private List<StatusSource> sources = new CopyOnWriteArrayList<StatusSource>();

  public Map<StatusSource, Status> retrieveStatuses() {
    Map<StatusSource, Status> statuses
        = new LinkedHashMap<StatusSource, Status>(sources.size() * 2);
    for (StatusSource source : sources) {
      statuses.put(source, source.retrieveStatus());
    }
    return statuses;
  }

  public void addSource(StatusSource source) {
    if (source == null) {
      throw new NullPointerException();
    }
    sources.add(source);
  }

  public void removeSource(StatusSource source) {
    sources.remove(source);
  }
}
