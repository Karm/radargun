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
package org.radargun.stages.lifecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.config.TimeConverter;
import org.radargun.stages.DefaultDistStageAck;
import org.radargun.stages.helpers.KillHelper;
import org.radargun.stages.helpers.RoleHelper;
import org.radargun.stages.helpers.StartHelper;

/**
 * The stage start and kills some nodes concurrently (without waiting for each other).
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Stage(doc = "The stage start and kills some nodes concurrently (without waiting for each other).")
public class ParallelStartKillStage extends AbstractStartStage {

   @Property(doc = "Set of slaves which should be killed in this stage. Default is empty.")
   private Collection<Integer> kill = new ArrayList<Integer>();

   @Property(converter = TimeConverter.class, doc = "Delay before the slaves are killed. Default is 0.")
   private long killDelay = 0;

   @Property(doc = "If set to true, the nodes should be shutdown. Default is false = simulate node crash.")
   private boolean tearDown = false;

   @Property(doc = "Set of slaves which should be started in this stage. Default is empty.")
   private Collection<Integer> start = new ArrayList<Integer>();

   @Property(converter = TimeConverter.class, doc = "Delay before the slaves are started. Default is 0.")
   private long startDelay = 0;

   @Property(doc = "Applicable only for cache wrappers with Partitionable feature. Set of slaves that should be" +
         "reachable from the new node. Default is all slaves.")
   private Set<Integer> reachable = null;

   /* Note: having role for start has no sense as the dead nodes cannot have any role in the cluster */
   @Property(doc = "Another way how to specify killed nodes is by role. Available roles are "
         + RoleHelper.SUPPORTED_ROLES + ". By default this is not used.")
   private RoleHelper.Role role;

   @Override
   public DistStageAck executeOnSlave() {
      if (lifecycle == null) {
         log.warn("No lifecycle for service " + slaveState.getServiceName());
         return successfulResponse();
      }
      boolean killMe = kill.contains(slaveState.getSlaveIndex()) || RoleHelper.hasRole(slaveState, role);
      boolean startMe = start.contains(slaveState.getSlaveIndex());
      if (!(killMe || startMe)) {
         log.info("Nothing to kill or start...");
      }
      while (killMe || startMe) {
         if (startMe) {
            if (lifecycle.isRunning()) {
               if (!killMe) {
                  log.info("Wrapper already set on this slave, not starting it again.");
                  startMe = false;
                  return successfulResponse();
               } 
            } else {
               if (startDelay > 0) {
                  try {
                     Thread.sleep(startDelay);
                  } catch (InterruptedException e) {
                     log.error("Starting delay was interrupted.", e);
                  }
               }
               try {
                  StartHelper.start(slaveState, false, null, 0, reachable);
               } catch (RuntimeException e) {
                  return errorResponse("Issues while instantiating/starting cache wrapper", e);
               }
               startMe = false;
            }            
         }
         if (killMe) {
            if (!lifecycle.isRunning()) {
               if (!startMe) {
                  log.info("Wrapper is dead, nothing to kill");
                  killMe = false;
                  return successfulResponse();
               }
            } else {
               try {
                  Thread.sleep(killDelay);
               } catch (InterruptedException e) {
                  log.error("Killing delay was interrupted.", e);
               }
               try {
                  KillHelper.kill(slaveState, tearDown, false);
               } catch (RuntimeException e) {
                  return errorResponse("Failed to kill the service", e);
               }
               killMe = false;
            }
         }
      }
      return successfulResponse();
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks) {
      boolean success = true;
      logDurationInfo(acks);
      for (DistStageAck stageAck : acks) {
         DefaultDistStageAck defaultStageAck = (DefaultDistStageAck) stageAck;
         if (defaultStageAck.isError() && (mayFailOn == null || !mayFailOn.contains(stageAck.getSlaveIndex()))) {
            log.warn("Received error ack " + defaultStageAck);
            return false;
         } else if (defaultStageAck.isError()) {
            log.info("Received allowed error ack " + defaultStageAck);
         } else {
            log.trace("Received success ack " + defaultStageAck);
         }
      }
      if (log.isTraceEnabled())
         log.trace("All ack messages were successful");
      return success;
   }
}
