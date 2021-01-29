# Decomposer
Library for electronic signature containers parsing

Reads data from electronic signature containers (e. g. PAdES, CAdES, ASiC or older XZEP files).
It is focused on 3 main data structures:
1. Electronic signature - who signed the document, when he signed it, what certificates he used etc.
2. Certificate - instances of signature certificates for both authorization and timestamp authority
3. Electronic document itself and its metadata

The getDocument() method of Decomposer class accepts signature container content and creates Container - Signatures - Certificate - Document structure for you, including
all viable metadata of the entities.

One can find this library useful especially when in a need for authomatization of electronically signed documents processing.
