package com.google.enterprise.adaptor;

import com.google.common.base.Strings;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
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

  private List<DocumentContentTransform> transforms;

  public ContentTransformFactory(
      final List<Map<String, String>> transforms) {
    if (transforms.size() <= 0) {
      return;
    }
    this.transforms = new LinkedList<DocumentContentTransform>();
    for (int i = (transforms.size() - 1); i >= 0; i--) {
      final Map<String, String> tConfig = transforms.get(i);
      final String className = tConfig.get("class");
      if (Strings.isNullOrEmpty(className)) {
        throw new RuntimeException(
            "Document Content Transform class is missing " + className);
      }
      try {
        //noinspection unchecked
        final Class<DocumentContentTransform> clazz =
            (Class<DocumentContentTransform>) Class.forName(className);
        final Constructor<DocumentContentTransform> constructor =
            clazz.getConstructor(Map.class);
        this.transforms.add(constructor.newInstance(tConfig));
      } catch (Exception e) {
        throw new RuntimeException(
            "Cannot use document content transform of type " + className, e);
      }
    }
  }

  /**
   * Creates a new transform pipeline.
   *
   * @param original    original content stream
   * @param contentType content type
   * @param metadata    metadata
   * @return stream pipeline or original output stream
   */
  public final OutputStream createPipeline(final OutputStream original,
                                           final String contentType,
                                           final Metadata metadata) {
    if (null == transforms || transforms.size() <= 0) {
      return original;
    }
    DocumentContentTransform last = null;
    for (final DocumentContentTransform transform : transforms) {
      if (null == last) {
        transform.setOriginalStream(original);
      } else {
        transform.setOriginalStream(last);
      }
      transform.setContentType(contentType);
      transform.setMetadata(metadata);
      last = transform;
    }
    return last;
  }
}
