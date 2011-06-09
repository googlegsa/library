/** A DeletedDocId is a DocId that when sent to GSA
  results in quickly removing referenced document
  from crawling and index.
  <p> Please note that GSA will figure out a document
  is deleted on its own and sending a DeletedDocId is
  optional.  Sending the GSA DeletedDocId
  instances will be faster than waiting for GSA to
  realize a document has been deleted.
  <p> Look at DocId for more details. */
class DeletedDocId extends DocId {
  DeletedDocId(String id) {
    super(id, DocReadPermissions.USE_HEAD_REQUEST);
  }

  /** Provides delete for action attribute value. */
  String getFeedFileAction() {
    return "delete";
  } 

  /** "DeletedDocId(" + getUniqueId() + ")" */
  public String toString() {
    return "DeletedDocId(" + getUniqueId() + ")";
  }
}
