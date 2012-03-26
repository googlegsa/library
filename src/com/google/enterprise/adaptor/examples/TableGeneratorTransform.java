// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor.examples;

import com.google.enterprise.adaptor.AbstractDocumentTransform;
import com.google.enterprise.adaptor.Metadata;
import com.google.enterprise.adaptor.TransformException;

import au.com.bytecode.opencsv.CSVReader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * This transform takes in a CSV file, generates an HTML table with the data,
 * and inserts it into a template HTML file that's provided by the user.
 * In the template HTML file, place <code>&amp;#0;</code> where you'd like the table
 * to be inserted.
 */
public class TableGeneratorTransform extends AbstractDocumentTransform {
  private static final Logger log = Logger.getLogger(TableGeneratorTransform.class.getName());

  public TableGeneratorTransform() {}

  public TableGeneratorTransform(String templateFile) throws IOException {
    loadTemplateFile(templateFile);
  }

  @Override
  public void transform(ByteArrayOutputStream contentIn, OutputStream contentOut,
                        Metadata metadata, Map<String, String> params)
      throws TransformException, IOException {
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
  }

  private void loadTemplateFile(String templateFile) throws IOException {
    FileInputStream stream = new FileInputStream(new File(templateFile));
    try {
      FileChannel fc = stream.getChannel();
      MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
      htmlTemplate = Charset.defaultCharset().decode(bb).toString();
    } finally {
      stream.close();
    }
  }

  private String htmlTemplate = "<HTML><HEAD></HEAD><BODY>" + SIGIL + "</BODY></HTML>";

  /**
   * This is the placeholder that gets replaced by the generated table. We use
   * the escaped null character, because it is explicitly disallowed in HTML.
   */
  private static final String SIGIL = "&#0;";

  public static TableGeneratorTransform create(Map<String, String> config)
      throws IOException {
    String templateFile = config.get("templateFile");
    TableGeneratorTransform transform;
    if (templateFile == null) {
      transform = new TableGeneratorTransform();
    } else {
      transform = new TableGeneratorTransform(templateFile);
    }
    transform.configure(config);
    return transform;
  }
}
