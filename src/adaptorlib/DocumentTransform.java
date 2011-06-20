// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Represents an individual transform in the transform pipeline.
 * Subclass this to add your own custom behavior.
 *
 * @author brandoni@google.com (Brandon Iles)
 */
public class DocumentTransform  {

  public DocumentTransform(String name) {
    this.name = name;
  }

  /**
   * Override this function to do the actual data transformation.
   * Read data from the byte arrays, and write them to the OutputStreams.
   * Any changes to the params map will be passed on the subsequent transforms.
   *
   * @throws TransformException
   * @throws IOException
   */
  public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                        ByteArrayOutputStream metaDataIn, OutputStream metaDataOut,
                        Map<String, String> params) throws TransformException, IOException {
    // Defaults to identity transform
    contentIn.writeTo(contentOut);
    metaDataIn.writeTo(metaDataOut);
  }

  public void name(String name) { this.name = name; }
  public String name() { return name; }

  /**
   * If this property is true, a failure of this transform will cause the entire
   * transform pipeline to abort. This is useful in the case where a particular
   * transform is required in order to server data. For example, a transform
   * tasked with redacting or filtering document content.
   *
   * If this is false and a error occurs, this transform is treated as a
   * identity transform.
   */
  public void errorHaltsPipeline(boolean errorHaltsPipeline) {
    this.errorHaltsPipeline = errorHaltsPipeline;
  }

  public boolean errorHaltsPipeline() {
    return errorHaltsPipeline;
  }

  private boolean errorHaltsPipeline = false;
  private String name = "";
}
