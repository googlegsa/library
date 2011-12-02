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

import java.io.*;

/**
 * Interface provided to {@link Adaptor#getDocContent} for performing the
 * actions needed to satisfy a request. If the {@code DocId} provided by {@link
 * Request#getDocId} does not exist, a {@link java.io.FileNotFoundException}
 * should be thrown.
 *
 * <p>There are several ways that a request can be processed. In the simplest
 * case an Adaptor always sets different pieces of metadata, calls {@link
 * #getOutputStream}, and writes the document contents.
 *
 * <p>For improved efficiency during recrawl by the GSA, an Adaptor should check
 * {@link Request#hasChangedSinceLastAccess} and call {@link
 * #respondNotModified} when it is {@code true}. This prevents the Adaptor from
 * ever needing to retrieve the document contents and metadata.
 */
public interface Response {
  /**
   * Respond to the GSA or other client that it already has the latest version
   * of a file and its metadata. If you have called other methods on this object
   * to provide various metadata, the effects of those methods will be ignored.
   *
   * <p>If called, this must be the last call to this interface. If the document
   * does not exist, you must throw {@link java.io.FileNotFoundException} and
   * not call this method. Once you call this method, for the rest of the
   * processing, exceptions may no longer be communicated to clients cleanly.
   */
  public void respondNotModified() throws IOException;

  /**
   * Get stream to write document contents to. There is no need to flush or
   * close the {@code OutputStream} when done.
   *
   * <p>If called, this must be the last call to this interface (although, for
   * convenience, you may call this method multiple times). If the document
   * does not exist, you must throw {@link java.io.FileNotFoundException} and
   * not call this method. Once you call this method, for the rest of the
   * processing, exceptions may no longer be communicated to clients cleanly.
   */
  public OutputStream getOutputStream() throws IOException;

  /**
   * Describe the content type of the document.
   */
  public void setContentType(String contentType);

  /**
   * Provide metadata that apply to the document.
   */
  public void setMetadata(Metadata m);
}
