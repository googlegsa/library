package adaptorlib;

import java.util.*;

/**
 * Contains registers and stats regarding runtime.
 */
class Journal {
  private Journal() {
  }

  private static HashMap<DocId, Integer> timesPushed;
  private static HashMap<DocId, Integer> timesQueried;
  private static HashMap<DocId, Integer> timesGsaCrawled;
  private static Date startedAt;

  static {
    startedAt = new Date();
    timesPushed = new HashMap<DocId, Integer>();
    timesQueried = new HashMap<DocId, Integer>();
  }

  static int numUniqueDocIdsPushed() {
    return timesPushed.size(); 
  }

  static int numUniqueDocContentRequests() {
    return timesQueried.size(); 
  }

  static int numUniqueGsaCrawled() {
    return timesGsaCrawled.size(); 
  }


  static Date whenStarted() {
    return startedAt;
  }

  static void recordDocIdPush(List<DocId> pushed) {
    for (DocId id : pushed) {
      increment(timesPushed, id);
    }
  }

  static void recordDocContentRequest(DocId requested) {
    increment(timesQueried, requested); 
  }

  static void recordGsaCrawl(DocId docId) {
    increment(timesGsaCrawled, docId); 
  }

  static private void increment(HashMap<DocId, Integer> counts, DocId id) {
    if (!counts.containsKey(id)) {
      counts.put(id, 0);
    }
    counts.put(id, 1 + counts.get(id));
  }
}
