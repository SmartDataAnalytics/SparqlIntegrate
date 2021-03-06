# SPARQL Integrate examples


SPARQL Integrate is a set of function extensions for Jena plus interfaces, including a command line runner.
When installing the debian package of the command line runner, you can use a conventional hash bang to make sparql integrate files self-executable: 

```
hashbang.sparql:
-----------------------------
#!/usr/bin/sparql-integrate


SELECT 1 { }
-----------------------------
```

```
chmod +x hashbang.sparql
./hashbang.sparql
```

Output:
```
------
| .0 |
======
| 1  |
------
```

Of course, you can always run files using
```
sparql-integrate file1.sparql ... filen.sparql
```

## Retrieving remote content
A simple but effective mechanism for unified retrieval of local or remote data is provided by the `url:text` function and property function.

```
SELECT * {
  <example-data/data.csv> url:text ?str
}
```

```
SELECT * {
  # TODO: Insert url to data.csv via a rawgit-like service
  <https://cdn.jsdelivr.net/gh/...> url:text ?str
}
```


```
--------------------------------
| str                          |
================================
| "\"a\",\"b\"\n\"c\",\"d\"\n" |
--------------------------------
```

## Processing JSON
This section explains the supported features for working with JSON literals in SPARQL. For simplicity, we hijacked the `xsd` namespace and
added a `xsd:json` datatype.

* Creating a JSON literal
```
SELECT * {
  BIND('{"hello": "world"}'^^xsd:json AS ?s)
}
```

Alternatively, in case you need to create a JSON literal from a variable, you can use the standard
SPARQL function *STRDT*:
```
SELECT ?s {
  BIND('{"hello": "world"}' AS ?str)
  BIND(STRDT(?str, xsd:json) AS ?s)
}
```

In both cases the output is:
```
---------------------------------------------------------------------
| s                                                                 |
=====================================================================
| "{\"hello\": \"world\"}"^^<http://www.w3.org/2001/XMLSchema#json> |
---------------------------------------------------------------------
```

* Extracting a JSON attribute
The main function for extracting attributes from a JSON object is `json:path`:
```
SELECT ?s {
  BIND('{"hello": "world"}'^^xsd:json AS ?str)
  BIND(json:path(?str, "$.hello") AS ?s)
}
```
Of course, inlining is possible
```
SELECT ?s {
  BIND(json:path('{"hello": "world"}'^^xsd:json, "$.hello") AS ?s)
}
```

Again, in both cases the output is:
```
-----------
| s       |
===========
| "world" |
-----------
```

* Unnesting JSON arrays
JSON arrays can be tranformed into result sets using the `json:unnest` function.

```
SELECT * {
  '[true, 1, "hi", {}, []]'^^xsd:json json:unnest (?item ?index)
}
```

This operation transform literal values (boolean, string and numeric) into corresponding standard RDF literals, whereas JSON objects (arrays *are* also JSON objects) remain literals of type `xsd:json`.

**Note: In a future version we also intend to allow `?json json:unnest ?item` in cases where the index is not needed, but this is not supported yet.**
 
```
------------------------------------------------------------
| item                                             | index |
============================================================
| true                                             | 0     |
| "1.0"^^<http://www.w3.org/2001/XMLSchema#double> | 1     |
| "hi"                                             | 2     |
| "{}"^^<http://www.w3.org/2001/XMLSchema#json>    | 3     |
| "[]"^^<http://www.w3.org/2001/XMLSchema#json>    | 4     |
------------------------------------------------------------
```

One can also specify a specific index:
```
SELECT * {
  '[true, 1, "hi", {}, []]'^^xsd:json json:unnest (?item 1)
}

```

```
--------
| item |
========
| "hi" |
--------
```

* Converting JSON objects to JSON arrays
The `json:entries` function converts a JSON object to a JSON array of corresponding items.
Each item is a JSON object having a `key` and a `value` attribute:
```
SELECT ?index ?entry {
  BIND('{"k1": "v1", "k2": "v2"}'^^xsd:json AS ?obj)
  BIND(json:entries(?obj) AS ?arr)
  ?arr json:unnest (?entry ?index)
}
```

```
----------------------------------------------------------------------------------------
| index | entry                                                                        |
========================================================================================
| 0     | "{\"key\":\"k1\",\"value\":\"v1\"}"^^<http://www.w3.org/2001/XMLSchema#json> |
| 1     | "{\"key\":\"k2\",\"value\":\"v2\"}"^^<http://www.w3.org/2001/XMLSchema#json> |
----------------------------------------------------------------------------------------
```

* Zipping JSON arrays
Here we show a more sophicisticated example, that combines several techniques:

* Multiple dependent SPARQL statements: The SELECT query refers to the inserted data
* Construction of IRIs

```
# Insert an example resource with a JSON literal
INSERT DATA {
  eg:workload1 eg:workload """{
    "ids": [ "id1", "id2"],
    "names": [ "name1", "name2", ]
  }"""^^xsd:json
}


# Combine id and name
SELECT ?s ?name
WHERE {
  ?x eg:workload ?o  .
  BIND(json:path(?o, "$.ids") AS ?ids)
  BIND(json:path(?o, "$.names") AS ?names)

  ?ids json:unnest (?id ?i) .
  ?names json:unnest (?name ?i) .

  BIND("http://www.example.org/" AS ?ns)
  BIND(IRI(CONCAT(?ns, ENCODE_FOR_URI(?id))) AS ?s)
}
```


```
------------------------------------------
| s                            | name    |
==========================================
| <http://www.example.org/id1> | "name1" |
| <http://www.example.org/id2> | "name2" |
------------------------------------------
```

* Converting RDF terms to JSON Primitives
Non-json RDF terms can be converted to json using`json:convert(?rdfTerm)`.
The conversion mechanism relies on the `gson` library and the Java object that backs an RDF term.
Several examples are shown below.
Note, that in some cases, such as xsd:date, this approach can expose Java internals.


```sparql
SELECT ?before ?after {
  # COALESCE() without arguments yields null
  { BIND(COALESCE() AS ?before) } UNION
  { BIND(true AS ?before) } UNION
  { BIND(1 AS ?before) } UNION
  { BIND(3.14 AS ?before) } UNION
  { BIND("string" AS ?before) } UNION
  { BIND(<urn:foobar> AS ?before) } UNION
  { BIND('{"key": "value"}'^^xsd:json AS ?before) } UNION
  { BIND('2021-06-21'^^xsd:date AS ?before) } UNION
  { BIND(str('2021-06-21'^^xsd:date) AS ?before) }  BIND(json:convert(?before) AS ?after)
}
```

```
------------------------------------------------------------------------------------------------------
| before                           | after                                                           |
======================================================================================================
|                                  |                                                                 |
| true                             | "true"^^xsd:json                                                |
| 1                                | "1"^^xsd:json                                                   |
| 3.34                             | "3.34"^^xsd:json                                                |
| "string"                         | "\"string\""^^xsd:json                                          |
| <urn:foobar>                     | "\"urn:foobar\""^^xsd:json                                      |
| "{\"key\": \"value\"}"^^xsd:json | "{\"key\":\"value\"}"^^xsd:json                                 |
| "2021-06-21"^^xsd:date           | "{\"mask\":7,\"data\":[2021,6,21,0,0,0,0,0,0], ... }"^^xsd:json |
| "2021-06-21"                     | "\"2021-06-21\""^^xsd:json                                      |
------------------------------------------------------------------------------------------------------

```


* Creating JSON Objects
`json:object` takes a variable number of arguments which are interpreted as a sequence of key-value pairs.
Hence, the number of arguments must be even.
The key must always be a string, whereas the values are converted to json elements using `json:toJson`.
A type error with any of the arguments causes the object construction to raise a type error.


```sparql
SELECT ?jsonObject {
  BIND(1 AS ?value1)
  BIND('key2' AS ?key2)
  BIND(json:object('key1', ?value1, ?key2, 'value2') AS ?jsonObject)
}

```

```
-------------------------------------------------------------------------------
| json                                                                        |
===============================================================================
| "{\"key1\":1,\"key2\":\"value2\"}"^^<http://www.w3.org/2001/XMLSchema#json> |
-------------------------------------------------------------------------------
```


* Creating a JSON Array
Arrays can be constructed from a variable number of arguments that become the items.
Non-json objects are implicitly converted to json ones using `json:toJson`.
A type error with any of the arguments causes the array construction to raise a type error.

```sparql
SELECT ?json {
  BIND(true AS ?item1)
  BIND('item2' AS ?item2)
  BIND(json:array(?item1, ?item2, 3, json:object('item', 4)) AS ?json)
}
```

```
------------------------------------------------------------------------------
| jsonArray                                                                  |
==============================================================================
| "[true,\"item2\",3,{\"item\":4}]"^^<http://www.w3.org/2001/XMLSchema#json> |
------------------------------------------------------------------------------
```

* Calling JavaScript Functions
The function `json:js` takes a Javascript function definition as the first argument, followed by a variable number of
arguments that are passed to that function. Arguments are converted to JSON using the RDF-term-to-JSON conversion approach mentioned above.

**JavaScript's lambda syntax using the arrow operator (=>) does not yet work for function definitions**

```sparql
SELECT * {
  BIND(json:js("function(x) { return x.join('-'); }", "[1, 2, 3]"^^xsd:json) AS ?result)
}
```

```
-----------
| result  |
===========
| "1-2-3" |
-----------
```


## Processing CSV
In general, the `csv:parse` property function is used to make CSV data available in a SPARQL query with each CSV row becoming an entry in the result set. The syntax is `?s csv:parse(?rowJson "optionsString)"`.
The options string is composed of a base CSV format name followed by an optional list of modifiers.

If the subject is a string literal, it is interpreted as CSV data.
If the argument is an IRI, `sparql-integrate` will attempt to resolve the IRI to an input stream to the data. If no further joins are used, this is a **streaming** operation that works with arbitrary large CSV files.

**NOTE ?rowJson must be variable**


* Using the first row as headers

The `-h` option causes the first row to be used as headers which means that the header values will be used as keys in the JSON document for each row.

```
SELECT * {
"""fn,ln
Mary,Major
John,Doe""" csv:parse (?rowJson "excel -h")
}
```


```
SELECT * { <example-data/people.csv> csv:parse (?rowJson "excel -h") }
```

```
---------------------------------------------------------------------------------
| rowJson                                                                       |
=================================================================================
| "{\"fn\":\"Mary\",\"ln\":\"Major\"}"^^<http://www.w3.org/2001/XMLSchema#json> |
| "{\"fn\":\"John\",\"ln\":\"Doe\"}"^^<http://www.w3.org/2001/XMLSchema#json>   |
---------------------------------------------------------------------------------

```



## Processing XML
For XML processing, the `xml:path` function and property functions are provided.
Unlike JSON, XML does not have a native array datatype which could be used for storing XML nodes matching an XPath expression.
In order to avoid having to introduce one, the `xml:path` *property function* can be used to unnest XML nodes based on an XPath expression, whereas the `xml:path` *function* can be used to access attributes and texts:

**NOTE: In order to avoid potential conflicts with Jena's machinery, this version of sparql-integrate introduces a custom xsd:xml datatype instead of reusing rdf:XMLLiteral.**

**NOTE: XPath matches are converted to independent RDF terms, i.e. the link to a match's parent element is lost and so is e.g. namespace information defined on ancestors. In most cases, XPath's `local-name` function should be usable as a workaround: `//[local-name()="elementNameWithoutNamespace"]`**

```
SELECT * {
  BIND('<ul id="ul1"><li>item</li></ul>'^^xsd:xml AS ?xml)
  BIND(xml:path(?xml, "//ul/@id") AS ?id)
  BIND(xml:path(?xml, "//li") AS ?item)
}
```

```
------------------------------------------------------------------------------------------------
| xml                                                                         | id    | item   |
================================================================================================
| "<ul id=\"ul1\"><li>item</li></ul>"^^<http://www.w3.org/2001/XMLSchema#xml> | "ul1" | "item" |
------------------------------------------------------------------------------------------------

```

Unnesting an XML document is done in regard to a given xpath expression:
```
SELECT * {
  """<ul id="ul1"><li>item</li></ul>"""^^xsd:xml xml:unnest ("//li" ?item)
}
```

```
-----------------------------------------------------------------------------------------------------------------------
| item                                                                                                                |
=======================================================================================================================
| "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><li>item</li>"^^<http://www.w3.org/2001/XMLSchema#xml> |
-----------------------------------------------------------------------------------------------------------------------
```


## Querying the file system

* Simple recursive listing of files
[fs-list-all-files.sparql](fs-list-all-files.sparql)

```
SELECT * {
  <example-data> fs:find ?file
}
```

The output will use absolute file URLs.
**NOTE: This behavior is not stable and may be changed to relative file URLs**
```
---------------------------------------
| file                                |
=======================================
| <file:///.../example-data/data.ttl> |
| <file:///.../example-data/data.csv> |
---------------------------------------
```

* Filtering files by content
The SPARQL predicate `fs:probeRdf` yields true if the given RDF URL argument refers to an existing file whose content can be parsed as RDF.


```
SELECT * {
  <example-data> fs:find ?file
  FILTER(fs:probeRdf(?file))
}

```

```
---------------------------------------
| file                                |
=======================================
| <file:///.../example-data/data.ttl> |
---------------------------------------
```


* Querying over files

`sparql-integrate` ships with an enhancement of jena's query processor that allows "federating" queries to files via the `SERVICE` clause:

```
SELECT * {
  <example-data> fs:find ?file
  FILTER(fs:probeRdf(?file))
  SERVICE ?file {
    ?s ?p ?o
  }
}
```

**NOTE: As of now, the fs:probeRdf check is necessary, as querying non-rdf files will raise an exception. This behavior is subject to change.**

```
------------------------------------------------------------------------------------------------------------------------------
| file                                | s                          | p                          | o                          |
==============================================================================================================================
| <file:///.../example-data/data.ttl> | <http://www.example.org/s> | <http://www.example.org/p> | <http://www.example.org/o> |
------------------------------------------------------------------------------------------------------------------------------
```

# Federation

## Federating to Virtual Files

The `vfs:` URI scheme can be used to address virtual files. This feature relies on [Apache's Virtual File System](https://github.com/apache/commons-vfs) and the Java NIO integration [vfs2nio](https://github.com/sshtools/vfs2nio/tree/master/src/main/java).
If a URL in the SERVICE clause resolves to a file then it will be probed for any encodings (= byte level transformations, such as compression) and the content type. If the effective content type of the file belongs to the class of RDF formats supported by Jena, then it will be loaded into an **in-memory** model. (Obviously this approach should not be done with large files).


```sparql
SELECT ?endpoint ?status {
  SERVICE <vfs:https://raw.githubusercontent.com/SmartDataAnalytics/lodservatory/master/latest-status.ttl> {
    ?s
      sd:endpoint ?endpoint ;
      <https://schema.org/serverStatus> ?status
} } LIMIT 3
```

```
---------------------------------------------------------------------------------------
| endpoint                                  | status                                  |
=======================================================================================
| <https://bmrbpub.pdbj.org/search/rdf>     | <https://schema.org/Online>             |
| <https://upr.eagle-i.net/sparqler/sparql> | <https://schema.org/Online>             |
| <http://babelnet.org/sparql/>             | <https://schema.org/OfflineTemporarily> |
---------------------------------------------------------------------------------------

```


## Federating to Sorted (Bzip2 Encoded) N-Triples

Binary search mode allows for lookups by subject on files containing n-triples.
This mode can operate directly on remote http(s) resources that support HTTP range requests - without the need to download them.

The `x-binsearch:` URI scheme enables this mode. The remainder must be an URI that resolves to a (virtual) file containing n-triples data sorted on the bytes (rather than characters). One way to sort data that way is to invoke the unix sort tool like this: `LC_ALL sort -u file.nt`.
The binary search system also supports operation on top of bzip2 encoding based on hadoop's bzip2 codec. This could be extended to any of hadoop's splittable codecs.


The example below demonstrates the use of `x-binsearch:` in conjunction with `vfs:` in order to perform binary search lookup on a remote resource:


```sparql
SELECT * {
  SERVICE <x-binsearch:vfs:https://databus.dbpedia.org/dnkg/cartridge-input/kb/2020.09.29/kb_partition=person_set=thes_content=facts_origin=export.nt.bz2> {
    <http://data.bibliotheken.nl/id/thes/p067461255> ?p ?o .
    FILTER(strStarts(str(?p), "http://schema.org/"))
  }
}
```

```
-----------------------------------------------------------------------
| p                                 | o                               |
=======================================================================
| <http://schema.org/birthDate>     | "1885"                          |
| <http://schema.org/name>          | "Lavedan, Pierre"               |
| <http://schema.org/sameAs>        | <http://viaf.org/viaf/34459176> |
| <http://schema.org/alternateName> | "Lavedan, Pierre Louis Léon"    |
-----------------------------------------------------------------------
```



