// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib;

import au.com.bytecode.opencsv.CSVReader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.MappedByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.Map;

/**
 * This transform takes in a CSV file, generates an HTML table with the data,
 * and inserts it into a template HTML file that's provided by the user.
 * In the template HTML file, place <code>&amp;#0;</code> where you'd like the table
 * to be inserted.
 */
public class TableGeneratorTransform extends DocumentTransform {
  private static Logger LOG = Logger.getLogger(TableGeneratorTransform.class.getName());

  public TableGeneratorTransform() {
    super("TableGeneratorTransform");
  }

  public TableGeneratorTransform(String templateFile) {
    super("TableGeneratorTransform");
    try {
      loadTemplateFile(templateFile);
    }
    catch (IOException e) {
      LOG.log(Level.WARNING, "TableGeneratorTransform could not load templateFile: " +
              templateFile, e);
    }
  }

  @Override
  public void transform(ByteArrayOutputStream contentIn, ByteArrayOutputStream metadataIn,
                        OutputStream contentOut, OutputStream metadataOut,
                        Map<String, String> params) throws TransformException, IOException {
    String csv = contentIn.toString();
    List<String[]> records = new CSVReader(new StringReader(csv)).readAll();
    StringBuilder tableBuilder = new StringBuilder();
    if (!records.isEmpty()) {
      tableBuilder.append("<table border=\"1\">\n");
      for (String[] record : records) {
        tableBuilder.append("<tr>\n");
        for (String field : record) {
          tableBuilder.append("<td>");
          tableBuilder.append(field);
          tableBuilder.append("</td>\n");
        }
        tableBuilder.append("</tr>\n");
      }
      tableBuilder.append("</table>");
    }
    String content = htmlTemplate.replace(SIGIL, tableBuilder.toString());
    contentOut.write(content.getBytes());
    metadataIn.writeTo(metadataOut);
  }

  private void loadTemplateFile(String templateFile) throws IOException {
    FileInputStream stream = new FileInputStream(new File(templateFile));
    try {
      FileChannel fc = stream.getChannel();
      MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
      htmlTemplate = Charset.defaultCharset().decode(bb).toString();
    }
    finally {
      stream.close();
    }
  }

  private String htmlTemplate = "<HTML><HEAD></HEAD><BODY>" + SIGIL + "</BODY></HTML>";

  /**
   * This is the placeholder that gets replaced by the generated table. We use
   * the escaped null character, because it is explicitly disallowed in HTML.
   */
  private static final String SIGIL = "&#0;";
}
