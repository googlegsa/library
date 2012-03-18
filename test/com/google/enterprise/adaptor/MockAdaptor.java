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

import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;
import java.io.IOException;

/**
 * Mock of {@link Adaptor}.
 */
class MockAdaptor extends AbstractAdaptor {
  public byte[] documentBytes = new byte[] {1, 2, 3};

  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException,
      IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getDocContent(Request request, Response response)
      throws IOException {
    response.getOutputStream().write(documentBytes);
  }
}
