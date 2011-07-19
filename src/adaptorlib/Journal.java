package adaptorlib;

import java.util.*;

/**
 * Contains registers and stats regarding runtime.
 */
class Journal {
  private Journal() {
  }

  private static HashMap<DocId, Integer> timesPushed
      = new HashMap<DocId, Integer>();
  private static HashMap<DocId, Integer> timesQueried
      = new HashMap<DocId, Integer>();
  private static HashMap<DocId, Integer> timesGsaCrawled
      = new HashMap<DocId, Integer>();
  private static long totalPushes;
  private static long totalQueries;
  private static long totalGsaQueries;
  private static Date startedAt = new Date();

  static int numUniqueDocIdsPushed() {
    return timesPushed.size(); 
  }

  static int numUniqueDocContentRequests() {
    return timesQueried.size(); 
  }

  static int numUniqueGsaCrawled() {
    return timesGsaCrawled.size(); 
  }

  static long numTotalDocIdsPushed() {
    return totalPushes;
  } 

  static long numTotalQueries() {
    return totalQueries;
  }

  static long numTotalGsaQueries() {
    return totalGsaQueries;
  }

  static Date whenStarted() {
    return startedAt;
  }

  static void recordDocIdPush(List<DocId> pushed) {
    for (DocId id : pushed) {
      increment(timesPushed, id);
    }
    totalPushes += pushed.size();
  }

  static void recordDocContentRequest(DocId requested) {
    increment(timesQueried, requested); 
    totalQueries++;
  }

  static void recordGsaCrawl(DocId docId) {
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
