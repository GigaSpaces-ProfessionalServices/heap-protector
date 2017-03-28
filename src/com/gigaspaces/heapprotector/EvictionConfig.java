package com.gigaspaces.heapprotector;

import java.util.HashMap;
import java.util.Map;

public class EvictionConfig {

	private Double evictionStartThreshold;
	private Double evictionStopThreshold;
	
	private Map<String,String> classInstanceCountThreshold = new HashMap<String,String>();
			
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
}
