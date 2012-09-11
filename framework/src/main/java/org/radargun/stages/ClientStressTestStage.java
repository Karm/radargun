/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stages;

import java.util.Map;

import org.radargun.stressors.ClientStressTestStressor;


public class ClientStressTestStage extends WebSessionBenchmarkStage {

   private int initThreads = 1;
   
   private int maxThreads = 10;
   
   private int increment = 1;
   
   @Override   
   protected Map<String, String> doWork() {
      log.info("Starting " + getClass().getSimpleName() + ": " + this);
      ClientStressTestStressor putGetStressor = new ClientStressTestStressor();
      putGetStressor.setNodeIndex(getSlaveIndex());
      putGetStressor.setNumberOfAttributes(getNumberOfAttributes());
      putGetStressor.setNumberOfRequests(getNumberOfRequests());
      putGetStressor.setInitThreads(initThreads);
      putGetStressor.setMaxThreads(maxThreads);
      putGetStressor.setIncrement(increment);
      putGetStressor.setOpsCountStatusLog(getOpsCountStatusLog());
      putGetStressor.setSizeOfAnAttribute(getSizeOfAnAttribute());
      putGetStressor.setWritePercentage(getWritePercentage());
      putGetStressor.setKeyGeneratorClass(getKeyGeneratorClass());
      putGetStressor.setUseTransactions(isUseTransactions());
      putGetStressor.setCommitTransactions(isCommitTransactions());
      putGetStressor.setTransactionSize(getTransactionSize());
      putGetStressor.setDurationMillis(getDurationMillis());
      return putGetStressor.stress(cacheWrapper);
   }

   public int getInitThreads() {
      return initThreads;
   }

   public void setInitThreads(int initThreads) {
      this.initThreads = initThreads;
   }

   public int getMaxThreads() {
      return maxThreads;
   }

   public void setMaxThreads(int maxThreads) {
      this.maxThreads = maxThreads;
   }

   public int getIncrement() {
      return increment;
   }

   public void setIncrement(int increment) {
      this.increment = increment;
   }
}