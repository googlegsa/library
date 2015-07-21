package com.google.enterprise.adaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

/**
 * The document content transformer can modify the content of a document.
 *
 * @author Dominik Weidenfeld (dominik.weidenfeld@twt.de)
 */
public class DocumentContentTransformer extends OutputStream {

  protected final Map<String, String> config;
  protected final Metadata metadata;
  protected final String contentType;
  private OutputStream originalStream;

  public DocumentContentTransformer(final Map<String, String> config,
                                    final OutputStream originalStream, final String contentType,
                                    final Metadata metadata) {
    // config and metadata cannot be null
    // contentType possibly be null, but it is allowed
    if (null == originalStream) {
      throw new NullPointerException();
    }
    this.config = Collections.unmodifiableMap(config);
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
