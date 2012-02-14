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

import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

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
  public Record pushRecords(Iterable<Record> records, PushErrorHandler handler)
      throws InterruptedException;

  /**
   * Push named resources immediately and block until they are successfully
   * provided to the GSA or the error handler gives up. This method can take a
   * while in error conditions, but is not something that generally needs to be
   * avoided.
   *
   * <p>Named resources are {@code DocId}s without any content or metadata, that
   * only exist for ACL inheritance. These {@code DocId} will never be visible
   * to the user and have no meaning outside of ACL processing.
   *
   * <p>If you plan on using the return code, then the provided map should have
   * a predictable iteration order, like {@link java.util.TreeMap}.
   *
   * <p>Equivalent to {@code pushNamedResources(resources, null)}.
   *
   * @return {@code null} on success, otherwise the first DocId to fail
   * @see #pushNamedResources(Map, PushErrorHandler)
   */
  public DocId pushNamedResources(Map<DocId, Acl> resources)
      throws InterruptedException;

  /**
   * Push named resources immediately and block until they are successfully
   * provided to the GSA or the error handler gives up. This method can take a
   * while in error conditions, but is not something that generally needs to be
   * avoided.
   *
   * <p>Named resources are {@code DocId}s without any content or metadata, that
   * only exist for ACL inheritance. These {@code DocId} will never be visible
   * to the user and have no meaning outside of ACL processing.
   *
   * <p>If you plan on using the return code, then the provided map should have
   * a predictable iteration order, like {@link java.util.TreeMap}.
   *
   * <p>If handler is {@code null}, then a default error handler is used.
   *
   * @return {@code null} on success, otherwise the first DocId to fail
   */
  public DocId pushNamedResources(Map<DocId, Acl> resources,
                                  PushErrorHandler handler)
      throws InterruptedException;

  /** Contains DocId and other feed file record attributes. */
  public static final class Record implements DocIdSender.Item {
    private final DocId id; 
    private final boolean delete;
    private final Date lastModified;
    private final URI link;
    private final boolean crawlImmediately;
    private final boolean crawlOnce;
    private final boolean lock;
  
    private Record(DocId docid, boolean delete, Date lastModified,
        URI link, boolean crawlImmediately, boolean crawlOnce, boolean lock) {
      this.id = docid;
      this.delete = delete;
      this.lastModified = lastModified;
      this.link = link;
      this.crawlImmediately = crawlImmediately;
      this.crawlOnce = crawlOnce;
      this.lock = lock;
    }
    
    public DocId getDocId() {
      return id;
    }
  
    public boolean isToBeDeleted() {
      return delete;
    }
  
    public Date getLastModified() {
      return lastModified;
    }
  
    public URI getResultLink() {
      return link;
    }
  
    public boolean isToBeCrawledImmediately() {
      return crawlImmediately;
    }
  
    public boolean isToBeCrawledOnce() {
      return crawlOnce;
    }
  
    public boolean isToBeLocked() {
      return lock;
    }
  
    @Override
    public boolean equals(Object o) {
      boolean same = false;
      if (null != o && this.getClass().equals(o.getClass())) {
        Record other = (Record) o;
        same = this.id.equals(other.id)
            && (this.delete == other.delete)
            && (this.crawlImmediately == other.crawlImmediately)
            && (this.crawlOnce == other.crawlOnce)
            && (this.lock == other.lock)
            && equalsNullSafe(lastModified, other.lastModified)
            && equalsNullSafe(link, other.link);
      } 
      return same;
    }
  
    @Override
    public int hashCode() {
      Object members[] = new Object[] { id, delete, lastModified, link,
          crawlImmediately, crawlOnce, lock };
      return Arrays.hashCode(members);
    }
  
    @Override
    public String toString() {
      return "Record(docid=" + id.getUniqueId()
          + ",delete=" + delete
          + ",lastModified=" + lastModified
          + ",resultLink=" + link
          + ",crawlImmediately=" + crawlImmediately
          + ",crawlOnce=" + crawlOnce
          + ",lock=" + lock + ")";
    }
  
    private static boolean equalsNullSafe(Object a, Object b) {
      boolean same;
      if (null == a && null == b) {
        same = true;
      } else if (null != a && null != b) {
        same = a.equals(b);
      } else {
        same = false;
      }
      return same;
    }

    /**
     * Used to create instances of Record, which are immutable.
     * DocId is required.
     */
    public static class Builder {
      private DocId docid = null;
      private boolean delete = false;
      private Date lastModified = null;
      private URI link = null;
      private boolean crawlImmediately = false;
      private boolean crawlOnce = false;
      private boolean lock = false;
  
      public Builder(DocId id) {
        if (null == id) {
          throw new NullPointerException();
        }
        docid = id;
      }

      /** Makes Builder that can duplicate a record. */
      public Builder(Record startPoint) {
        this.docid = startPoint.id;
        this.delete = startPoint.delete;
        this.lastModified = startPoint.lastModified;
        this.link = startPoint.link;
        this.crawlImmediately = startPoint.crawlImmediately;
        this.crawlOnce = startPoint.crawlOnce;
        this.lock = startPoint.lock;
      }

      public Builder setDocId(DocId id) {
        if (null == id) {
          throw new NullPointerException();
        }
        this.docid = id;
        return this;
      }
    
      public Builder setDeleteFromIndex(boolean b) {
        this.delete = b;
        return this;
      }
    
      public Builder setLastModified(Date lastModified) {
        this.lastModified = lastModified;
        return this;
      }
    
      public Builder setResultLink(URI link) {
        this.link = link;
        return this;
      }
    
      public Builder setCrawlImmediately(boolean b) {
        this.crawlImmediately = crawlImmediately;
        return this;
      }
    
      public Builder setCrawlOnce(boolean b) {
        this.crawlOnce = crawlOnce;
        return this;
      }
    
      public Builder setLock(boolean b) {
        this.lock = lock;
        return this;
      }
  
      /** Creates single instance of Record.  Does not reset builder. */
      public Record build() {
        return new Record(docid, delete, lastModified,
            link, crawlImmediately, crawlOnce, lock);
      }
    }
  }
}
