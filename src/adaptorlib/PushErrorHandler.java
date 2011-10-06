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

/**
 * Interface for handling errors encountered during pushing of {@code DocId}s.
 */
public interface PushErrorHandler {
  /**
   * {@link GsaCommunicationHandler#pushDocIds} had a failure connecting with
   * GSA to send a batch. The thrown exception is provided as well as the number
   * of times that this batch was attempted to be sent. Return {@code true} to
   * retry, perhaps after a Thread.sleep() of some time.
   */
  public boolean handleFailedToConnect(Exception ex, int ntries)
      throws InterruptedException;

  /**
   * {@link GsaCommunicationHandler#pushDocIds} had a failure writing to the GSA
   * while sending a batch.  The thrown exception is provided as well as the
   * number of times that this batch was attempted to be sent. Return {@code
   * true} to retry, perhaps after a Thread.sleep() of some time.
   */
  public boolean handleFailedWriting(Exception ex, int ntries)
      throws InterruptedException;

  /**
   * {@link GsaCommunicationHandler#pushDocIds} had a failure reading response
   * from GSA. The thrown exception is provided as well as the number of times
   * that this batch was attempted to be sent. Return {@code true} to retry,
   * perhaps after a Thread.sleep() of some time.
   */
  public boolean handleFailedReadingReply(Exception ex, int ntries)
      throws InterruptedException;
}
