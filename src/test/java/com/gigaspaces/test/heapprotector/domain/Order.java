package com.gigaspaces.test.heapprotector.domain;

import com.gigaspaces.annotation.pojo.SpaceClass;
import com.gigaspaces.annotation.pojo.SpaceId;
import com.gigaspaces.annotation.pojo.SpaceIndex;
import com.gigaspaces.metadata.index.SpaceIndexType;

/**
 * A simple Order object. Important properties include the
 * id of the object, a type (used to perform routing when working with
 * partitioned space), the raw Order data and processed Order data, and a
 * boolean flag indicating if this Order object was processed or not.
 */
@SpaceClass
public class Order {

	private Integer id;

	private OrderType type;

	private String rawData;

	private String data;

	private Boolean processed;

	private Long orderTime;

	@SpaceIndex(type = SpaceIndexType.EXTENDED)
	public Long getOrderTime() {
		return orderTime;
	}

	public void setOrderTime(Long orderTime) {
		this.orderTime = orderTime;
	}

	public Boolean getProcessed() {
		return processed;
	}

	/**
	 * Constructs a new Order object.
	 */
	public Order() {

	}

	/**
	 * Constructs a new Order object with the given type and raw Order data.
	 */
	public Order(OrderType type, String rawData) {
		this.type = type;
		this.rawData = rawData;
		this.processed = false;
	}

	/**
	 * The id of this object.
	 */
	@SpaceId(autoGenerate = false)
	public Integer getId() {
		return id;
	}

	/**
	 * The id of this object. Its value will be auto generated when it is
	 * written to the space.
	 */
	public void setId(Integer id) {
		this.id = id;
	}

	/**
	 * The type of the Order object. Used as the routing field when working with
	 * a partitioned space.
	 */
	@SpaceIndex(type = SpaceIndexType.BASIC)
	public OrderType getType() {
		return type;
	}

	/**
	 * The type of the Order object. Used as the routing field when working with
	 * a partitioned space.
	 */
	public void setType(OrderType type) {
		this.type = type;
	}

	/**
	 * The raw Order data this object holds.
	 */
	public String getRawData() {
		return rawData;
	}

	/**
	 * The raw Order data this object holds.
	 */
	public void setRawData(String rawData) {
		this.rawData = rawData;
	}

	/**
	 * The processed Order data this object holds.
	 */
	public String getData() {
		return data;
	}

	/**
	 * The processed Order data this object holds.
	 */
	public void setData(String data) {
		this.data = data;
	}

	/**
	 * A boolean flag indicating if the Order object was processed or not.
	 */
	public Boolean isProcessed() {
		return processed;
	}

	/**
	 * A boolean flag indicating if the Order object was processed or not.
	 */
	public void setProcessed(Boolean processed) {
		this.processed = processed;
	}

	public String toString() {
		return "id[" + id + "] type[" + type + "] rawData[" + rawData
				+ "] data[" + data + "] processed[" + processed
				+ "] orderTime [" + orderTime + "]";
	}
}
