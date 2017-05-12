package com.gigaspaces.heapprotector;

import java.util.HashMap;
import java.util.Map;

public class EvictionConfig {

  private Double evictionStartThreshold;
  private Double evictionStopThreshold;
  private Integer evictionBatchToSleepTimeFactor;
  private Integer maxEvictionBatchSize;
  private Integer evictionBatchSize;
  private Integer maxEvictionCycle;

  //	private boolean explicit-gc
  private Map<String, String> classInstanceCountThreshold = new HashMap<String, String>();

  public Double getEvictionStartThreshold() {
    return evictionStartThreshold;
  }

  public void setEvictionStartThreshold(Double evictionStartThreshold) {
    this.evictionStartThreshold = evictionStartThreshold;
  }

  public Double getEvictionStopThreshold() {
    return evictionStopThreshold;
  }

  public void setEvictionStopThreshold(Double evictionStopThreshold) {
    this.evictionStopThreshold = evictionStopThreshold;
  }

  public Map<String, String> getClassInstanceCountThreshold() {
    return classInstanceCountThreshold;
  }

  public void setClassInstanceCountThreshold(Map<String, String> classInstanceCountThreshold) {
    this.classInstanceCountThreshold = classInstanceCountThreshold;
  }

  public Integer getEvictionBatchToSleepTimeFactor() {
    return evictionBatchToSleepTimeFactor;
  }

  public void setEvictionBatchToSleepTimeFactor(Integer evictionBatchToSleepTimeFactor) {
    this.evictionBatchToSleepTimeFactor = evictionBatchToSleepTimeFactor;
  }

  public Integer getMaxEvictionBatchSize() {
    return maxEvictionBatchSize;
  }

  public void setMaxEvictionBatchSize(Integer maxEvictionBatchSize) {
    this.maxEvictionBatchSize = maxEvictionBatchSize;
  }

  public Integer getEvictionBatchSize() {
    return evictionBatchSize;
  }

  public void setEvictionBatchSize(Integer evictionBatchSize) {
    this.evictionBatchSize = evictionBatchSize;
  }

  public Integer getMaxEvictionCycle() {
    return maxEvictionCycle;
  }

  public void setMaxEvictionCycle(Integer maxEvictionCycle) {
    this.maxEvictionCycle = maxEvictionCycle;
  }
}
