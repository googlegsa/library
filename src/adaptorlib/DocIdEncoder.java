package adaptorlib;
import java.net.URI;

interface DocIdEncoder {
  URI encodeDocId(DocId docId);
}
