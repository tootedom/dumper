- [Dumper](#dumper)
    - [Configuration](#configuration)
    - [License](#license)        	    
	    

# Dumper

Basic java class combined from the following two posts, to allow for the dumping of a .hprof from a running java process:

- https://blog.42.nl/articles/connecting-to-a-jvm-programmatically/
- https://blogs.oracle.com/sundararajan/entry/programmatically_dumping_heap_from_java

This class does what a `jmap -dump:live,format=b,file=/tmp/jvm.hprof` would do, but can work in circumstances where
jmap isn't working for some reason.


## Configuration

An example of running the class is as follows.  This will create a hprof in `/tmp/jvm<uuid>.hrpof`

    su -l userjvmprocess -c "java -classpath /usr/java/default/lib/tools.jar:/tmp/dumpper-0.0.1-SNAPSHOT.jar org.greencheek.jvmtools.HeapDumper <javapid>" -s /bin/bash

To change the file name use the system property `-Ddumpfile=/where/you/want.hprof`.

    su -l userjvmprocess -c "java -Ddumpfile=/tmp/jvm1.hprof -classpath /usr/java/default/lib/tools.jar:/tmp/dumper-0.0.1-SNAPSHOT.jar org.greencheek.jvmtools.HeapDumper 26814" -s /bin/bash

To dump only the live reachable objects in the heap use the system property: `-Ddumpliveonly=true`

    su -l userjvmprocess -c "java -Ddumpfile=/tmp/jvm1.hprof -Ddumpliveonly=true -classpath /usr/java/default/lib/tools.jar:/tmp/dumper-0.0.1-SNAPSHOT.jar org.greencheek.jvmtools.HeapDumper 26814" -s /bin/bash

## License ##

Apache v2, See LICENSE file
