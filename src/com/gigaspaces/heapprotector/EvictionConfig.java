package com.gigaspaces.heapprotector;

public class EvictionConfig {

	private Double evictionStartThreshold;
	private Double evictionStopThreshold;

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

}
