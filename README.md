# Chainvayler
## ~Transparent Replication and Persistence for POJO's (Plain Old Java Objects)

* [What is this?](#what-is-this)
* [Introduction](#introduction)
* [Bank sample](#bank-sample)
  * [Overview](#bank-sample-overview)
  * [Running the sample in Kubernetes](#bank-sample-run-kubernetes)
  * [Running the sample locally](#bank-sample-run-local)
* [How it works?](#how-it-works)
* [Determinism](#determinism)
* [Limitations](#limitations)
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
but will be garbage collected later on if there are no other references to it. And yes, this also means garbage collection works as expected 
with _Chainvayler_. I had said _almost transparent_, right? ;)

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
  

### [Running the sample in Kubernetes](#bank-sample-run-kubernetes)
### [Running the sample locally](#bank-sample-run-local)

## [How it works?](#how-it-works)

## [Determinism](#determinism)

## [Limitations](#limitations)

## [FAQ and more](#faq-and-more)
