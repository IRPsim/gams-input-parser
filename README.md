# GAMS-Input-Parser
Ziel dieser Anwendung ist es, Annotationen aus Kommentaren in GAMS-Modellen auszulesen und in einer passenden Art zur weiteren Verarbeitung in Front-/Backend von IRPsim zu verwenden.
Grundannahmen sind:
- Alle Parameter und Setdefinitionen sind in Dateien mit den Präfixen "input_" und "output_" in den Unterordnern "input" und "output" enthalten.
- Es gibt in `input/ui-input.edn`, `input-output.edn` und `input-delta.edn` hierarchische Informationen als Konfiguration für die Weboberfläche.

## Annotationen in GAMS-Kommentaren
Input/Outputdateien dürfen nur eine strikte Teilmenge von GAMS-Syntax enthalten. Im wesentlichen sind das Parameter- und Set-Definitionen sowie $LOAD-Befehle
Alle Parameter können eine Reihe von Metadaten haben in Form von GAMS-Kommentaren haben. Die erwartete Syntax ist in der Grammatikdefinition in `gams-parameters.ebnf` als Backus-Naur-Notation enthalten.
Speziell für Sets gilt: Sie müssen immer eine Obermenge als Parameter enthalten (also `set_xy(*)` falls es keine Obermenge gibt).

Alle Metadaten starten mit einem Stern und einem Bindestrich. 

Vollständiges Beispiel für einen Parameter:

> 
    * - description: Einlesen Kundentarife Strom-Arbeitstarife Netzbezug
    * - type: float
    * - identifier: Strom-Arbeitstarife Netzbezug
    * - unit: [CHF / MWh]
    * - domain: [0,)
    * - default: 0
    * - validation:
    * - hidden: 0
    * - processing:
    * - rule: 
    PARAMETER par_F_E_EGrid_energy(set_ii,set_side_fares,set_pss) Tarife
    $LOAD par_F_E_EGrid_energy
	

Vollständiges Beispiel für ein Set:

> 
    * - description: Reservepool
    * - type: String
    * - identifier: Reservepool
    * - default: PRPool, SRPool, TRPool
    * - hidden: 1
    * - icon: 
    * - fill: 
    * - border:
    * - shape: 
    SET set_market_MS_P(set_market_MS) Reservepool
    $LOAD set_market_MS_P


### identifier
Kurzbezeichner, zur Anzeige als menschenlesbarer Name des Parameters

### description
Langtext, kann HTML enthalten. Wird typischerweise als Tooltip angezeigt.

### type
Datentyp des Parameters. Zulässige Werte sind: `float`, `boolean` (auf GAMS-Seite sind das 0 oder 1), `String` (bei Sets einzig zulässiger Wert)

### unit
in eckigen Klammern, Einheit der Werte für die Anzeige

### domain
Zulässiger Wertebereich. Wird mit Klammer angegeben um ein offenes oder geschlossenes Interval zu definieren. Unter-/Obergrenzen die egal sind können weggelassen werden.
Beispiele: 
> 
    (0,5] 0 < x <= 5
    [1,]  1 <= x
    [,1)  x < 1

### default
Standardwert, falls ein Parameterwert nicht vordefiniert ist. Bei Sets können hier kommagetrennt Elemente aufgezählt werden, die in einem Set immer enthalten sein müssen.

### hidden
Für Sets, die nicht durch Benutzer verändert werden dürfen kann `hidden: 1` gesetzt werden.

### processing
Für aggregierte Ergebnisse gedacht. Enhält eine kommagetrennte Liste von statistische Aggegrationsfunktionen. Beispiel: `min, max, avg`. Kann auch leer bleiben.

### rule
Analog zu Zellbezügen in Excel kann es nützlich sein, wenn als Reaktion auf die Änderung eines Parameterwertes andere Änderungen ausgeführt werden. Mit diesen Regeln können etwa XOR-Beziehungen zwischen zwei booleschen Werten sichergestellt werden. Die Syntax enspricht der IF-Syntax von GAMS.


### validation
Validierungsregeln beschreiben Relationen zwischen Parameterwerten. Zulässige Relationen sind <,<=,>,>=,==,!=.
Zusätzliche sind Beziehungen mit GAMS-Syntax möglich. Für Parameter mit Set-Abhängigkeiten kann pro Parameter entweder der volle Setname oder als Wildcard "*" angegeben werden. Gemeint ist dann entweder genau ein Element oder alle Elemente des Sets.

Beispiel für eine einfache Relation, gültig für das jeweils gleiche Setelement:

> 
    * -validation: par_SOC_DES_CS_max(set_tech_DES_CS) >= par_SOC_DES_CS_min(set_tech_DES_CS)
	
Beispiel für eine IF-Regel mit der Bedeutung: Wenn es für ein Element aus `set_side_cust` und einem beliebigen anderen Element im Parameter `par_X_DES_ES` einen Wert `1` gibt muss für jeden Wert desselben Setelementes in `par_X_DS_PV` der Wert `0` stehen.

> 
    * - validation: IF (par_X_DES_ES(set_side_cust,*) == 1, par_X_DS_PV(set_side_cust,*) == 0)


### Metadaten für Graphdarstellungen
Die folgenden Metadaten können nur auf Sets definiert werden und beschreiben die optische Darstellung von Knoten in Netzwerkgraphen. Sie werden jeweils verwendet, wenn die Graphdarstellung mindestens einen der vier Aspekte auf Ebene von Teilmengen referenziert.

### icon
Relativer Pfad zu einer SVG-Datei ausgehend vom Wurzelverzeichnisses des Modells (dieselbe Ebene wie `input/` und `output/`). Beispiel: `icons/Kletteraffe.svg`.

### fill
Farbe des Knotens im CSS-Format, entweder als Name oder in der hexadezimalen Form `#CAFF11`, siehe auch http://www.w3schools.com/cssref/css_colors.asp

### border
Analog zu `fill`, spezifiziert die Farbe des Rahmens.

### shape
Definiert die Form des Knotens. Unterstützt werden aktuell ausschließlich diese Werte:
- 'triangle-up'
- 'triangle-down'
- 'pentagon'
- 'hexagon'
- 'octagon'
- 'gear'
- 'flower'
- 'rectangle'
- 'square'
- 'circle'
- 'ellipse'
- 'diamond'

## Aufbau der `ui-xxx.edn` Dateien
Die EDN-Dateien enthalten die optische Anordnung von Eingabemasken und Ausgaben. Die Struktur ist hierarchisch. Jedes Element kann folgende Schlüssel haben: `:label`, `:icon`, `:description`, `:graph`, `:sections`, `:set`, `:tags`

### `:label`
Benennung der Ebene im Baum. Wenn kein Label angegeben ist aber `:set` definiert wurde wird der Identifier dieses Sets als Label verwendet.

### `:description`
HTML-Beschreibungstext. Wenn kein Wert angegeben ist aber `:set` definiert wurde wird die Beschreibung dieses Sets als Beschreibungstext.

### `:icon`
CSS-Klassen für die Icons im Baum. Unterstützt werden aktuell Glyphicons und Fontawesome.

### `:set`
Ein Teilbaum kann entweder beliebe Skalare und Zeitreihen enthalten oder aber Parameter, die von einem gemeinsamen Set abhängig sind. Alle Parameter, die mit Hilfe von `:tags` ermittelt werden beziehen sich dann auf die Menge (oder Untermengen) die hier angegeben ist.

### `:tags`
Tags beschreiben jeweils Konjunktionen von Namensbestandteilen von Parametern. `[["a" "b"] ["c" "d"]]` meint alle Parameter die entweder "a" UND "b" im Namen haben oder "c" UND "d". Parameter wie etwa `par_a_b_c_d` werden aber nur einmal erfasst und nicht mehrfach ausgewählt.

### `:graph`
Jede Seite im Baum kann maximal ein `:graph`-Element enthalten. Beispiel:

> 
    {:edges {:tags [["X" "energyLink"]]
	         :heading "optionales Label für Legende"
			 :groups [{:tags [["E"] ["EGrid"]] :label "Stromflüsse" :color "DarkCyan"}
                      {:tags [["W"]] :label "Wärmeflüsse" :color "DodgerBlue"}
                      {:tags [["PV"]] :label "PV-Voodoo" :color "HotPink"}]}
     :nodes {:set "set_pss"
	         :hide-single? true ; optional, false wenn nicht gesetzt
             :where "par_X_pss_model" ; optional
             :color {:type :parameter 
			         :tags [["OH"]]
					 :heading "siehe edges"} ;optional
             :border {:type :fix 
			          :value "#CAFE12"
					  :heading "siehe edges"} ;optional
             :shape {:type :subsets
			         :heading "siehe edges"
					 :groups [{:tags [["DES"]] :value "ellipse" :label "Dezentral XYZ"}
                              {:tags [["DS"]] :value "flower" :label "Zentral XYZ"}
                              {:tags [["SS"]] :value "square" :label "Kraftwerk"}
                              {:tags [["NS"]] :value "hexagon" :label "Netz"}
                              {:tags [["MS"]] :value "gear" :label "Markt"}]} ;optional
             :icon {:type :subsets}}}

Verplichtend sind nur die Elemente `:edges` und `:set` in `:nodes`. 
Knoten werden ausschließlich aus dem gewählten Set angezeigt. Man kann also auch nur Teilgraphen visualisieren, wenn passende Sethierarchien zur Verfügung stehen.

`:hide-single?` gibt an, ob Knoten, die nicht mit anderen Knoten in Verbindung stehen ausgeblendet werden sollen oder nicht. Dieses Ausblenden ist nützlich für Graphen, die nur ausgewählte Beziehungen visualisieren.

Die Kanten werden analog zur Auswahl von Parametern im Baum über Tags spezifiziert. Es werden nur zweidimensionle Parameter berücksichtigt, die binäre Daten enthalten und mindestens einer der abhängigen Sets in Beziehung zur Knotenmenge steht (gleich, Ober- oder Untermenge).

Es können vier verschiedene optische Merkmale für die Knoten definiert werden: Die Farbe der Füllung, die Farbe des Rahmens, die Form des Knotens und eine Icon. Alle Merkmale sind optional. Es gibt jeweils drei mögliche Arten der Konfiguration:

- fixe Werte (alle Einträge sind identisch)
- Teilmengen (das optische Attribut steht entweder als Metadatum in der Setdefinition, in der ein Knoten als Blattuntermenge enthalten ist oder wird explizit mit der Gruppierung im optionalen Eintrag `:groups` festgelegt)
- Parameter (Farbe wird automatisch gewählt je nachdem, mit welchem Setelement ein Knoten über einen booleschen zweidimensionalen Parameter in Verbindung steht).

### `:sections`
Der Aufbau ist hierarchisch. Unter diesem Schlüssel kann also pro Ebene ein Vektor von EDN-Maps mit dem gleichen Aufbau definiert werden wie in diesem aktuellen Kapitel beschrieben.

