---
---

Benchmark configuration
-----------------------

### Context

One of the most important purposes of RadarGun is to support benchmarking of distributed caches/data grids.  Generally speaking, a benchmark on a distributed cache is performed as follows:

* a number of nodes are started, each living in its own process. A node is an instance of a distributed cache
* RadarGun waits until all these nodes see each other and form a cluster
* once the cluster is formed, RadarGun will warm up the cluster: run a set of operations (read+write) against each node in the cluster. This is in order to allow any JVM compiler optimizations to kick in (reproduce production-like environment)
* after the warm up is finished the actual benchmark is executed. Same benchmark code will be executed against each node within the cluster. Each benchmark stresses each node and records performance data, e.g. average write/read duration
* optionally, the same benchmark might be run again on greater number of nodes. This is useful for observing how performance is affected by the cluster size (i.e. how does the distributed cache scales out)
* optionally, same benchmark might be run on a different cache product, for comparative benchmarks
* at the end, a report is generated comparing the performance of different products, on different cluster sizes. Bellow is an example of such a generated report, comparing write performance between [Infinispan](http://www.jboss.org/infinispan) and [JBossCache](http://www.jboss.org/jbosscache/)

![Graph](https://github.com/downloads/radargun/radargun/Replicated_PUT_for_5minsstartup.png)

### Architecture

In order to be able to benchmark distributed caches, RadarGun is using a client/server architecture, in which the RadarGun control node (*Master*) coordinates multiple *slaves*. Each slave runs as an independent process, that handles one of the nodes of the benchmarked cluster. The Master has the following responsibilities:

* parse the configuration (see *Configuration* section bellow), and based on that give work to slaves
* the unit of work the master gives to slaves is named *stage*. E.g. here are the stages that might constitute an benchmark run:

    * ServiceStartStage - master broadcasts this stage to all slaves. Each slave will start an distributed cache node, and, after it it started acknowledge to the master. Once master has acknowledgment from all slaves it will move to the next stage.
    * ClusterValidationStage - now that all the nodes are started, the master will ask all the slaves to verify that they are part of the cluster. Each slave verifies and acknowledge to the master. Once master has acknowledgment from all slaves it will move to the next stage.
    * BasicOperationsTest, warmup - when the slaves receive this stage, they will execute an set of read and write operations on each node of the cluster. The purpose of this is to to simulate a production environment by allowing the hot spot to kick in.
    * BasicOperationsTest - this is the actual test that will be monitored for performance. 
               
* after broadcasting a stage to all slave, the master will block until all stages acknowledge the termination of it. Only after that the master will move to the next stage. If an error appears at any point (e.g. one of the nodes don't pass ClusterValidation stage) then the benchmark will be skipped.

For a complete reference of the architecture behind RadarGun please refer to the [Design documentation]({{page.path_to_root}}architecture/design_documentation.html)

### Configuration

Here is where you can find the complete example (see `conf/benchmark-dist.xml`). This section will take each individual element and detail it. An important aspect is the fact that the configuration will only be defined on the master, and it basically defines the way in which the master coordinates the slaves.

*Root element*

    <benchmark xmlns=urn:radargun:benchmark:2.1">

During compilation RadarGun generates schema (XSD) with documented properties for all stages in the distributed benchmark. With this you can be sure that the properties in schema are always in sync with source code. Schema files can be located in `schema` directory of RadarGun distribution. 

*Master configuration*

    <master bindAddress=${master.address:127.0.0.1}" port=${master.port:2103}"/>

Master will open its sever socket at that address (host/port) and wait for connections from slaves. "${master.address:127.0.0.1}" syntax can be read: if there is a system property named "master.address" then use that one; otherwise default to "127.0.0.1". This way of specifying values can be used for all XML attributes.

*Cluster configuration*

    <clusters>
        <scale from=2" to=3" inc=1">
            <cluster />
        </scale>
    </clusters>

This section contains definition of clusters, which specify number nodes that the benchmark will run on. In this example, the benchmark will initially run on 2 nodes (from - initial size), and then 3 nodes (to - final size). After each run, the cluster size is incremented with "inc", until the "to" is reached. If scaling is not required we omit `scale` element and use `cluster` element directly.  

*Product configuration*

    <configurations>
        <config name=Infinispan 5.2 - distributed">
            <setup plugin=infinispan52">
                <embedded xmlns=urn:radargun:plugins:infinispan52:2.2" file=dist-sync.xml"/>
            </setup>
        </config>
        <config name=Infinispan 6.0 - distributed">
            <setup plugin=infinispan60">
                <embedded xmlns=urn:radargun:plugins:infinispan60:2.2" file=dist-sync.xml"/>
            </setup>
        </config>
    </configurations>

In this section we specify configurations we want to benchmark - our example uses two products: infinispan52 and infinispan60. The scenario will be run against every (config, cluster size) combo: infinispan52 configured with "dist-sync.xml" file will be run on a cluster of 2, then 3 nodes. Once this is finished, RadarGun will run the next infinispan60 configuration (dist-sync.xml) on 2 and 3 nodes. There's no restriction on the number of configured products or configurations.

For some scenarios you may also want to pass different configuration to different slaves. This can be achieved by configuring `groups`.

    <config name=Infinispan 5.2 - distributed">
        <setup plugin=infinispan52" group=group-lon">
            <embedded xmlns=urn:radargun:plugins:infinispan52:2.2" file=dist-sync-lon.xml"/>
        </setup>
        <setup plugin=infinispan52" group=group-nyc">
            <embedded xmlns=urn:radargun:plugins:infinispan52:2.2" file=dist-sync-nyc.xml"/>
        </setup>
        <setup plugin=infinispan52" group=group-sfo">
            <embedded xmlns=urn:radargun:plugins:infinispan52:2.2" file=dist-sync-sfo.xml"/>
        </setup>
    </config>

This requires groups to be explicitly specified in `clusters` definition

    <clusters>
        <cluster>
            <group name=group-lon" size=4"/>
            <group name=group-nyc" size=4"/>
            <group name=group-sfo" size=4"/>
        </cluster>
    </clusters>

Each stage (see the next section) can be restricted to run only on selected set of groups. For this purpose use `groups` attribute of any stage element.

### Scenario

    <scenario>
        <service-start />
        <jvm-monitor-start />

        <load-data num-entries=5000"/>
        <basic-operations-test test-name=warmup" num-requests=10000" num-threads-per-node=5">
            <key-selector>
                <concurrent-keys total-entries=5000" />
            </key-selector>
        </basic-operations-test>

        <clear-cache />

        <load-data num-entries=10000"/>
            <repeat from=10" to=30" inc=10">
                <basic-operations-test test-name=stress-test" amend-test=true"
                    duration=30s" num-threads-per-node=${repeat.counter}">
                    <key-selector>
                        <concurrent-keys total-entries=10000"/>
                    </key-selector>
                    <statistics>
                        <default>
                            <operation-stats>
                                <default />
                                <histogram />
                            </operation-stats>
                        </default>
                    </statistics>
                </basic-operations-test>
            </repeat>

        <jvm-monitor-stop />
    </scenario>

In this section we define sequence of stages:

* The benchmark workflow is defined by the stages:
    * service-start: this will start the cluster on an appropriate number of nodes (from <= num. nodes <= to). Additionally it verifies that the cluster is formed.
    * jvm-monitor-start: this starts monitoring of memory/cpu usage. For example, use this to examine effects of garbage collection on performance (provided there are unexpected spikes in report).
    * load-data: loads specified number of entries into cache. Use nested cache-selector to define target cache (default cache is used instead), key-generator to define format of keys and value-generator to specify type of values inserted into cache.
    * basic-operations-test: when name of this stage is set to `warmup`, statistics are not stored. Applies to all types of tests (*-test). 
    * [basic-operations-test]({{page.path_to_root}}measuring_performance/stress_test.html): the actual benchmark. Follow the link for a closer description. This stage performs randomly-selected operations against the cache (write/read/remove, ratio is configurable). Configure this stage to run for a specified period of time (`duration`), or define request count to be performed with `num-requests` parameter.
* If you want to repeat a sequence of the stages, you can use the `Repeat` element. See [Repeat](/radargun/radargun/wiki/Repeat) description for details.
* There are many other stages that you can use in your benchmark, and each has several configuration attributes. Here is a [list of stages]({{page.path_to_root}}architecture/stage_list.html) provided in the latest snapshot - however, this may be outdated. When you build RadarGun, XML schema files (XSD) are generated and placed in `target/distribution/RadarGun.x.y.z/schema` folder with up-to-date list of stages and their attributes, including the documentation.

### Report generation

    <reports>
        <reporter type=csv" />
        <reporter type=html" />
        <reporter type=serialized" />
    </reports>

In this last section report generation is configured. Use `csv` reporter if you want to process the results outside of RadarGun. `html` reporter generates graphical output and includes JVM monitoring output, histograms, configuration properties and many more. It is a good practice to define `serialized` reporter in your `reports` section, as this enables you to rerun all the reporters without the need to run the benchmark again (e.g. if something goes wrong during reporting). Recently `perfrepo` reporter has been added, which enables you to store performance results into an external repository for further analysis. Please see README file located in `reporter-perfrepo` folder for configuration example.   

### Running the benchmark

The sequence in which RadarGun should be started is the following:

* start the master. Once started, the master will block until all the slaves connected to it. The number of slaves expected to connect is equal to `to` attribute, from the `scale` element, or `size` attribute of `cluster` element if you don't use scaling.
* start `to/size` (see below) slaves
* after all the slaves are started and connected to the master, the benchmark will start as described in the *Architecture* section
* after the master is started and all the slaves are connected, it is not possible to add/remove/replace slaves
* if one of the slave process fails unexpectedly, then master will shutdown itself and all slaves
* if the master is killed, all slaves shut down. This might take a while, depending on the length of the task the slave was executing at the time

RadarGun comes with a number of scripts that help starting the master and the nodes. They are present in the distribution: 

* see [Building Binaries]({{page.path_to_root}}getting_started/building_binaries.html) for obtaining a distribution and about its content 
* and [Using scripts]({{page.path_to_root}}getting_started/using_the_scripts.html) for information on what each scripts does and how it should be launched 