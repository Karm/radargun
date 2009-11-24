package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;
import org.cachebench.DistStageAck;

import java.util.Arrays;
import java.util.List;
import java.util.Collections;

/**
 * Distributed stage that would validate that cluster is correctly formed.
 * <pre>
 * Algorithm:
 * - each slave does a put(slaveIndex);
 * - each slave checks weather all (or part) of the remaining slaves replicated here.
 * <p/>
 * Config:
 *   - 'isPartialReplication' : is set to true, then the slave will consider that the cluster is formed when one slave
 *      replicated here. If false (default value) then replication will only be considered successful if all
 * (clusterSize)
 *      slaves replicated here.
 * </pre>
 *
 * TODO - as per Bela, the merge happens between 10 and 30 seconds. configure by default to wait 10 times more, with
 * longer delays between sleeps 
 *
 * @author Mircea.Markus@jboss.com
 */
public class ClusterValidationStage extends AbstractDistStage {

   private static Log log = LogFactory.getLog(ClusterValidationStage.class);


   private static final int REPLICATION_TRY_COUNT = 17;
   private static final int REPLICATION_TRY_SLEEP = 2000;

   private static final String PREFIX = "_InstallBenchmarkStage_";


   private boolean isPartialReplication = false;
   private CacheWrapper wrapper;

   public DistStageAck executeOnSlave() {
      DefaultDistStageAck response = newDefaultStageAck();
      try {
         wrapper = slaveState.getCacheWrapper();
         tryToPut();
         int replResult = checkReplicationSeveralTimes();
         if (!isPartialReplication) {
            if (replResult > 0) {//only executes this on the slaves on which replication happened.
               int index = confirmReplication();
               if (index >= 0) {
                  response.setError(true);
                  response.setErrorMessage("Slave with index" + index + " hasn't confirmed the replication");
                  return response;
               }
            }
         } else {
            log.info("Using partial replication, skiping conirm phase");
         }
         response.setPayload(replResult);
      } catch (Exception e) {
         response.setError(true);
         response.setRemoteException(e);
         return response;
      }
      return response;
   }

   private int confirmReplication() throws Exception {
      wrapper.put(Collections.EMPTY_LIST, PREFIX + "_confirms_" + getSlaveIndex(), "true");
      for (int i = 0; i < getActiveSlaveCount(); i++) {
         for (int j = 0; j < 10 && (wrapper.get(Collections.EMPTY_LIST, PREFIX + "_confirms_" + i) == null); j++) {
            tryToPut();
            wrapper.put(Collections.EMPTY_LIST, PREFIX + "_confirms_" + getSlaveIndex(), "true");
            Thread.sleep(1000);
         }
         if (wrapper.get(Collections.EMPTY_LIST, PREFIX + "_confirms_" + i) == null) {
            log.warn("Confirm phase unsuccessful. Slave " + i + " hasn't acknowleged the test");
            return i;
         }
      }
      log.info("Confirm phase successful.");
      return -1;
   }

   public boolean processAckOnMaster(List<DistStageAck> acks) {
      logDurationInfo(acks);
      boolean success = true;
      for (DistStageAck ack : acks) {
         DefaultDistStageAck defaultStageAck = (DefaultDistStageAck) ack;
         if (defaultStageAck.isError()) {
            log.warn("Ack error from remote slave: " + defaultStageAck, defaultStageAck.getRemoteException());
            return false;
         }
         int replCount = (Integer) defaultStageAck.getPayload();
         if (isPartialReplication) {
            if (!(replCount > 0)) {
               log.warn("Replication hasn't occured on slave: " + defaultStageAck);
               success = false;
            }
         } else { //total replication expected
            int expectedRepl = getActiveSlaveCount() - 1;
            if (!(replCount == expectedRepl)) {
               log.warn("On slave " + ack + " total repl hasn't occured. Expected " + expectedRepl + " and received " + replCount);
               success = false;
            }
         }
      }
      if (success) {
         log.info("Cluster successfully formed!");
      } else {
         log.warn("Cluster hasn't formed!");
      }
      return success;
   }


   private void tryToPut() throws Exception {
      int tryCount = 0;
      while (tryCount < 5) {
         try {
            wrapper.put(Arrays.asList(PREFIX, "" + getSlaveIndex()), PREFIX + getSlaveIndex(), "true");
            return;
         }
         catch (Throwable e) {
            log.warn("Error while trying to put data: ", e);
            tryCount++;
         }
      }
      throw new Exception("Couldn't accomplish additiona before replication!");
   }


   private int checkReplicationSeveralTimes() throws Exception {
      int replCount = 0;
      for (int i = 0; i < REPLICATION_TRY_COUNT; i++) {
         replCount = replicationCount();
         if ((isPartialReplication && replCount >= 1) || (!isPartialReplication && (replCount == getActiveSlaveCount() - 1))) {
            log.info("Replication test successfully passed. isPartialReplication? " + isPartialReplication + ", replicationCount = " + replCount);
            return replCount;
         }
         log.info("Replication test failed, " + (i + 1) + " tries so far. Sleeping for  " + REPLICATION_TRY_SLEEP
               + " millis then try again");
         Thread.sleep(REPLICATION_TRY_SLEEP);
      }
      log.info("Replication test failed. Last replication count is " + replCount);
      return -1;
   }

   private int replicationCount() throws Exception {
      int clusterSize = getActiveSlaveCount();
      int replicaCount = 0;
      for (int i = 0; i < clusterSize; i++) {
         int currentSlaveIndex = getSlaveIndex();
         if (i == currentSlaveIndex) {
            continue;
         }
         Object data = tryGet(i);
         if (data == null || !"true".equals(data)) {
            log.trace("Cache with index " + i + " did *NOT* replicate");
         } else {
            log.trace("Cache with index " + i + " replicated here ");
            replicaCount++;
         }
      }
      log.info("Number of caches that replicated here is " + replicaCount);
      return replicaCount;
   }


   private Object tryGet(int i) throws Exception {
      int tryCont = 0;
      while (tryCont < 5) {
         try {
            return wrapper.getReplicatedData(Arrays.asList(PREFIX, "" + i), PREFIX + i);
         }
         catch (Throwable e) {
            tryCont++;
         }
      }
      return null;
   }

   public void setPartialReplication(boolean partialReplication) {
      isPartialReplication = partialReplication;
   }


   @Override
   public String toString() {
      return "ClusterValidationStage{" +
            "isPartialReplication=" + isPartialReplication +
            ", wrapper=" + wrapper +
            "} " + super.toString();
   }
}