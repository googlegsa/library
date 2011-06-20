// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.Exception;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author brandoni@google.com (Brandon Iles)
 */
public class TransformPipeline extends AbstractList<DocumentTransform> {

  /**
   * ContentIn and metaDataIn are guaranteed to remain unchanged.
   */
  public void transform(ByteArrayOutputStream contentIn,
                        ByteArrayOutputStream metaDataIn,
                        ByteArrayOutputStream contentOut,
                        ByteArrayOutputStream metaDataOut,
                        Map<String, String> params) throws TransformException, IOException {
    if (transformList.isEmpty()) {
      contentIn.writeTo(contentOut);
      metaDataIn.writeTo(metaDataOut);
      return;
    }

    ByteArrayOutputStream contentInTransit = new ByteArrayOutputStream();
    ByteArrayOutputStream metaInTransit = new ByteArrayOutputStream();
    contentIn.writeTo(contentInTransit);
    metaDataIn.writeTo(metaInTransit);
    for (int i = 0; i < transformList.size(); i++) {
      DocumentTransform transform = transformList.get(i);
      try {
        transform.transform(contentInTransit, metaInTransit, contentOut, metaDataOut, params);
      }
      catch (TransformException e) {
        // TODO: Log error
        if (transform.errorHaltsPipeline()) {
          contentOut.reset();
          metaDataOut.reset();
          throw e;
        }
        else {
          continue;
        }
      }
      if (i < transformList.size() - 1) {
        contentOut.writeTo(contentInTransit);
        metaDataOut.writeTo(metaInTransit);
      }
    }
  }

  @Override
  public void add(int index, DocumentTransform transform) {
    transformList.add(index, transform);
  }

  @Override
  public DocumentTransform get(int index) {
    return transformList.get(index);
  }

  @Override
  public DocumentTransform set(int index, DocumentTransform transform) {
    return transformList.set(index, transform);
  }

  @Override
  public DocumentTransform remove(int index) {
    return transformList.remove(index);
  }

  @Override
  public int size() {
    return transformList.size();
  }

  private ArrayList<DocumentTransform> transformList = new ArrayList<DocumentTransform>();
}
