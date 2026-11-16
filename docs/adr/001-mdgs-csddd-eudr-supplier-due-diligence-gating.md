# ADR-001: Pre-Ingestion MDG-S Supplier CSDDD & EUDR Geo-Traceability Gate for S/4HANA 2025

## Status
Accepted

## Context
EU CSDDD (2024/1760) and EUDR (2023/1115) require verified supplier ESG due diligence audits, ISO 17442 LEI entity identification, and closed WGS84 GeoJSON production plot polygons prior to activating purchasing roles in SAP S/4HANA 2025.

## Decision
Implement a Groovy 3 Reader-streamed validation gate in SAP CPI enforcing ISO 17442 LEI Mod-97-10 verification, CSDDD score/expiry checks, RFC 7946 WGS84 GeoJSON ring closure, and SHA-256 Due Diligence Statement minting.

## Consequences
Non-compliant suppliers are diverted to an HTTP 422 MDG-S Stewardship quarantine queue before polluting S/4HANA Business Partner tables.
