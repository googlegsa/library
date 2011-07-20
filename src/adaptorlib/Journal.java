package adaptorlib;

import java.util.*;

/**
 * Contains registers and stats regarding runtime.
 */
class Journal {
  Journal() {
  }

  private HashMap<DocId, Integer> timesPushed
      = new HashMap<DocId, Integer>();
  private long totalPushes;

  private HashMap<DocId, Integer> timesGsaRequested
      = new HashMap<DocId, Integer>();
  private long totalGsaRequests;

  private HashMap<DocId, Integer> timesNonGsaRequested
      = new HashMap<DocId, Integer>();
  private long totalNonGsaRequests;

  private Date startedAt = new Date();

  int numUniqueDocIdsPushed() {
    return timesPushed.size(); 
  }

  long numTotalDocIdsPushed() {
    return totalPushes;
  } 

  int numUniqueGsaRequests() {
    return timesGsaRequested.size(); 
  }

  long numTotalGsaRequests() {
    return totalGsaRequests;
  }

  int numUniqueNonGsaRequests() {
    return timesNonGsaRequested.size(); 
  }

  long numTotalNonGsaRequests() {
    return totalNonGsaRequests;
  }

  Date whenStarted() {
    return startedAt;
  }

  synchronized void recordDocIdPush(List<DocId> pushed) {
    for (DocId id : pushed) {
      increment(timesPushed, id);
    }
    totalPushes += pushed.size();
  }

  synchronized void recordGsaContentRequest(DocId docId) {
    increment(timesGsaRequested, docId); 
    totalGsaRequests++;
  }

  synchronized void recordNonGsaContentRequest(DocId requested) {
    increment(timesNonGsaRequested, requested); 
    totalNonGsaRequests++;
  }

  static private void increment(
      HashMap<DocId, Integer> counts, DocId id) {
    if (!counts.containsKey(id)) {
      counts.put(id, 0);
    }
    counts.put(id, 1 + counts.get(id));
  }
}
