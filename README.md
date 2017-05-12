<h1>Custom Eviction using <font color='blue'>heapprotector</font> package</h1>

heapprotector is a library which allows custom eviction mechamism in a PU.

To create the library jar file, simple execute a maven build command:

<pre>
<b>$ mvn install</b>
</pre>

The **heapprotector-1.0.jar** file (present in the **target**) folder needs to be copied to the **lib/optional/pu-common** folder of the **XAP** installation.

<h2>How to use custom eviction mechanism with a space</h2>

The following are snippets from the <b>pu.xml</b> for a space in which <b>custom eviction policy</b> is used.
```xml
	<! ... ... ... ->
    
	<os-core:space id="space" url="/./mySpace">
		<os-core:properties>
			<props>
				<!-- Use ALL IN CACHE -->
				<prop key="space-config.engine.cache_policy">1</prop>
				<prop key="space-config.engine.memory_usage.high_watermark_percentage">97</prop>
				<prop key="space-config.engine.memory_usage.write_only_block_percentage">96</prop>
				<prop key="space-config.engine.memory_usage.write_only_check_percentage">95</prop>
				<prop key="space-config.engine.memory_usage.low_watermark_percentage">94</prop>
			</props>
		</os-core:properties>
	</os-core:space>

	<os-core:giga-space id="gigaSpace" space="space" tx-manager="transactionManager"/>

	<os-core:distributed-tx-manager id="transactionManager" default-timeout="5000" />

	<bean id="ec" class="com.gigaspaces.heapprotector.EvictionConfig">
		<property name="evictionStartThreshold"><value>70</value></property>
		<property name="evictionStopThreshold"><value>66</value></property>
		<property name="evictionBatchToSleepTimeFactor"><value>64</value></property>
		<property name="maxEvictionBatchSize"><value>200000</value></property>
    <property name="maxEvictionCycle"><value>100</value></property>
		<property name="classInstanceCountThreshold">
			<map>
				<entry key="com.gigaspaces.test.heapprotector.domain.Order1" value="1200000,1300000"/>
				<entry key="com.gigaspaces.test.heapprotector.domain.Order2" value="1300000,1400000"/>
				<entry key="com.gigaspaces.test.heapprotector.domain.Order3" value="1000000,1100000"/>
				<entry key="com.gigaspaces.test.heapprotector.domain.Order4" value="900000,1000000"/>
			</map>
		</property>
	</bean>

	<bean id="myEvictor" class="com.gigaspaces.heapprotector.EvictionManager">
		<property name="gs" ref="gigaSpace"></property>
		<property name="tm" ref="transactionManager"></property>
		<property name="ec" ref="ec"></property>
	</bean>
    
    	<! ... ... ... ->

</pre>
```

The bean **ec** contains configuration information pertaining to the custom eviction.

The explanations for the properties used in the definition of the bean **ec** which has the type **
com.gigaspaces.heapprotector.EvictionConfig** are as follows:

| Property | Type | Comment |
| -------- | ---- | ------- |
| evictionStartThreshold | Integer | upper limit (as percentage) of consumption of the heap memory before custom eviction is started |
| evictionStopThreshold | Integer | lower limit (as percentage) of consumption of the heap memory before custom eviction is stopped |
| evictionBatchToSleepTimeFactor | Integer | The cutsom eviction manager helps the garbage collector with it's clean up by introducing a sleep which is influenced by this parameter. The higher this value, the smaller is the sleep time. |
| maxEvictionBatchSize | Integer | maximum size of the number of object insatnces that get evicted by each eviction call |
| maxEvictionCycle | Integer | maximum number of times custom eviction is done on one clearance cycle |
| classInstanceCountThreshold | Map | This contains a map of key-value pairs. Each key represents a <b>space class name</b> for which custom eviction will occur, and the value is a <b>String</b> which contains <i>two comma-separated integers</i> representing the desired lower and upper limits between which the count of that space class instance should remain |

The evictor bean **myEvictor** of type **com.gigaspaces.heapprotector.EvictionManager** (which is responsible for the custom eviction) is defined with the configuration bean passed as one of the properties.






