package com.google.enterprise.adaptor;

import com.google.common.base.Strings;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The content transform factory holds all document content transformers
 * and puts them in series connection.
 *
 * @author Dominik Weidenfeld (dominik.weidenfeld@twt.de)
 */
public class ContentTransformFactory {

  private static final Logger LOG =
      Logger.getLogger(ContentTransformFactory.class.getName());

  private List<Map<String, String>> contentTransformers;

  public ContentTransformFactory(
      final List<Map<String, String>> contentTransformers) {
    this.contentTransformers = contentTransformers;
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
        LOG.log(Level.SEVERE,
            "Document Content Transformer class is missing {0}", className);
        throw new RuntimeException(
            "Document Content Transformer class is missing " + className);
      }
      try {
        //noinspection unchecked
        final Class<DocumentContentTransformer> clazz =
            (Class<DocumentContentTransformer>) Class.forName(className);
        final Constructor<DocumentContentTransformer> constructor =
            clazz.getConstructor(Map.class,
                OutputStream.class, String.class, Metadata.class);
        if (null == contentTransformer) {
          contentTransformer =
              constructor.newInstance(tConfig, original, contentType,
                  metadata);
        } else {
          contentTransformer =
              constructor.newInstance(tConfig, contentTransformer,
                  contentType, metadata);
        }
      } catch (Exception e) {
        LOG.log(Level.SEVERE,
            "Cannot use document content transformer of type {0}", className);
        throw new RuntimeException(
            "Cannot use document content transformer of type " + className);
      }
    }
    return contentTransformer;
  }
}
