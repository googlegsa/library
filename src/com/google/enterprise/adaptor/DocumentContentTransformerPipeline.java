package com.google.enterprise.adaptor;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The document content transformer pipeline holds all document content transformers
 * and puts them in series connection.
 *
 * @author Dominik Weidenfeld (dominik.weidenfeld@twt.de)
 */
public class DocumentContentTransformerPipeline {

  /**
   * Logger.
   */
  private static final Logger LOG =
      Logger.getLogger(DocumentContentTransformerPipeline.class.getName());

  /**
   * Content transformer classes.
   */
  private List<Map<String, String>> contentTransformers;

  /**
   * Constructor.
   */
  public DocumentContentTransformerPipeline() {
    contentTransformers = Lists.newLinkedList();
  }

  /**
   * Add a content transformer.
   *
   * @param contentTransformer content transformer configuration
   */
  public final void addContentTransformer(final Map<String, String> contentTransformer) {
    contentTransformers.add(0, contentTransformer);
  }

  /**
   * Creates a new transformer pipeline.
   *
   * @param original    original content stream
   * @param contentType content type
   * @param metadata    metadata
   * @return stream pipeline or original output stream
   */
  public final OutputStream createPipeline(final OutputStream original,
                                           final String contentType,
                                           final Metadata metadata) {
    if (contentTransformers.size() <= 0) {
      return original;
    }
    DocumentContentTransformer contentTransformer = null;
    for (final Map<String, String> tConfig : contentTransformers) {
      final String className = tConfig.get("class");
      if (Strings.isNullOrEmpty(className)) {
        LOG.log(Level.WARNING, "Document Content Transformer class is missing");
        continue;
      }
      try {
        //noinspection unchecked
        final Class<DocumentContentTransformer> clazz =
            (Class<DocumentContentTransformer>) Class.forName(className);
        final Constructor<DocumentContentTransformer> constructor =
            clazz.getConstructor(Map.class, OutputStream.class, String.class, Metadata.class);
        if (null == contentTransformer) {
          contentTransformer =
              constructor.newInstance(tConfig, original, contentType, metadata);
        } else {
          contentTransformer =
              constructor.newInstance(tConfig, contentTransformer, contentType, metadata);
        }
      } catch (Exception e) {
        LOG.log(Level.WARNING, "Cannot use document content transformer of type {0}", className);
      }
    }
    return contentTransformer;
  }
}
