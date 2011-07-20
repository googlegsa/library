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
  private HashMap<DocId, Integer> timesQueried
      = new HashMap<DocId, Integer>();
  private HashMap<DocId, Integer> timesGsaCrawled
      = new HashMap<DocId, Integer>();
  private long totalPushes;
  private long totalQueries;
  private long totalGsaQueries;
  private Date startedAt = new Date();

  int numUniqueDocIdsPushed() {
    return timesPushed.size(); 
  }

  int numUniqueDocContentRequests() {
    return timesQueried.size(); 
  }

  int numUniqueGsaCrawled() {
    return timesGsaCrawled.size(); 
  }

  long numTotalDocIdsPushed() {
    return totalPushes;
  } 

  long numTotalQueries() {
    return totalQueries;
  }

  long numTotalGsaQueries() {
    return totalGsaQueries;
  }

  Date whenStarted() {
    return startedAt;
  }

  void recordDocIdPush(List<DocId> pushed) {
    for (DocId id : pushed) {
      increment(timesPushed, id);
    }
    totalPushes += pushed.size();
  }

  void recordDocContentRequest(DocId requested) {
    increment(timesQueried, requested); 
    totalQueries++;
  }

  void recordGsaCrawl(DocId docId) {
    increment(timesGsaCrawled, docId); 
    totalGsaQueries++;
  }

  static private void increment(HashMap<DocId, Integer> counts, DocId id) {
    if (!counts.containsKey(id)) {
      counts.put(id, 0);
    }
    counts.put(id, 1 + counts.get(id));
  }
}
