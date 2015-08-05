package com.google.enterprise.adaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The document content transform can modify the content of a document.
 *
 * @author Dominik Weidenfeld (dominik.weidenfeld@twt.de)
 */
public class DocumentContentTransform extends OutputStream {

  protected final Map<String, String> config;
  protected final Metadata metadata;
  protected final String contentType;
  private OutputStream originalStream;

  /**
   * Constructs a document content transformer.
   *
   * @param config         the configuration for this instance
   *                       (never null, but maybe empty)
   * @param originalStream the original content stream
   * @param contentType    the content type of the stream
   *                       (could possibly be null or empty)
   * @param metadata       the metadata already collected for this document
   *                       (never null, but maybe empty)
   */
  public DocumentContentTransform(final Map<String, String> config,
                                  final OutputStream originalStream, final String contentType,
                                  final Metadata metadata) {
    if (null == originalStream) {
      throw new NullPointerException();
    }
    this.config = Collections.unmodifiableMap(new HashMap<String, String>(config));
    this.originalStream = originalStream;
    this.contentType = contentType;
    this.metadata = metadata;
  }

  @Override
  public void write(int b) throws IOException {
    originalStream.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    originalStream.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    originalStream.write(b, off, len);
  }

  @Override
  public void flush() throws IOException {
    originalStream.flush();
  }

  @Override
  public void close() throws IOException {
    originalStream.close();
  }
}
