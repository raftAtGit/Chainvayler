# Chainvayler
## ~Transparent Replication and Persistence for POJO's (Plain Old Java Objects)

* [What is this?](#what-is-this)
* [Introduction](#introduction)
* [Bank sample](#bank-sample)
  * [Overview](#overview)
  * [Running the sample in Kubernetes](#running-the-sample-in-kubernetes)
  * [Running the sample locally](#running-the-sample-locally)
* [How it works?](#how-it-works)
* [Performance](#performance)
* [Determinism](#determinism)
* [Limitations](#limitations)
  * [Garbage collection](#garbage-collection)
  * [Clean shutdown](#clean-shutdown)
* [FAQ and more](#faq-and-more)

## [What is this?](#what-is-this)

This is a _proof of concept_ library which provides [POJOs](http://en.wikipedia.org/wiki/Plain_Old_Java_Object) (Plain Old Java Objects) __replication__ and __persistence__ capabilities 
almost __transparently__.

_Chainvayler_ requires neither implementing special interfaces nor extending from special classes nor a backing relational database. 
Only some _@Annotations_ and conforming a few rules is necessary.

Either _replication_ or _persistence_ can be disabled independently. If _replication_ is disabled, you will have locally persisted POJOs. 
If _persistence_ is disabled, you will have your POJOs replicated over JVMs possibly spreading over multiple nodes. 
If both _replication_ and _persistence_ is disabled, well you will only have _Chainvayler_'s overhead ;)

I've found the idea really promising, so went ahead and made a PoC implementation.

Sounds too good to be true? Well, keep reading... ;)

## [Introduction](#introduction)

As mentioned, _Chainvayler_ only requires some _@Annotations_ and conforming a few rules.

Here is a quick sample:
```
@Chained
class Library {
   final Map<Integer, Book> books = new HashMap<Integer, Book>();
   int lastBookId = 1;
   
   @Modification
   void addBook(Book book) {
      book.setId(lastBookId++);
      books.put(book.getId(), book);
   }
}
```
Quite a _Plain Old Java Object_, isn't it? Run the __Chainvayler compiler__ after __javac__ and then to get a reference to a chained instance:

```
Library library = Chainvayler.create(Library.class);
```
or this variant to configure options:
```
Library library = Chainvayler.create(Library.class, config);
```

Now, add as many books as you want to your library, they will be _automagically_ persisted and replicated to other JVMs. 
Kill your program any time, when you restart it, the previously added books will be in your library.

Note, the call to _Chainvayler.create(..)_ is only required for the _root_ of object graph. All other objects are created in regular ways, 
either with the _new_ oprerator or via factories, builders whatever. As it is, _Chainvayler_ is quite flexible, other objects may be other 
instances of _root_ class, subclasses/superclasses of it, or instances of a completely different class hierarchy.

The only requirement to be _chained_ (persisted/replicated) is to be reachable directly or indirectly from the _root_. 
For sure, there is no point in persisting/replicating an object that should soon be garbage collected and will not be accesible 
in the next JVM session. 

BTW, above is not a _hard_ requirement. Even if your _chained_ object is not reachable from the _root_, it will be persisted/replicated 
but will be garbage collected later on if there are no other references to it. Only And yes, this also means garbage collection works as expected with _Chainvayler_. I had said _almost transparent_, right? ;)

## [Bank sample](#bank-sample)

### [Overview](#bank-sample-overview)

_Chainvayler_ comes with a [_Bank_](bank-sample/src/main/java/raft/chainvayler/samples/bank) sample, for both demonstration and testing purposes.

Below is the class diagram of the _Bank_ sample:
![Class diagram](https://chainvayler-public.s3-us-west-2.amazonaws.com/images/bank-sample-class-diagram.png) 

Nothing much fancy here. Apparently this is a toy diagram for a real banking application, but hopefully good enough to demonstrate _Chainvayler_'s capabilities.

[_Bank_](bank-sample/src/main/java/raft/chainvayler/samples/bank/Bank.java) class is the _root_ class of this object graph. It's used to get a _chained_ instance of the object graph via _Chainvayler_. Every object reachable directly or indirectly from the root _Bank_ object will be _chained_ (persisted/replicated). Notice _Bank_ class has super and sub classes and even has a reference to another _Bank_ object, doesn't matter if it is a subclass or superclass or a _Bank_ class itself.

For the sake of brevity, I've skipped the class methods in the diagram but included a few to demonstrate _Chainvayler_'s capabilities:
  * _Person_ and _RichPerson_ constructors throw an exception if _name_ has a special value
  * _SecretCustomer_ throws an exception whenever _getName_ is called
  * _SecretCustomer_ resides in a different package, I will tell in a bit what it demonstrates
  

### [Running the sample in Kubernetes](#running-the-sample-in-kubernetes)

The easiest way to see _Chainvayler_ in action is to run the _Bank_ sample in _Kubernetes_ via provided _Helm_ charts.

In `Chainvayler/bank-sample` folder, run the following command:
```
helm install kube/chainvayler-bank-sample/ --name chainvayler-sample 
```

This will by default create 3 writer pods and the watcher application `peer-stats` to follow the process. Any writer or reader pods will register themselves to `peer-stats` pod via RMI and you can follow the process via `peer-stats` pod:

```
kubectl logs chainvayler-peer-stats-<ID> --follow
```

The output will be similar to below (will be updated every 5 seconds):
```
created RMI registry
bound PeerManager to registry
-- started | completed | pool size  | tx count   | tx/second | own tx count | own tx/sec | read count     | reads/sec --
------------------------------------------------------------------------------------------------------------------------
registered peer, count: 1
registered peer, count: 2
registered peer, count: 3
-- started | completed | pool size  | tx count   | tx/second | own tx count | own tx/sec | read count     | reads/sec --
   false     false       -1           1            0.05        0              0.00         0                0.00
   false     false       4            1            0.05        0              0.00         0                0.00
   true      false       4            1            40.00       0              0.00         0                0.00
------------------------------------------------------------------------------------------------------------------------
-- started | completed | pool size  | tx count   | tx/second | own tx count | own tx/sec | read count     | reads/sec --
   true      false       15154        19607        3891.25     6418           1273.41      0                0.00
   true      false       15161        19624        3897.52     6427           1276.21      0                0.00
   true      false       15184        19644        3893.76     6782           1344.43      0                0.00
------------------------------------------------------------------------------------------------------------------------
-- started | completed | pool size  | tx count   | tx/second | own tx count | own tx/sec | read count     | reads/sec --
   true      false       39019        45812        4555.33     14908          1482.35      0                0.00
   true      false       39021        45811        4556.50     14922          1484.19      0                0.00
   true      false       39049        45827        4553.56     15986          1588.28      0                0.00
------------------------------------------------------------------------------------------------------------------------
-- started | completed | pool size  | tx count   | tx/second | own tx count | own tx/sec | read count     | reads/sec --
   true      false       69130        77516        5141.67     25268          1675.89      0                0.00
   true      false       69143        77547        5144.76     25216          1672.93      0                0.00
   true      false       69146        77547        5141.35     27053          1793.49      0                0.00
------------------------------------------------------------------------------------------------------------------------
-- started | completed | pool size  | tx count   | tx/second | own tx count | own tx/sec | read count     | reads/sec --
   true      true        84774        94390        5249.50     32033          1787.16      0                0.00
   true      true        84774        94390        5228.20     31971          1770.85      0                0.00
   true      true        84774        94390        5278.17     30385          1812.62      0                0.00
------------------------------------------------------------------------------------------------------------------------
stopped all readers
will compare results..
transaction counts are the same! 94390
--all banks are identical!!
pool sizes are same  84774 == 84774
pool sizes are same  84774 == 84774
all pool classes are the same
this pair looks identical
this pair looks identical
this pair looks identical
-- all pools are identical!!
average tx/second: 5251.96, own tx/second: 1790.21
```

So, what happened (with default values) exactly is:
* We had launched 3 instances of _Bank_ sample where _replication_ is enabled and _persistence_ is disabled
* They all registered themselves to `peer-stats` application to provide some metrics
* They started to make random _write_ operations over their copy of the _Bank_ with 5 threads
* `peer-stats` application collected and printed their metrics every 5 seconds
* After all writers are finished:
  * `peer-stats` got final details of each one's _Bank_ object and also _Chainvayler_'s implemenation details, like transaction count and internal object pool
  * Compared each one of them against any other them
  * Checked if they are __completely identical__
* Printed the final statistics

Welcome to _Chainvayler_ world! You just witnessed a POJO graph is _automagically_ and _~transparently_ replicated!

Feel free to try different settings: more writers, some readers, some writer-readers or more write actions. See the [values.yaml](bank-sample/kube/chainvayler-bank-sample/values.yaml) file for all options.

For example, lets create additional 2 readers:
```
helm install kube/chainvayler-bank-sample/ --name chainvayler-sample --set replication.readerCount=2 --set load.actions=5000
```
Increased the action count so we will have more time until they are completed. Kill any pod any time, when restarted, they will retrieve the initial state and catch the others.

For example, from the logs after they are killed and restarted:
```
requesting initial transactions [2 - 23582]
received all initial transactions [2 - 23582], count: 23581
```

If you enable persistence and mount external disks with the flags `--set persistence.enabled=true --set persistence.mountVolumes=true`, when pods are killed and restarted, logs will be something like:
```
requesting initial transactions [40154 - 69079]
received all initial transactions [40154 - 69079], count: 28926
```
In this case, the first 40153 transactions are loaded from the disk, next 28926 are retrieved from the network.

__Note:__ When replication is enabled, most of the time pods recover successfully after a kill/restart cycle. But still sometimes they cannot properly connect to Hazelcast cluster or some of the initial transactions get lost. Not sure if this is a misconfuguration by myself or Hazelcast is not bullet proof.

__Important__: Don't kill writer pods with `-9` switch before they are completed. This will hang the whole system. See [clean shutdown](#clean-shutdown) for details.

### [Running the sample locally](#bank-sample-run-local)

## [How it works?](#how-it-works)

## [Performance](#performance)

## [Determinism](#determinism)

## [Limitations](#limitations)

### [Garbage collection](#garbage-collection)

When _replication_ is not enabled, garbage collection works as expected. Any _chained_ object created but not accessible from the _root_ object will be garbage collected soon if there are no other references to it. This is achieved by holding references to _chained_ objects via _weak_ references. 

However, this is not possible when _replication_ is enabled. Imagine a _chained_ object is created on a JVM and it's not accessible from the _root_ object, there are only some other local references to it. Those other local references will prevent it to be garbage collected. 

When this _chained_ object is replicated to other JVMs, there won't be any local references to it, and hence nothing will stop it to be garbage collected if it's not accessible from the _root_ object. 

So, unfortunately, looks like, we need to keep a reference to all created _chained_ objects in replication mode and prevent them to be garbage collected. 

### [Clean shutdown](#clean-shutdown)

When _replication_ is enabled, clean shutdown is very important. In particular, if a writer reserves a transaction ID in the network and dies before sending the transaction to the network, the whole network will hang, they will wait indefinitely to receive that missing transaction. 

The _Bank_ sample registers a shutdown hook to the JVM and shutdowns _Chainvayler_ when JVM shutdown is initiated. This works fine for demonstration purposes unless JVM is killed with `-9 (-SIGKILL)` switch or a power outage happens.

But obviously this is not a bullet proof solution. A possible general solution is, if an awaited transaction is not received after some time, assume sending peer died and send the network a `NoOp` transaction with that ID, so the rest of the network can continue operating.

## [FAQ and more](#faq-and-more)
