package com.google.enterprise.adaptor;

import static java.util.AbstractMap.SimpleEntry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The content transform factory holds all document content transforms
 * and puts them in series connection.
 *
 * @author Dominik Weidenfeld (dominik.weidenfeld@twt.de)
 */
class ContentTransformFactory {

  private static final Logger log =
      Logger.getLogger(ContentTransformFactory.class.getName());

  /* the method on the ContentTransform that calculates the next ContentType. */
  private static final String METHOD_NAME =
      "getContentTypeOutputForContentTypeInput";
  private List<Map.Entry<Constructor<ContentTransform>, Map<String, String>>>
      transforms;
  private List<Method> contentTypeChain;

  /**
   * Constructs a new ContentTransformFactory. Also checks the basic
   * configuration values for the {@link ContentTransform}
   * instantiation.
   *
   * @param configs Configuration for each {@link ContentTransform}
   * @throws InvalidConfigurationException If the class for a {@link
   *                                       ContentTransform} is missing
   * @throws RuntimeException              If the class does not match all
   *                                       criteria
   */
  public ContentTransformFactory(
      final List<Map<String, String>> configs) {
    if (configs.size() <= 0) {
      return;
    }
    this.transforms = new ArrayList<
        Map.Entry<Constructor<ContentTransform>, Map<String, String>>>();
    contentTypeChain = new ArrayList<Method>();
    for (int i = 0; i < configs.size(); i++) {
      final Map<String, String> config = configs.get(i);
      final String className = config.get("class");
      if (Strings.isNullOrEmpty(className)) {
        throw new InvalidConfigurationException(
            "DocumentContentTransform class key is missing: " + config);
      }
      try {
        @SuppressWarnings("unchecked")
        final Class<ContentTransform> clazz =
            (Class<ContentTransform>) Class.forName(className);
        final Constructor<ContentTransform> constructor =
            clazz.getConstructor(Map.class, Metadata.class,
                String.class, OutputStream.class);
        this.transforms.add(
            new SimpleEntry<Constructor<ContentTransform>, Map<String, String>>(
                constructor, new TreeMap<String, String>(config)));

        Method m = null;
        try {
          m = clazz.getMethod(METHOD_NAME, String.class);
        } catch (NoSuchMethodException e) {
          throw new AssertionError(e);
        }
        if (m == null) {
          throw new AssertionError("Unable to find method " + METHOD_NAME
              + " in class " + className);
        }
        this.contentTypeChain.add(m);
      } catch (Exception e) {
        throw new InvalidConfigurationException(
            "Cannot get document content transform of type: " + className, e);
      }
    }
  }

  /**
   * Creates a new content transform pipeline.
   *
   * @param original    original content stream
   * @param contentType content type
   * @param metadata    metadata
   * @return stream pipeline or original output stream
   */
  public final OutputStream createPipeline(final OutputStream original,
                                           final String firstContentType,
                                           final Metadata metadata) {
    if (null == transforms || transforms.size() <= 0) {
      return original;
    }
    OutputStream currentOutputStream = original;
    int steps = transforms.size();
    for (int count = steps - 1; count >= 0; count--) {
      Map.Entry<Constructor<ContentTransform>, Map<String, String>> t =
          transforms.get(count);
      try {
        currentOutputStream = t.getKey().newInstance(t.getValue(), metadata,
            calculateContentType(firstContentType, count), currentOutputStream);
      } catch (Exception e) {
        throw new RuntimeException(
            "Cannot instantiate document content transform: "
                + t.getKey().getName(), e);
      }
    }
    return currentOutputStream;
  }

  /**
   * Iterates over the chain of potential ContentType changes, returning the
   * calculated ContentType at the end of the chain.
   */
  public String calculateResultingContentType(String initialContentType) {
    return calculateContentType(initialContentType, contentTypeChain.size());
  }

  /**
   * Iterates over the chain of potential ContentType changes, returning the
   * calculated ContentType after "n" successive Content Transformations.
   * If n = 0, this returns the initialContentType passed in.
   * If any of the calls to
   * <code>getContentTypeOutputForContentTypeInput()</code> return a null String
   * it is replaced with the empty String.
   *
   * @throws AssertionError if n < 0 or n > contentTypeChain.size()
   */
  @VisibleForTesting
  String calculateContentType(String initialContentType, int n) {
    if (n < 0) {
      throw new AssertionError("n must be non-negative");
    }
    if (n > contentTypeChain.size()) {
      throw new AssertionError("only " + contentTypeChain.size()
          + " transform(s) present");
    }
    String currentContentType = initialContentType;
    for (int i = 0; i < n; i++) {
      Method m = contentTypeChain.get(i);
      try {
        currentContentType = (String) m.invoke(null, currentContentType);
        if (null == currentContentType) {
          currentContentType = "";
        }
      } catch (Exception ex) {
        throw new RuntimeException("Call to " + METHOD_NAME + " #" + i
            + " failed:", ex);
      }
    }
    return currentContentType;
  }
}
