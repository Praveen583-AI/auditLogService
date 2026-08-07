# Functional Architecture

## Purpose

Define the minimum logical architecture needed to satisfy the audit-log prototype requirements. Components are named by responsibility and remain modules within one application unless a future requirement justifies extraction.

## Logical View

```mermaid
flowchart TD
    Caller["API caller or reviewer"] --> API["API operations"]
    API --> Append["Append service"]
    API --> Query["Audit query"]
    API --> Verify["Chain verification"]
    API --> Lifecycle["Audit record lifecycle"]
    API --> Export["Bulk export"]
    API --> Compliance["Compliance query"]

    Append --> Integrity["Integrity rules"]
    Append --> Repository["Audit repository"]
    Query --> View["Record view policy"]
    Query --> Repository
    Verify --> Integrity
    Verify --> Repository
    Lifecycle --> Repository
    Export --> Query
    Export --> Integrity
    Export --> View
    Compliance --> Query
    Compliance --> View
```

## Components

### API Operations

**Inputs:** Event writes, query filters, pagination, verification requests, lifecycle requests, export requests, and the clarified compliance request.

**Outputs:** Validation responses, stored-record acknowledgements, paged results, verification outcomes, lifecycle results, export bundles, and compliance results.

**Owned decisions:**

- External request and response contracts.
- Request-shape validation and error format.
- Which operations are exposed.
- Prototype authorization assumptions for sensitive operations.
- Confirmation that no general event update or delete operation is exposed.

**Dependencies:** All application-operation modules.

The API layer must not compute hashes, query persistence directly, perform lifecycle decisions, or construct export proofs.

### Append Service

**Inputs:** Validated event fields and the documented timestamp policy.

**Outputs:** Completed stored record, stable record identifier, and acknowledgement or failure.

**Owned decisions:**

- Event validation beyond request shape.
- Timestamp assignment.
- Record identity and ordering.
- Authoritative predecessor selection.
- Concurrent append behavior.
- Atomic completion of hash construction and persistence.
- Point at which a write is considered successful.

**Dependencies:** Integrity Rules and Audit Repository.

### Integrity Rules

**Inputs:** Event fields, predecessor integrity metadata, genesis value, and persisted lifecycle evidence during verification.

**Outputs:** Canonical hash input, content hash, predecessor link, and deterministic verification calculations.

**Owned decisions:**

- Hash-covered fields.
- Canonical record representation.
- Hash algorithm.
- Genesis value.
- Chain scope.
- Supported integrity calculations.

**Dependencies:** None of the API or persistence implementations. Both Append Service and Chain Verification consume the same rules.

### Audit Repository

**Inputs:** Completed audit records and authorized lifecycle evidence.

**Outputs:** Atomic append result, ordered records, filtered records, lifecycle evidence, and data used for database-inspection tests.

**Owned decisions:**

- Persistence contract.
- Atomic append behavior.
- Ordered and filtered retrieval primitives.
- Storage representation of records and lifecycle evidence.

**Dependencies:** Integrity and lifecycle data contracts.

The repository does not decide retention eligibility, redaction authorization, query visibility, or export disclosure.

### Audit Query

**Inputs:** Any supported combination of `actorId`, `resourceType` plus `resourceId`, `eventType`, `from` / `to`, and pagination.

**Outputs:** Ordered, paged records in the permitted representation.

**Owned decisions:**

- Filter-combination behavior.
- Time-boundary semantics.
- Default ordering.
- Pagination contract.

**Dependencies:** Audit Repository and Record View Policy.

### Audit Record Lifecycle

This boundary contains separate retention and structured-redaction operations while sharing l×Ï7ÒÚ$z{-®éÜj×'&VÆF–öâÔ–B"Â4õ%$TÄD”ôåô”B¢æ6öçFVçEG—R„ÖVF–G—RäÄ”4D”ôåô¥4ôâ¢æ6öçFVçB‚"" ¢°¢&WfVçEG—R#¢&–çfÆ–BG—R"À¢&WfVçE66†VÖfW'6–öâ#¢À¢'–ÆöB#¢·Ğ¢Ğ¢"""’¢ææDW‡V7B‡7FGW2‚’æ—4&E&WVW7B‚’¢ææDW‡V7B††VFW"‚’ç7G&–ær‚%‚Ô6÷'&VÆF–öâÔ–B"Â4õ%$TÄD”ôåô”B’¢ææDW‡V7B†§6öåF‚‚"Bæ6öFR"’çfÇVR‚$”ådÄ”Eõ$UTU5B"’¢ææDW‡V7B†§6öåF‚‚"Bæ6÷'&VÆF–öä–B"’çfÇVR„4õ%$TÄD”ôåô”B’¢ææDW‡V7B†§6öåF‚‚"Bçf–öÆF–öç2"Â†56—¦RƒR’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚&¦¶'FçfÆ–FF–öâ"’’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚'&V¦V7FVEfÇVR"’’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚'7F6µG&6R"’’’“°¢Ğ ¢FW7@¢fö–B6öæfÆ–7F–æt–FV×÷FVæ7”¶W•&WGW&ç3C•v—F†÷WD÷&–v–æÄFF€¢6GW&VD÷WGWB÷WGW@¢’F‡&÷w2W†6WF–öâ°¢v—fVâ†7&VFTVF—DWfVçBæ7&VFR†W„”DTÕõDTä5•ô´U’’Âç’‚’’¢çv–ÆÅF‡&÷r†æWr–FV×÷FVæ7”¶W•&WW6VDW†6WF–öâ‚’“° ¢Öö6´×f2çW&f÷&Ò‡fÆ–E&WVW7B‚'6V7&WBÖ66÷VçB×fÇVR"’¢ææDW‡V7B‡7FGW2‚’æ—46öæfÆ–7B‚’¢ææDW‡V7B†§6öåF‚‚"Bæ6öFR"’çfÇVR‚$”DTÕõDTä5•ô´U•õ$UU4TB"’¢ææDW‡V7B†§6öåF‚‚"Bæ6÷'&VÆF–öä–B"’çfÇVR„4õ%$TÄD”ôåô”B’¢ææDW‡V7B†§6öåF‚‚"Bçf–öÆF–öç2"Â†56—¦Rƒ’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚'6V7&WBÖ66÷VçB×fÇVR"’’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚&f–ævW'&–çB"’’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚'7F6µG&6R"’’’“° ¢76W'EF†B†÷WGWB’æ6öçF–ç2‚$”DTÕõDTä5•ô´U•õ$UU4TB"¢æ6öçF–ç2„4õ%$TÄD”ôåô”B¢æFöW4æ÷D6öçF–â‚'6V7&WBÖ66÷VçB×fÇVR"¢æFöW4æ÷D6öçF–â‚&f–ævW'&–çB"¢æFöW4æ÷D6öçF–â‚'7F6µG&6R"“°¢Ğ ¢FW7@¢fö–B÷fW'6—¦VE–ÆöE&WGW&ç3C5v—F†÷WDV6†ö–æu–ÆöB€¢6GW&VD÷WGWB÷WGW@¢’F‡&÷w2W†6WF–öâ°¢v—fVâ†7&VFTVF—DWfVçBæ7&VFR†W„”DTÕõDTä5•ô´U’’Âç’‚’’¢çv–ÆÅF‡&÷r†æWr–ÆöEFöôÆ&vTW†6WF–öâ‚’“° ¢Öö6´×f2çW&f÷&Ò‡fÆ–E&WVW7B‚'6Vç6—F—fR×–ÆöBÖÖ&¶W""’¢ææDW‡V7B‡7FGW2‚’æ—5–ÆöEFöôÆ&vR‚’¢ææDW‡V7B†§6öåF‚‚"Bæ6öFR"’çfÇVR‚%”ÄôEôÄ”Ô•EôU„4TTDTB"’¢ææDW‡V7B†§6öåF‚‚"BæÖW76vR"’çfÇVR‚%F†R&WVW7BW†6VVG2F†RW&Ö—GFVB6—¦Râ"’¢ææDW‡V7B†§6öåF‚‚"Bæ6÷'&VÆF–öä–B"’çfÇVR„4õ%$TÄD”ôåô”B’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚'6Vç6—F—fR×–ÆöBÖÖ&¶W""’’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚%–ÆöEFöôÆ&vTW†6WF–öâ"’’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚'7F6µG&6R"’’’“° ¢76W'EF†B†÷WGWB’æ6öçF–ç2‚%”ÄôEôÄ”Ô•EôU„4TTDTB"¢æ6öçF–ç2„4õ%$TÄD”ôåô”B¢æFöW4æ÷D6öçF–â‚'6Vç6—F—fR×–ÆöBÖÖ&¶W""¢æFöW4æ÷D6öçF–â‚%–ÆöEFöôÆ&vTW†6WF–öâ"¢æFöW4æ÷D6öçF–â‚'7F6µG&6R"“°¢Ğ ¢FW7@¢fö–BVæW‡V7FVDf–ÇW&U&WGW&ç4f—†VCSv—F†÷WDW†6WF–öäFWF–Ç2€¢6GW&VD÷WGWB÷WGW@¢’F‡&÷w2W†6WF–öâ°¢v—fVâ†7&VFTVF—DWfVçBæ7&VFR†W„”DTÕõDTä5•ô´U’’Âç’‚’’¢çv–ÆÅF‡&÷r†æWr'VçF–ÖTW†6WF–öâ€¢&f—‡GW&R×6Vç6—F—fRÖFF&6RÖFWF–Â"’“° ¢Öö6´×f2çW&f÷&Ò‡fÆ–E&WVW7B‚'6Vç6—F—fR×–ÆöBÖÖ&¶W""’¢ææDW‡V7B‡7FGW2‚’æ—4–çFW&æÅ6W'fW$W'&÷"‚’¢ææDW‡V7B†§6öåF‚‚"Bæ6öFR"’çfÇVR‚$”åDU$äÅôU%$õ""’¢ææDW‡V7B†§6öåF‚‚"BæÖW76vR"’çfÇVR‚%F†R&WVW7B6÷VÆBæ÷B&R6ö×ÆWFVBâ"’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚&f—‡GW&R×6Vç6—F—fRÖFF&6RÖFWF–Â"’’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚'6VÆV7B¢"’’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚%'VçF–ÖTW†6WF–öâ"’’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚'7F6µG&6R"’’’“° ¢76W'EF†B†÷WGWB’æ6öçF–ç2‚$”åDU$äÅôU%$õ""¢æ6öçF–ç2„4õ%$TÄD”ôåô”B¢æFöW4æ÷D6öçF–â‚&f—‡GW&R×6Vç6—F—fRÖFF&6RÖFWF–Â"¢æFöW4æ÷D6öçF–â‚'6VÆV7B¢"¢æFöW4æ÷D6öçF–â‚%'VçF–ÖTW†6WF–öâ"¢æFöW4æ÷D6öçF–â‚'7F6µG&6R"“°¢Ğ ¢FW7@¢fö–B6ö×ÆWFVD–çfÆ–EfW&–f–6F–öå&WGW&ç3#v—F…&V6öâ‚’F‡&÷w2W†6WF–öâ°¢v—fVâ†6†–åfW&–f–6F–öâçfW&–g’‚'FVæçC§FVæçBÓ"’’çv–ÆÅ&WGW&â€¢fW&–f–6F–öå&W7VÇBæ–çfÆ–B€¢fW&–f–6F–öå&W7VÇBäf–ÇW&U&V6öâä4ôåDTåEô„4…ôÔ•4ÔD4‚À¢"ÂÂÂÂÂÀ¢%F†R7F÷&VB6öçFVçB†6‚FöW2æ÷BÖF6‚F†R&V6Æ7VÆFVB†6‚â ¢¢“° ¢Öö6´×f2çW&f÷&Ò†vWB€¢D‚²"ö6†–ç2÷FVæçC§FVæçBÓ÷fW&–f–6F–öâ ¢’¢ææDW‡V7B‡7FGW2‚’æ—4ö²‚’¢ææDW‡V7B†§6öåF‚‚"Bç7FGW2"’çfÇVR‚$”ådÄ”B"’¢ææDW‡V7B†§6öåF‚‚"BçfÆ–B"’çfÇVR†fÇ6R’¢ææDW‡V7B†§6öåF‚‚"Bæf–ÇW&U&V6öâ"¢çfÇVR‚$4ôåDTåEô„4…ôÔ•4ÔD4‚"’¢ææDW‡V7B†§6öåF‚‚"Bæf–ÇW&U6WVVæ6R"’çfÇVRƒ"’“°¢Ğ ¢FW7@¢fö–BÖÆf÷&ÖVD7W'6÷%&WGW&ç3Cv—F†÷WDÆövv–æt7W'6÷"€¢6GW&VD÷WGWB÷WGW@¢’F‡&÷w2W†6WF–öâ°¢7G&–ær&t7W'6÷"Ò'6Vç6—F—fRÖ–çfÆ–BÖ7W'6÷"#°¢v—fVâ†6öçFW‡E&÷f–FW"æ7W'&VçD6öçFW‡B‚’’çv–ÆÅ&WGW&â€¢æWrVF—E&WVW7D6öçFW‡B€¢'FVæçBÓ"Â'&öGV6W"Ó"Â&7F÷"Ó"À¢%U4U""Â$UD„TåD”4DTEõ$”ä4•Â ¢¢“°¢v—fVâ†VF—EVW'’ç6V&6‚€¢W‚'FVæçBÓ"’À¢ç’„VF—DWfVçE7V6–f–6F–öâæ6Æ72’À¢WƒS’À¢W‡&t7W'6÷"¢’’çv–ÆÅF‡&÷r†æWr–çfÆ–D7W'6÷$W†6WF–öâ€¢–çfÆ–D7W'6÷$W†6WF–öâå&V6öâäÔÄdõ$ÔT@¢’“° ¢Öö6´×f2çW&f÷&Ò†vWB…D‚¢ç&Ò‚&7W'6÷""Â&t7W'6÷"¢æ†VFW"‚%‚Ô6÷'&VÆF–öâÔ–B"Â4õ%$TÄD”ôåô”B’¢ææDW‡V7B‡7FGW2‚’æ—4&E&WVW7B‚’¢ææDW‡V7B†§6öåF‚‚"Bæ6öFR"’çfÇVR‚$5U%4õ%ôÔÄdõ$ÔTB"’¢ææDW‡V7B†§6öåF‚‚"Bæ6÷'&VÆF–öä–B"’çfÇVR„4õ%$TÄD”ôåô”B’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‡&t7W'6÷"’’’“° ¢76W'EF†B†÷WGWB’æ6öçF–ç2‚$5U%4õ%ôÔÄdõ$ÔTB"¢æ6öçF–ç2„4õ%$TÄD”ôåô”B¢æFöW4æ÷D6öçF–â‡&t7W'6÷"“°¢Ğ ¢FW7@¢fö–BW††W7FVD6†–äÆö6µ&WGW&ç3S5v—F…&WG'”gFW"€¢6GW&VD÷WGWB÷WGW@¢’F‡&÷w2W†6WF–öâ°¢v—fVâ†7&VFTVF—DWfVçBæ7&VFR†W„”DTÕõDTä5•ô´U’’Âç’‚’’¢çv–ÆÅF‡&÷r†æWr6†–ä'W7”W†6WF–öâ€¢æWr'VçF–ÖTW†6WF–öâ‚'6Vç6—F—fRÆö6²FWF–Â"¢’“° ¢Öö6´×f2çW&f÷&Ò‡fÆ–E&WVW7B‚'6Vç6—F—fR×–ÆöBÖÖ&¶W""’¢ææDW‡V7B‡7FGW2‚’æ—56W'f–6UVæf–Æ&ÆR‚’¢ææDW‡V7B††VFW"‚’ç7G&–ær‚%&WG'’ÔgFW""Â#"’¢ææDW‡V7B†§6öåF‚‚"Bæ6öFR"’çfÇVR‚$4„”åô%U5’"’¢ææDW‡V7B†§6öåF‚‚"Bæ6÷'&VÆF–öä–B"’çfÇVR„4õ%$TÄD”ôåô”B’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B€¢6öçF–ç57G&–ær‚'6Vç6—F—fRÆö6²FWF–Â"¢’’“° ¢76W'EF†B†÷WGWB’æ6öçF–ç2‚$4„”åô%U5’"¢æ6öçF–ç2„4õ%$TÄD”ôåô”B¢æFöW4æ÷D6öçF–â‚'6Vç6—F—fRÆö6²FWF–Â"¢æFöW4æ÷D6öçF–â‚'6Vç6—F—fR×–ÆöBÖÖ&¶W""“°¢Ğ ¢FW7@¢fö–BG&ç6–VçD6öææV7F–öäf–ÇW&U&WGW&ç56æ—F—¦VCS2€¢6GW&VD÷WGWB÷WGW@¢’F‡&÷w2W†6WF–öâ°¢v—fVâ†7&VFTVF—DWfVçBæ7&VFR†W„”DTÕõDTä5•ô´U’’Âç’‚’’¢çv–ÆÅF‡&÷r†æWrFV×÷&'”FF&6Tf–ÇW&TW†6WF–öâ€¢æWr'VçF–ÖTW†6WF–öâ€¢&f—‡GW&R×6Vç6—F—fRÖ6öææV7F–öâÖFWF–Â ¢¢’“° ¢Öö6´×f2çW&f÷&Ò‡fÆ–E&WVW7B‚'–ÆöB×fÇVR"’¢ææDW‡V7B‡7FGW2‚’æ—56W'f–6UVæf–Æ&ÆR‚’¢ææDW‡V7B†§6öåF‚‚"Bæ6öFR"¢çfÇVR‚%DTÕõ$%•ôDD$4Uôd”ÅU$R"’¢ææDW‡V7B†§6öåF‚‚"Bæ6÷'&VÆF–öä–B"’çfÇVR„4õ%$TÄD”ôåô”B’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚&F"×6V7&WB"’’’¢ææDW‡V7B†6öçFVçB‚’ç7G&–ær†æ÷B†6öçF–ç57G&–ær‚'–ÆöB×fÇVR"’’’“° ¢76W'EF†B†÷WGWB’æ6öçF–ç2‚%DTÕõ$%•ôDD$4Uôd”ÅU$R"¢æ6öçF–ç2„4õ%$TÄD”ôåô”B¢æFöW4æ÷D6öçF–â‚&F"×6V7&WB"¢æFöW4æ÷D6öçF–â‚&f—‡GW&R×6Vç6—F—fRÖ6öææV7F–öâÖFWF–Â"¢æFöW4æ÷D6öçF–â‚'–ÆöB×fÇVR"“°¢Ğ ¢FW7@¢fö–B7V66W76gVÄ7&VFU&WGW&ç4GW&&ÆU&V6V—B‚’F‡&÷w2W†6WF–öâ°¢UT”BWfVçD–BÒUT”Bæg&öÕ7G&–ær‚#ÓÓÓÓ"“°¢VF—DWfVçE&W7öç6R&W7öç6RÒæWrVF—DWfVçE&W7öç6R€¢WfVçD–BÀ¢'FVæçC§FVæçBÓ"À¢À¢–ç7FçBç'6R‚###bÓ‚ÓuCC£3£"ãCSe¢"’À¢&"ç&WVBƒcB’À¢%4„Ó#Sb"À¢¢“°¢v—fVâ†7&VFTVF—DWfVçBæ7&VFR†W„”DTÕõDTä5•ô´U’’Âç’‚’’¢çv–ÆÅ&WGW&â†æWr7&VFTVF—DWfVçE&W7VÇB‡&W7öç6RÂfÇ6R’“° ¢Öö6´×f2çW&f÷&Ò‡fÆ–E&WVW7B‚&66÷VçBÓ"’¢ææDW‡V7B‡7FGW2‚’æ—47&VFVB‚’¢ææDW‡V7B††VFW"‚’ç7G&–ær€¢$Æö6F–öâ"À¢"÷cöVF—BöWfVçG2ò"²WfVçD–@¢’¢ææDW‡V7B††VFW"‚’ç7G&–ær‚$–FV×÷FVæ7’Õ&WÆ–VB"Â&fÇ6R"’¢ææDW‡V7B†§6öåF‚‚"BæWfVçD–B"’çfÇVR†WfVçD–BçFõ7G&–ær‚’’¢ææDW‡V7B†§6öåF‚‚"Bç6WVVæ6TçVÖ&W""’çfÇVRƒ’“°¢Ğ ¢&—fFR÷&rç7&–ævg&ÖWv÷&²çFW7BçvV"ç6W'fÆWBç&WVW7BäÖö6´‡GG6W'fÆWE&WVW7D'V–ÆFW ¢fÆ–E&WVW7B…7G&–ær&W6÷W&6T–B’°¢&WGW&â÷7B…D‚¢æ†VFW"‚$–FV×÷FVæ7’Ô¶W’"Â”DTÕõDTä5•ô´U’¢æ†VFW"‚%‚Ô6÷'&VÆF–öâÔ–B"Â4õ%$TÄD”ôåô”B¢æ6öçFVçEG—R„ÖVF–G—RäÄ”4D”ôåô¥4ôâ¢æ6öçFVçB‚"" ¢°¢&WfVçEG—R#¢$44õTåEõUDDTB"À¢&WfVçE66†VÖfW'6–öâ#¢À¢&ö67W'&VDB#¢###bÓ‚ÓuCC£3£"ã#5¢"À¢&7F÷"#¢°¢&–B#¢&V×Æ÷–VRÓC""À¢'G—R#¢%U4U" ¢ÒÀ¢'&W6÷W&6R#¢°¢'G—R#¢$44õTåB"À¢&–B#¢"W2 ¢ÒÀ¢'–ÆöB#¢°¢&6†ævVDf–VÆG2#¢²&FG&W72%Ğ¢Ğ¢Ğ¢"""æf÷&ÖGFVB‡&W6÷W&6T–B’“°¢Ğ§Ğ 