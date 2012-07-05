## Important notice

Automator has now been rebranded to jARVEST. The DSL part of this
project is now integrated into
[jARVEST](http://sing.ei.uvigo.es/jarvest/). Please refer there if you
want to use the most up-to-date version.

## Distributed aAutomator Runtime Environment

Final year project at [Vigo's University](www.esei.uvigo.es). It
deploys [aAUTOMATOR](www.iadis.net/dl/final_uploads/200713L028.pdf) as
a service with a REST API. There are two client apis implemented: a
Java one and a Python one.

Additionally a newly created DSL is defined for easing the creation of
aUTOMATOR *robots*. More information about its implementation
[here](http://ogf.github.com/DARE/dsl/transformer.html).

The executions requested to the service can be distributed among
several worker processes. For more information on this take a look at
the [workers](http://ogf.github.com/DARE/workers/uberdoc.html) and
[backend](http://ogf.github.com/DARE/backend/uberdoc.html) files.

### License

Distributed under
[MIT](http://www.opensource.org/licenses/mit-license.php) license.

Copyright 2012, Oscar Gonzalez.
