# Saola
Saola is a lifecycle and depdnecy management library.

## Installation
Add clojure to dependency list: 
```clojure
[org.clojars.jj/saola "1.0.0"]
```


## Usage
Saola allows parallelly starting and stopping components with an automatic dependency resolution
### Core Concepts
Saola has two types of components: `Job` and `Service` 
Job is a short-lived process that can only be started
Service is a long-lived process that can be started and stopped.


## License

Copyright © 2025 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
