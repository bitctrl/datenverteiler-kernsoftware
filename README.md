Kernsoftware [![Build Status](https://travis-ci.org/falkoschumann/datenverteiler-kernsoftware.png)](https://travis-ci.org/falkoschumann/datenverteiler-kernsoftware)
============

Die Kernsoftware des Datenverteilers in Form eines Maven-Projekts. Der Zweck
dieses Projekts ist es, die Softwareinheiten (SWE) der Kernsoftware als
Maven-Artefakte zur Verf�gung zu stellen und so als Maven-Abh�ngigkeit nutzbar
zu machen.

Das Projekt beinhaltet die SWEs, die vom NERZ e.V. als Paket *Kernsoftware* zum
Download bereit gestellt werden.

Die Projektdokumentation befindet sich unter
http://falkoschumann.github.io/datenverteiler-kernsoftware/

Das Maven Repository http://falkoschumann.github.io/maven-repository/releases/
enth�lt alle mit diesem Projekt erzeugten Artefakte der Kernsofware: Binary
JARs, Source JARs und JavaDoc JARs.


Hinweise zur Maven-Konfiguration
--------------------------------

Die SWEs der Kernsoftware sind als Unterprojekte angelegt. Im Root-Projekt sind
alle gemeinsamen Einstellungen konfiguriert, einschlie�lich der Sektionen f�r
*build* und *reporting*.

In den Unterprojekten der SWEs sind nur vom Root-Projekt abweichende
Einstellungen konfiguriert. Insbesondere bei Reports ist im Root-Projekt der
aggregierte Report aktiviert und im Unterprojekt wieder deaktiviert.


---

Dieses Projekt ist nicht Teil des NERZ e.V. Die offizielle Software sowie
weitere Informationen zur bundeseinheitlichen Software f�r
Verkehrsrechnerzentralen (BSVRZ) finden Sie unter http://www.nerz-ev.de.
