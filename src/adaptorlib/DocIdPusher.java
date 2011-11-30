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
 * Interface that allows at-will pushing of {@code DocId}s to the GSA.
 */
public interface DocIdPusher {
  /**
   * Push {@code DocId}s immediately and block until they are successfully
   * provided to the GSA or the error handler gives up. This method can take a
   * while in error conditions, but is not something that generally needs to be
   * avoided.
   *
   * <p>Equivalent to {@code pushDocIds(docIds, null)} and {@link
   * #pushRecords(Iterable)} with empty metadata for each {@code Record}.
   *
   * @return {@code null} on success, otherwise the first DocId to fail
   * @see #pushDocIds(Iterable, PushErrorHandler)
   */
  public DocId pushDocIds(Iterable<DocId> docIds)
      throws InterruptedException;

  /**
   * Push {@code DocId}s immediately and block until they are successfully
   * provided to the GSA or the error handler gives up. This method can take a
   * while in error conditions, but is not something that generally needs to be
   * avoided.
   *
   * <p>If handler is {@code null}, then a default error handler is used.
   *
   * <p>Equivalent to {@link #pushRecords(Iterable, PushErrorHandler)}
   * with empty metadata for each {@code Record}.
   *
   * @return {@code null} on success, otherwise the first DocId to fail
   */
  public DocId pushDocIds(Iterable<DocId> docIds, PushErrorHandler handler)
      throws InterruptedException;

  /**
   * Push {@code Record}s immediately and block until they are successfully
   * provided to the GSA or the error handler gives up. This method can take a
   * while in error conditions, but is not something that generally needs to be
   * avoided.
   *
   * <p>Equivalent to {@code pushRecords(records, null)}.
   *
   * @return {@code null} on success, otherwise the first Record to fail
   * @see #pushRecords(Iterable, PushErrorHandler)
   */
  public Record pushRecords(Iterable<Record> records)
      throws InterruptedException;

  /**
   * Push {@code Record}s immediately and block until they are successfully
   * provided to the GSA or the error handler gives up. This method can take a
   * while in error conditions, but is not something that generally needs to be
   * avoided.
   *
   * <p>If handler is {@code null}, then a default error handler is used.
   *
   * @return {@code null} on success, otherwise the first Record to fail
   */
  public Record pushRecords(Iterable<Record> records,
                              PushErrorHandler handler)
      throws InterruptedException;

  /**
   * DocId and PushAttributes pair for passing with {@link DocIdPusher}.
   */
  public static class Record {
    private final DocId docId;
    private final PushAttributes pushAttrs;
  
    public Record(DocId docId, PushAttributes pushAttrs) {
      if (docId == null || pushAttrs == null) {
        throw new NullPointerException();
      }
      this.docId = docId;
      this.pushAttrs = pushAttrs;
    }
  
    public DocId getDocId() {
      return docId;
    }
  
    public PushAttributes getPushAttributes() {
      return pushAttrs;
    }
  
    @Override
    public boolean equals(Object o) {
      if (o == null || !getClass().equals(o.getClass())) {
        return false;
      }
      Record docRecord = (Record) o;
      return docId.equals(docRecord.docId)
          && pushAttrs.equals(docRecord.pushAttrs);
    }
  
    @Override
    public int hashCode() {
      return docId.hashCode() ^ pushAttrs.hashCode();
    }
  
    @Override
    public String toString() {
      return "DocIdPusher.Record(" + docId + "," + pushAttrs + ")";
    }
  }
}
