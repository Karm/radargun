package org.radargun.sysmonitor;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Mircea Markus <mircea.markus@gmail.com>
 */
public abstract class AbstractActivityMonitor implements Runnable, Serializable {

   static final ObjectName OS_NAME = getOSName();

   static final String PROCESSING_CAPACITY_ATTR = "ProcessingCapacity";
   static final String PROCESS_UP_TIME = "Uptime";
   static final ObjectName RUNTIME_NAME = getRuntimeName();
   static final NumberFormat PERCENT_FORMATTER = NumberFormat.getPercentInstance();

   protected final List<BigDecimal> measurements = new ArrayList<BigDecimal>();

   private static ObjectName getRuntimeName() {
      try {
         return new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);
      } catch (MalformedObjectNameException ex) {
         throw new RuntimeException(ex);
      }
   }

   private static ObjectName getOSName() {
      try {
         return new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
      } catch (MalformedObjectNameException ex) {
         throw new RuntimeException(ex);
      }
   }

   public static long getCpuMultiplier(MBeanServerConnection con) throws Exception {
      Number num;
      try {
         num = (Number) con.getAttribute(OS_NAME, PROCESSING_CAPACITY_ATTR);
      } catch (AttributeNotFoundException e) {
         num = 1;
      }
      return num.longValue();
   }

   public static String formatPercent(double value) {
      return PERCENT_FORMATTER.format(value / 100);
   }

   public final void addMeasurement(BigDecimal value) {
      measurements.add(value);
   }

   protected void addMeasurementAsPercentage(long v) {
      addMeasurement(new BigDecimal(v).divide(new BigDecimal(10)));
   }

   public LinkedHashMap<Integer, BigDecimal> formatForGraph(int interval, int xCount) {
      int x = 0;
      LinkedHashMap<Integer, BigDecimal> map = new LinkedHashMap<Integer, BigDecimal>(measurements.size());
      for (BigDecimal y : measurements) {
         map.put(x, y);
         x += interval;
      }
      
      //now calibrate...
      x -= interval; //this is max X
      if (x <= xCount || map.isEmpty()) {
        return map;
      }

      int stepSize = x / xCount;

      LinkedHashMap<Integer, BigDecimal> calibratedResult = new LinkedHashMap<Integer, BigDecimal>(xCount + 1);
      Iterator<Map.Entry<Integer,BigDecimal>> it = map.entrySet().iterator();
      Map.Entry<Integer, BigDecimal> current = it.next();

      done:
      for (int i = 0; i <= xCount; i++) {
         BigDecimal total = new BigDecimal(0);
         int count = 0;
         int thisX = i * stepSize;
         int thisStepMax = (i+1) * stepSize;
         while (current.getKey() < thisStepMax) {
            total = total.add(current.getValue());
            count++;
            if (!it.hasNext()) {
               if (count != 0) {
                  calibratedResult.put(thisX, total.divide(new BigDecimal(count), RoundingMode.HALF_EVEN));
               }
               break done;
            } else {
              current = it.next();
            }
         }
         if (count == 0) throw new IllegalStateException("Cannot happen as xCount < x!");

         calibratedResult.put(thisX, total.divide(new BigDecimal(count), RoundingMode.HALF_EVEN));
      }

      return calibratedResult;
   }

   public Integer getMeasurementCount() {
      return measurements.size();
   }
}
