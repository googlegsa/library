package adaptorlib;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Interface for user-specific implementation details of an Adaptor.
 * Implementations must be thread-safe.
 *
 * @see adaptorlib.examples.AdaptorTemplate
 */
public interface Adaptor {
  /**
   * Provides contents and metadata of particular document.
   */
  public void getDocContent(Request request, Response response)
      throws IOException;

  /**
   * Pushes all the {@code DocId}s that are suppose to be indexed by the GSA.
   * This will frequently involve re-sending {@code DocId}s to the GSA, but this
   * allows healing previous errors and cache inconsistencies. This method
   * should determine a list of {@code DocId}s to push and call {@link
   * DocIdPusher#pushDocIds} one or more times.
   *
   * <p>{@code pusher} is provided as convenience and is the same object
   * provided to {@link #setDocIdPusher} previously. This method may take a
   * while and implementations are free to call {@link Thread#sleep}
   * occasionally to reduce load.
   */
  public void getDocIds(DocIdPusher pusher) throws IOException,
      InterruptedException;

  /**
   * Determines whether the user identified is allowed to access the {@code
   * DocId}s.
   *
   * @param userIdentifier User to authorize, or {@code null} for anonymous
   *        users
   * @param ids Collection of {@code DocId}s that need to be checked
   * @return an {@code AuthzStatus} for each {@code DocId} provided in {@code
   *         ids}
   */
  public Map<DocId, AuthzStatus> isUserAuthorized(String userIdentifier,
      Collection<DocId> ids) throws IOException;

  /**
   * Provides a {@code DocIdPusher} object to be called whenever the Adaptor
   * wishes. This allows doing event-based incremental pushes at any time. The
   * method is called as soon as the Adaptor is provided to the {@code
   * GsaCommunicationHandler}.
   */
  public void setDocIdPusher(DocIdPusher pusher);

  /**
   * Interface provided to {@link Adaptor#getDocContent} for describing the
   * action that should be taken.
   */
  public static interface Request {
    /**
     * Returns whether the Adaptor needs to provide the metadata and contents of
     * a document. If this method returns false, then only verifying that the
     * document exists is enough. Adaptor writers are not required to pay
     * attention to this method, but doing so increases performance since the
     * Adaptor does less work.
     */
    public boolean needDocumentContent();

    /**
     * Returns {@code true} if the GSA or other client's current copy of the
     * document was retrieved after the {@code lastModified} date; {@code false}
     * otherwise. {@code lastModified} must be in GMT.
     *
     * <p>If {@code false}, the client does not need to be re-sent the data,
     * since what they have cached is the most recent version. In this case, you
     * should then call {@link Response#respondNotModified}.
     */
    public boolean hasChangedSinceLastAccess(Date lastModified);

    /**
     * Returns the last time a GSA or other client retrieved the data, or {@code
     * null} if none was provided by the client. The returned {@code Date} is in
     * GMT.
     *
     * <p>This is useful for determining if the client needs to be re-sent the
     * data since what they have cached may be the most recent version. If the
     * client is up-to-date, then call {@link Response#respondNotModified}.
     *
     * @return date in GMT client last accessed the DocId or {@code null}
     */
    public Date getLastAccessTime();

    /**
     * Provides the document ID for the document that is being requested. DocId
     * was not necessarily provided previously by the Adaptor; it is
     * client-provided and must not be trusted.
     */
    public DocId getDocId();
  }

  /**
   * Interface provided to {@link Adaptor#getDocContent} for performing the
   * actions needed to satisfy a request. If the {@code DocId} provided by
   * {@link Request#getDocId} does not exist, a {@link
   * java.io.FileNotFoundException} should be thrown.
   *
   * <p>There are several ways that a request can be processed. In the simplest
   * case an Adaptor always sets different pieces of metadata, calls {@link
   * #getOutputStream}, and writes the document contents.
   *
   * <p>For improved efficiency during recrawl by the GSA, an Adaptor should
   * check {@link Request#hasChangedSinceLastAccess} and call {@link
   * #respondNotModified} when it is {@code true}. This prevents the Adaptor
   * from ever needing to retrieve the document contents and metadata.
   *
   * <p>For improved efficiency during late authorization binding requests, an
   * Adaptor should check the {@link Request#needDocumentContent} method to see
   * if the contents and metadata need to be retrieved. If the Adaptor checks
   * {@link Request#hasChangedSinceLastAccess}, that processing should take
   * precedence over this one.
   */
  public static interface Response {
    /**
     * Respond to the GSA or other client that it already has the latest version
     * of a file and its metadata. If you have called other methods on this
     * object to provide various metadata, the effects of those methods will be
     * ignored.
     *
     * <p>If called, this must be the last call to this interface. If the
     * document does not exist, you must throw {@link
     * java.io.FileNotFoundException} and not call this method. Once you call
     * this method, for the rest of the processing, exceptions may no longer be
     * communicated to clients cleanly.
     */
    public void respondNotModified();

    /**
     * Get stream to write document contents to. Always returns a writable
     * stream, but during a HEAD request the stream may not do anything. There
     * is no need to flush or close the {@code OutputStream} when done.
     *
     * <p>If called, this must be the last call to this interface (although, for
     * convenience, you may call this method multiple times). If the document
     * does not exist, you must throw {@link java.io.FileNotFoundException} and
     * not call this method. Once you call this method, for the rest of the
     * processing, exceptions may no longer be communicated to clients cleanly.
     */
    public OutputStream getOutputStream();

    /**
     * Describe the content type of the document.
     */
    public void setContentType(String contentType);

    /**
     * Describe ACLs that apply to the document.
     */
    public void setDocReadPermissions(DocReadPermissions acl);
  }

  /**
   * Interface that allows at-will pushing of {@code DocId}s to the GSA.
   */
  public static interface DocIdPusher {
    /**
     * Push {@code DocId}s immediately and block until they are successfully
     * provided to the GSA or the error handler gives up. This process can take
     * a while in error conditions, but is not something that generally needs to
     * be avoided.
     *
     * <p>Equivalent to {@code pushDocIds(docIds, null)}.
     *
     * @return {@code null} on success, otherwise the first DocId to fail
     * @see #pushDocIds(Iterable, Adaptor.PushErrorHandler)
     */
    public DocId pushDocIds(Iterable<DocId> docIds)
        throws InterruptedException;

    /**
     * Push {@code DocId}s immediately and block until they are successfully
     * provided to the GSA or the error handler gives up. This process can take
     * a while in error conditions, but is not something that generally needs to
     * be avoided.
     *
     * <p>If handler is {@code null}, then a default error handler is used.
     *
     * @return {@code null} on success, otherwise the first DocId to fail
     */
    public DocId pushDocIds(Iterable<DocId> docIds, PushErrorHandler handler)
        throws InterruptedException;
  }

  /**
   * Interface for handling errors encountered during pushing of {@code DocId}s.
   */
  public static interface PushErrorHandler {
    /**
     * {@link GsaCommunicationHandler#pushDocIds} had a failure connecting with
     * GSA to send a batch. The thrown exception is provided as well as the
     * number of times that this batch was attempted to be sent. Return true to
     * retry, perhaps after a Thread.sleep() of some time.
     */
    public boolean handleFailedToConnect(Exception ex, int ntries)
        throws InterruptedException;

    /**
     * {@link GsaCommunicationHandler#pushDocIds} had a failure writing to the
     * GSA while sending a batch.  The thrown exception is provided as well as
     * the number of times that this batch was attempted to be sent. Return true
     * to retry, perhaps after a Thread.sleep() of some time.
     */
    public boolean handleFailedWriting(Exception ex, int ntries)
        throws InterruptedException;

    /**
     * {@link GsaCommunicationHandler#pushDocIds} had a failure reading response
     * from GSA. The thrown exception is provided as well as the number of times
     * that this batch was attempted to be sent. Return true to retry, perhaps
     * after a Thread.sleep() of some time.
     */
    public boolean handleFailedReadingReply(Exception ex, int ntries)
        throws InterruptedException;
  }

  /**
   * Interface for handling error encountered during scheduled pushing of
   * {@code DocId}s.
   */
  public static interface GetDocIdsErrorHandler {
    /**
     * {@link GsaCommunicationHandler#pushDocIds} had a failure reading from
     * this Adaptor's {@link Adaptor#getDocIds}. The thrown exception is
     * provided as well as the number of times that this batch was attempted to
     * be sent. Return true to retry, perhaps after a Thread.sleep() of some
     * time.
     */
    public boolean handleFailedToGetDocIds(Exception ex, int ntries)
        throws InterruptedException;
  }
}
